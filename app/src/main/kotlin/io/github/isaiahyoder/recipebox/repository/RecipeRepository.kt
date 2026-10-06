package io.github.isaiahyoder.recipebox.repository

import androidx.room.withTransaction
import io.github.isaiahyoder.recipebox.data.RecipeDao
import io.github.isaiahyoder.recipebox.data.RecipeDatabase
import io.github.isaiahyoder.recipebox.data.RecipeEntity
import io.github.isaiahyoder.recipebox.data.RecipePageEntity
import io.github.isaiahyoder.recipebox.data.toEntity
import io.github.isaiahyoder.recipebox.model.RecipeDraft
import io.github.isaiahyoder.recipebox.model.SourceKind
import io.github.isaiahyoder.recipebox.photos.PhotoStore
import io.github.isaiahyoder.recipebox.tags.TagRefresher

/** What happens to a recipe's cover photo when she saves the editor. */
sealed interface PhotoChange {
    data object Keep : PhotoChange

    /** Her own photo, already saved under [file], replaces the cover for good. */
    data class Replace(val file: String) : PhotoChange

    data object Remove : PhotoChange
}

/** The result of adding a recipe from a source. */
sealed interface AddResult {
    data class Saved(val recipeId: Long) : AddResult

    /** She already has the recipe, by its link or by its title on the same website. */
    data class AlreadySaved(val recipeId: Long) : AddResult
}

/**
 * Saving and deleting whole recipes, with their tags and photo files.
 *
 * Every new recipe, from any source, is saved here: imports through [add],
 * and recipes she checked or typed in the editor through [save]. A save is
 * one transaction: the recipe row, its saved page, and its tags change
 * together. Photo files that are no longer used are deleted only after it commits.
 */
class RecipeRepository(
    private val database: RecipeDatabase,
    private val photos: PhotoStore,
    private val tagRefresher: TagRefresher,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val recipes: RecipeDao get() = database.recipeDao()

    /**
     * Adds a recipe read from a source without her checking it first, such as
     * a shared link, unless she already has it.
     */
    suspend fun add(draft: RecipeDraft): AddResult = database.withTransaction {
        val existing = findDuplicate(draft)
        if (existing != null) {
            AddResult.AlreadySaved(existing)
        } else {
            val now = clock()
            val id = insertNew(draft.toEntity(now))
            draft.pageSnapshot?.let { database.pageDao().savePage(RecipePageEntity(id, it, now)) }
            AddResult.Saved(id)
        }
    }

    /**
     * A recipe she already has that [draft] repeats: one with the same link, or
     * a web recipe with the same title from the same website.
     */
    suspend fun findDuplicate(draft: RecipeDraft): Long? {
        draft.sourceUrl?.let { url -> recipes.findIdBySourceUrl(url)?.let { return it } }
        val site = draft.siteName
        if (draft.sourceKind != SourceKind.WEB || site == null) return null
        return recipes.titlesFromSite(site).firstOrNull { RecipeDraft.titleKey(it.title) == draft.titleKey }?.id
    }

    /** Saves a new recipe with its automatic tags and categories. */
    private suspend fun insertNew(recipe: RecipeEntity): Long {
        val id = recipes.insert(recipe.copy(id = 0))
        tagRefresher.refreshRecipe(recipe.copy(id = id))
        return id
    }

    /**
     * Saves a new recipe (id 0) or her edits to one, and returns its id.
     * Fields the editor doesn't show, such as a cover photo downloaded while
     * the editor was open, a favorite, or the last scale, come from the saved
     * row, so the editor's copy never overwrites them.
     */
    suspend fun save(edited: RecipeEntity, photo: PhotoChange, unusedFiles: Collection<String> = emptyList()): Long {
        val oldFiles = unusedFiles.toMutableList()
        val id = database.withTransaction {
            val current = if (edited.id == 0L) null else recipes.getRecipe(edited.id)
            var row = if (current == null) {
                edited
            } else {
                edited.copy(
                    imageUrl = current.imageUrl,
                    imageFile = current.imageFile,
                    imageIsOwn = current.imageIsOwn,
                    favorite = current.favorite,
                    lastScale = current.lastScale,
                    createdAt = current.createdAt,
                )
            }
            row = when (photo) {
                PhotoChange.Keep -> row
                is PhotoChange.Replace -> {
                    row.imageFile?.let(oldFiles::add)
                    row.copy(imageFile = photo.file, imageIsOwn = true, imageUrl = null)
                }
                PhotoChange.Remove -> {
                    row.imageFile?.let(oldFiles::add)
                    row.copy(imageFile = null, imageIsOwn = false, imageUrl = null)
                }
            }
            if (current == null) {
                insertNew(row)
            } else {
                recipes.update(row)
                tagRefresher.refreshRecipe(row)
                row.id
            }
        }
        oldFiles.forEach(photos::delete)
        return id
    }

    /** Deletes a recipe with its cover photo and card photos. Its tags and category links go with it. */
    suspend fun delete(recipeId: Long) {
        val recipe = database.withTransaction {
            recipes.getRecipe(recipeId).also { recipes.delete(recipeId) }
        } ?: return
        photos.delete(recipe.imageFile)
        recipe.cardPhotos.forEach(photos::delete)
    }
}
