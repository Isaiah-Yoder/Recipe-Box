package io.github.isaiahyoder.recipebox.repository

import androidx.room.withTransaction
import io.github.isaiahyoder.recipebox.data.RecipeDao
import io.github.isaiahyoder.recipebox.data.RecipeDatabase
import io.github.isaiahyoder.recipebox.data.RecipeEntity
import io.github.isaiahyoder.recipebox.photos.PhotoStore
import io.github.isaiahyoder.recipebox.tags.TagRefresher

/** What happens to a recipe's cover photo when she saves the editor. */
sealed interface PhotoChange {
    data object Keep : PhotoChange

    /** Her own photo, already saved under [file], replaces the cover for good. */
    data class Replace(val file: String) : PhotoChange

    data object Remove : PhotoChange
}

/**
 * Saving and deleting whole recipes, with their tags and photo files.
 *
 * A save is one transaction: the recipe row and its tags change together.
 * Photo files that are no longer used are deleted only after it commits.
 */
class RecipeRepository(
    private val database: RecipeDatabase,
    private val photos: PhotoStore,
    private val tagRefresher: TagRefresher,
) {
    private val recipes: RecipeDao get() = database.recipeDao()

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
            val id = if (current == null) recipes.insert(row.copy(id = 0)) else row.id.also { recipes.update(row) }
            tagRefresher.refreshRecipe(row.copy(id = id))
            id
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
