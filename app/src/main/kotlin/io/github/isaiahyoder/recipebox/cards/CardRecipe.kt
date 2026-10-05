package io.github.isaiahyoder.recipebox.cards

import io.github.isaiahyoder.recipebox.data.RecipeEntity
import io.github.isaiahyoder.recipebox.data.RecipeLine
import io.github.isaiahyoder.recipebox.importer.RecipeTextCleanup
import io.github.isaiahyoder.recipebox.importer.TextDuration
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class CardLine(val text: String, val isHeading: Boolean = false)

/**
 * A recipe as read from card photos, before she checks it. The fields match
 * assets/cards/schema.json, which the AI readers fill in.
 */
@Serializable
data class CardRecipe(
    val title: String = "",
    val servings: String = "",
    val prepTime: String = "",
    val cookTime: String = "",
    val ingredients: List<CardLine> = emptyList(),
    val steps: List<CardLine> = emptyList(),
    val notes: String = "",
    /** The kind of dish the AI reader judged it to be, such as "Dessert", or empty. */
    val course: String = "",
    /** The cuisine the AI reader judged it to be, such as "Italian", or empty. */
    val cuisine: String = "",
) {
    val isEmpty: Boolean get() = title.isBlank() && ingredients.isEmpty() && steps.isEmpty()

    fun toEntity(photos: List<String>, now: Long): RecipeEntity {
        val prep = TextDuration.minutes(prepTime)
        val cook = TextDuration.minutes(cookTime)
        val servingsText = servings.trim()
        return RecipeEntity(
            title = title.trim().trimEnd(':').trim().ifBlank { "Recipe card" },
            siteName = SITE_NAME,
            yieldText = servingsText.takeIf { it.isNotEmpty() && it.toIntOrNull() == null },
            servings = Regex("""\d+""").find(servingsText)?.value?.toIntOrNull()?.takeIf { it > 0 },
            prepMinutes = prep,
            cookMinutes = cook,
            totalMinutes = if (prep != null && cook != null) prep + cook else null,
            ingredients = ingredients.toLines(),
            steps = steps.toLines(),
            notes = notes.trim(),
            // The reader's judgment counts like a website's own labels, so automatic tags use it.
            siteCategories = listOfNotNull(course.trim().takeIf { it.isNotEmpty() }),
            siteCuisines = listOfNotNull(cuisine.trim().takeIf { it.isNotEmpty() }),
            cardPhotos = photos,
            createdAt = now,
            updatedAt = now,
        )
    }

    private fun List<CardLine>.toLines() = mapNotNull { line ->
        val text = line.text.trim().trimStart('•', '-', '*').trim()
        when {
            text.isEmpty() -> null
            // The recipe screen already labels its sections, so a copied card label adds nothing.
            text.trimEnd(':').trim().lowercase() in sectionNames -> null
            line.isHeading || (text.endsWith(":") && text.length <= 60) -> RecipeLine(RecipeTextCleanup.heading(text.trimEnd(':').trim()), isHeader = true)
            else -> RecipeLine(text)
        }
    }

    companion object {
        private val sectionNames = setOf("ingredients", "directions", "instructions", "method", "steps")

        /** Shown where a web recipe shows its site, such as "Allrecipes". */
        const val SITE_NAME = "Recipe card"
    }
}

/** Reads the JSON an AI reader returns, tolerating code fences and extra text around it. */
object CardJson {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    fun parse(text: String): CardRecipe? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { json.decodeFromString<CardRecipe>(text.substring(start, end + 1)) }.getOrNull()
    }
}
