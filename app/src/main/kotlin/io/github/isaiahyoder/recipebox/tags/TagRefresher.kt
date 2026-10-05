package io.github.isaiahyoder.recipebox.tags

import androidx.room.withTransaction
import io.github.isaiahyoder.recipebox.data.RecipeDatabase
import io.github.isaiahyoder.recipebox.data.RecipeEntity

/**
 * Recomputes every recipe's automatic tags with the current rules, such as
 * after an update that improves them.
 *
 * Only visible automatic tags change. Tags she added, automatic tags she
 * removed, categories, favorites, and notes stay exactly as they are. The
 * whole run is one database transaction, so an interruption changes nothing.
 */
class TagRefresher(private val database: RecipeDatabase) {
    /** Returns the number of recipes checked. */
    suspend fun refreshAll(): Int = database.withTransaction {
        val dao = database.recipeDao()
        val recipes = dao.getAllRecipes()
        for (recipe in recipes) {
            dao.replaceAutoTags(recipe.id, AutoTagger.tags(recipe.toTaggable()))
        }
        dao.deleteUnusedTags()
        recipes.size
    }
}

fun RecipeEntity.toTaggable() = TaggableRecipe(
    title = title,
    ingredients = ingredients.filterNot { it.isHeader }.map { it.text },
    steps = steps.filterNot { it.isHeader }.map { it.text },
    totalMinutes = totalMinutes,
    siteCategories = siteCategories,
    siteCuisines = siteCuisines,
    siteKeywords = siteKeywords,
)
