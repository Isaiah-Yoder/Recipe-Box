package io.github.isaiahyoder.recipebox.cards

import io.github.isaiahyoder.recipebox.data.RecipeEntity
import io.github.isaiahyoder.recipebox.importer.TextDuration

/** The recipe she checked, as a new library recipe with the card photos it was read from. */
fun CardRecipe.toEntity(photos: List<String>, now: Long): RecipeEntity {
    val prep = TextDuration.minutes(prepTime)
    val cook = TextDuration.minutes(cookTime)
    val servingsText = servings.trim()
    return RecipeEntity(
        title = cleanTitle,
        siteName = CardRecipe.SITE_NAME,
        yieldText = servingsText.takeIf { it.isNotEmpty() && it.toIntOrNull() == null },
        servings = Regex("""\d+""").find(servingsText)?.value?.toIntOrNull()?.takeIf { it > 0 },
        prepMinutes = prep,
        cookMinutes = cook,
        totalMinutes = if (prep != null && cook != null) prep + cook else null,
        ingredients = ingredientLines,
        steps = stepLines,
        notes = notes.trim(),
        // The reader's judgment counts like a website's own labels, so automatic tags use it.
        siteCategories = listOfNotNull(course.trim().takeIf { it.isNotEmpty() }),
        siteCuisines = listOfNotNull(cuisine.trim().takeIf { it.isNotEmpty() }),
        cardPhotos = photos,
        createdAt = now,
        updatedAt = now,
    )
}
