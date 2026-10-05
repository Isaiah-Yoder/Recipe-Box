package io.github.isaiahyoder.recipebox.repository

import androidx.room.withTransaction
import io.github.isaiahyoder.recipebox.data.CategoryDao
import io.github.isaiahyoder.recipebox.data.RecipeDatabase
import io.github.isaiahyoder.recipebox.data.TagDao
import io.github.isaiahyoder.recipebox.tags.TagRefresher

/** A recipe and one of its tags, such as a suggestion to accept. */
data class RecipeTag(val recipeId: Long, val tag: String)

/**
 * Changes to tags and categories. Each change and the category update it
 * causes happen in one transaction, so categories never show a half-applied
 * change, and screens don't repeat the "change a tag, then fill categories" rule.
 */
class LibraryRepository(
    private val database: RecipeDatabase,
    private val tagRefresher: TagRefresher,
) {
    private val tags: TagDao get() = database.tagDao()
    private val categories: CategoryDao get() = database.categoryDao()

    private suspend fun <T> changeThenFill(change: suspend () -> T): T = database.withTransaction {
        val result = change()
        tagRefresher.applyCategories()
        result
    }

    /** A tag she adds is hers: updating automatic tags never removes it. It can fill categories. */
    suspend fun addTag(recipeId: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) changeThenFill { tags.addManualTag(recipeId, trimmed) }
    }

    /** Removed tags stay removed, even automatic ones the rules would add again. */
    suspend fun removeTag(recipeId: Long, name: String) = changeThenFill { tags.removeTag(recipeId, name) }

    /** Confirms suggestions, which then count like tags she added. */
    suspend fun acceptSuggestions(items: Collection<RecipeTag>) = changeThenFill {
        items.forEach { tags.acceptSuggestion(it.recipeId, it.tag) }
    }

    /** A dismissed suggestion stays dismissed, like a removed tag. Suggestions never fill categories. */
    suspend fun dismissSuggestion(recipeId: Long, name: String) = tags.removeTag(recipeId, name)

    /**
     * Creates a category filled by [tag], or adds [tag] as a feeder to the
     * category with that name, so recipes with the tag join it now and later.
     */
    suspend fun makeCategory(tag: String) = changeThenFill {
        val id = categories.createCategory(tag)
        val category = categories.getCategories().first { it.id == id }
        categories.setFeederTags(id, (category.feederTags + tag).distinct())
    }

    /** Sets the tags that fill a category, then fills it. */
    suspend fun setFeederTags(categoryId: Long, feederTags: List<String>) =
        changeThenFill { categories.setFeederTags(categoryId, feederTags) }

    /** Sets several categories' feeder tags at once, such as from the offer after an update. */
    suspend fun setFeederTags(feeders: Map<Long, List<String>>) =
        changeThenFill { feeders.forEach { (id, tagNames) -> categories.setFeederTags(id, tagNames) } }

    suspend fun setRecipeCategories(recipeId: Long, categoryIds: Collection<Long>) =
        categories.setRecipeCategories(recipeId, categoryIds)

    suspend fun addToCategoryByHand(recipeId: Long, categoryId: Long) = categories.addToCategoryByHand(recipeId, categoryId)

    /** Creates a category at the end of the list, or returns the one that already has the name. */
    suspend fun createCategory(name: String): Long = categories.createCategory(name)

    /** Returns false when another category already has the name. */
    suspend fun renameCategory(id: Long, name: String): Boolean = categories.renameCategory(id, name)

    suspend fun deleteCategory(id: Long) = categories.deleteCategory(id)

    suspend fun reorderCategories(idsInOrder: List<Long>) = categories.reorderCategories(idsInOrder)
}
