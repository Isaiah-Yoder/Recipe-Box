package io.github.isaiahyoder.recipebox.tags

import androidx.room.withTransaction
import io.github.isaiahyoder.recipebox.data.RecipeDatabase
import io.github.isaiahyoder.recipebox.data.RecipeEntity

/**
 * Recomputes automatic tags with the current rules, then fills categories
 * from their feeder tags.
 *
 * Only automatic tags, suggestions, and automatic category memberships
 * change. Tags she added, confirmed, removed, or dismissed, recipes she put
 * in or took out of a category, favorites, and notes stay exactly as they
 * are. Each run is one database transaction, so an interruption changes nothing.
 */
class TagRefresher(private val database: RecipeDatabase) {
    private val categories = CategoryRules(database)

    /** Returns the number of recipes checked. */
    suspend fun refreshAll(): Int = database.withTransaction {
        val tags = database.tagDao()
        val recipes = database.recipeDao()
        // One recipe at a time, so a large library never sits in memory at once.
        val ids = recipes.allIds()
        for (id in ids) {
            val recipe = recipes.getRecipe(id) ?: continue
            tags.replaceAutoTags(id, AutoTagger.tags(recipe.toTaggable()))
        }
        tags.deleteUnusedTags()
        categories.applyAll()
        ids.size
    }

    /** Updates one recipe's tags, such as after an import or an edit, and the categories they feed. */
    suspend fun refreshRecipe(recipe: RecipeEntity) = database.withTransaction {
        database.tagDao().replaceAutoTags(recipe.id, AutoTagger.tags(recipe.toTaggable()))
        categories.applyTo(recipe.id)
    }

    /** Fills categories again, such as after she changes a tag or a category's feeders. */
    suspend fun applyCategories() = categories.applyAll()
}

fun RecipeEntity.toTaggable() = TaggableRecipe(
    title = title,
    ingredients = ingredients.filterNot { it.isHeader }.map { it.text },
    steps = steps.filterNot { it.isHeader }.map { it.text },
    totalMinutes = totalMinutes,
    siteCategories = siteCategories,
    siteCuisines = siteCuisines,
    siteKeywords = siteKeywords,
    fromCard = cardPhotos.isNotEmpty(),
)
