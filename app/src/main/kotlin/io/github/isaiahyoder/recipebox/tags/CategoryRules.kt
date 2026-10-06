package io.github.isaiahyoder.recipebox.tags

import androidx.room.withTransaction
import io.github.isaiahyoder.recipebox.data.CategoryEntity
import io.github.isaiahyoder.recipebox.data.RecipeCategoryEntity
import io.github.isaiahyoder.recipebox.data.RecipeDatabase
import io.github.isaiahyoder.recipebox.data.TagSource

/**
 * Fills categories from their feeder tags.
 *
 * A recipe with any of a category's feeder tags joins it automatically, and
 * leaves it when it no longer has one. Her own choices always win: a recipe
 * she added stays even without a feeder tag, and a recipe she took out stays
 * out even with one. Suggested tags don't count until she confirms them.
 */
class CategoryRules(private val database: RecipeDatabase) {
    suspend fun applyAll() = database.withTransaction {
        for (category in database.categoryDao().getCategories()) apply(category)
    }

    /**
     * Fills categories for one recipe, such as after it's imported or edited,
     * without checking every other recipe.
     */
    suspend fun applyTo(recipeId: Long) = database.withTransaction {
        val dao = database.categoryDao()
        val tagNames = database.tagDao().getTagNames(recipeId).map { it.lowercase() }.toSet()
        val links = dao.getCategoryLinks(recipeId).associateBy { it.categoryId }
        for (category in dao.getCategories()) {
            val wanted = category.feederTags.any { it.lowercase() in tagNames }
            val link = links[category.id]
            when {
                wanted && link == null -> dao.addToCategory(RecipeCategoryEntity(recipeId, category.id, TagSource.AUTO))
                !wanted && link != null && link.source == TagSource.AUTO && !link.hidden ->
                    dao.deleteCategoryLink(recipeId, category.id)
            }
        }
    }

    private suspend fun apply(category: CategoryEntity) {
        val dao = database.categoryDao()
        val wanted = if (category.feederTags.isEmpty()) {
            emptySet()
        } else {
            database.tagDao().recipeIdsWithTags(category.feederTags.map { it.lowercase() }).toSet()
        }
        val members = dao.getCategoryMembers(category.id).associateBy { it.recipeId }
        for (recipeId in wanted) {
            if (recipeId !in members) dao.addToCategory(RecipeCategoryEntity(recipeId, category.id, TagSource.AUTO))
        }
        for (link in members.values) {
            if (link.source == TagSource.AUTO && !link.hidden && link.recipeId !in wanted) {
                dao.deleteCategoryLink(link.recipeId, category.id)
            }
        }
    }
}
