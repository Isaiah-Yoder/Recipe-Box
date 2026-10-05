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
        val dao = database.recipeDao()
        for (category in dao.getCategories()) apply(category)
    }

    private suspend fun apply(category: CategoryEntity) {
        val dao = database.recipeDao()
        val wanted = if (category.feederTags.isEmpty()) {
            emptySet()
        } else {
            dao.recipeIdsWithTags(category.feederTags.map { it.lowercase() }).toSet()
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

    companion object {
        /**
         * Feeder tags that fit a category she named herself, such as the Dessert
         * tag for "Dessert" or Main Dish for "Dinner". Used to offer feeders, never applied silently.
         */
        fun suggestedFeeders(categoryName: String, tagsInUse: Collection<String>): List<String> {
            val name = categoryName.lowercase().trim()
            val singular = name.removeSuffix("es").takeIf { name.endsWith("ches") || name.endsWith("shes") }
                ?: name.removeSuffix("s")
            val aliases = mapOf(
                "dinner" to listOf(AutoTagger.MAIN_DISH), "dinners" to listOf(AutoTagger.MAIN_DISH),
                "entrees" to listOf(AutoTagger.MAIN_DISH), "mains" to listOf(AutoTagger.MAIN_DISH),
                "lunch" to listOf(AutoTagger.MAIN_DISH), "sides" to listOf(AutoTagger.SIDE_DISH),
                "dough" to listOf(AutoTagger.DOUGH), "crusts" to listOf(AutoTagger.DOUGH),
                "sweets" to listOf(AutoTagger.DESSERT), "baking" to listOf(AutoTagger.BREAD, AutoTagger.DESSERT),
            )
            val candidates = aliases[name].orEmpty() + (AutoTagger.VOCABULARY.keys + tagsInUse).filter { tag ->
                val key = tag.lowercase()
                key == name || key == singular || key.removeSuffix("s") == singular
            }
            return candidates.distinct()
        }
    }
}
