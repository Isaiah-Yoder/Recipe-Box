package io.github.isaiahyoder.recipebox.cards

import io.github.isaiahyoder.recipebox.model.RecipeLine
import io.github.isaiahyoder.recipebox.importer.RecipeTextCleanup
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

    /** The title as the recipe should be saved, with a default for a card without one. */
    val cleanTitle: String get() = title.trim().trimEnd(':').trim().ifBlank { "Recipe card" }

    val ingredientLines: List<RecipeLine> get() = ingredients.toLines()

    val stepLines: List<RecipeLine> get() = steps.toLines()

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
