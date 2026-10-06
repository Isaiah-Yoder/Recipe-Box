package io.github.isaiahyoder.recipebox.importer

import io.github.isaiahyoder.recipebox.model.RecipeLine
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Reads ingredients and steps from the recipe card a page shows, for the
 * two recipe plugins most food blogs use: WP Recipe Maker and Tasty Recipes.
 *
 * The card keeps what the search-engine data loses: group headings such as
 * "Toppings" or "Frosting", the bold first words of a step, and steps the
 * data leaves out.
 */
object RecipeCardHtml {
    data class Lists(val ingredients: List<RecipeLine>, val steps: List<RecipeLine>)

    fun read(document: Document): Lists? = wprm(document) ?: tasty(document)

    private fun wprm(document: Document): Lists? {
        val card = document.select(".wprm-recipe-container, .wprm-recipe")
            .firstOrNull { it.selectFirst(".wprm-recipe-ingredient") != null } ?: return null
        val ingredients = mutableListOf<RecipeLine>()
        for (group in card.select(".wprm-recipe-ingredient-group")) {
            heading(group.selectFirst(".wprm-recipe-group-name"))?.let { ingredients += it }
            for (item in group.select("li.wprm-recipe-ingredient")) {
                val name = item.selectFirst(".wprm-recipe-ingredient-name")?.text()
                val text = if (name == null) {
                    item.text()
                } else {
                    val notes = item.selectFirst(".wprm-recipe-ingredient-notes")?.text()
                        ?.trim()?.removePrefix(",")?.trim().orEmpty()
                    val main = listOfNotNull(
                        item.selectFirst(".wprm-recipe-ingredient-amount")?.text(),
                        item.selectFirst(".wprm-recipe-ingredient-unit")?.text(),
                        name,
                    ).filter { it.isNotBlank() }.joinToString(" ")
                    // Notes that already start with a parenthesis, such as "(or 2 small) peeled", stay as written.
                    when {
                        notes.isEmpty() -> main
                        notes.startsWith("(") -> "$main $notes"
                        else -> "$main ($notes)"
                    }
                }
                RecipeTextCleanup.ingredient(text)?.let { ingredients += it.copy(isHeader = false) }
            }
        }
        val steps = mutableListOf<RecipeLine>()
        for (group in card.select(".wprm-recipe-instruction-group")) {
            heading(group.selectFirst(".wprm-recipe-group-name"))?.let { steps += it }
            for (item in group.select("li.wprm-recipe-instruction")) {
                val text = (item.selectFirst(".wprm-recipe-instruction-text") ?: item).text().trim()
                if (text.isNotEmpty()) steps += RecipeLine(text)
            }
        }
        return Lists(ingredients, steps).takeIf { it.ingredients.isNotEmpty() }
    }

    private fun tasty(document: Document): Lists? {
        val ingredientsBody = document.selectFirst(".tasty-recipes-ingredients-body, .tasty-recipes-ingredients") ?: return null
        val ingredients = mutableListOf<RecipeLine>()
        for (element in ingredientsBody.select("h3, h4, li")) {
            if (element.tagName() == "li") {
                val item = element.clone().apply { select(".tr-ingredient-checkbox-container, input, label").remove() }
                RecipeTextCleanup.ingredient(item.text())?.let { ingredients += it.copy(isHeader = false) }
            } else {
                heading(element)?.let { ingredients += it }
            }
        }
        val steps = mutableListOf<RecipeLine>()
        document.selectFirst(".tasty-recipes-instructions-body, .tasty-recipes-instructions")?.let { body ->
            val items = body.select("h3, h4, li")
            for (element in items.ifEmpty { body.select("h3, h4, p") }) {
                if (element.tagName() == "h3" || element.tagName() == "h4") {
                    heading(element)?.let { steps += it }
                } else {
                    element.text().trim().takeIf { it.isNotEmpty() }?.let { steps += RecipeLine(it) }
                }
            }
        }
        return Lists(ingredients, steps).takeIf { it.ingredients.isNotEmpty() }
    }

    /** A group name, unless it only repeats the section's own title. */
    private fun heading(element: Element?): RecipeLine? {
        val text = element?.text()?.trim()?.trimEnd(':')?.trim().orEmpty()
        if (text.isEmpty() || text.lowercase() in sectionTitles) return null
        return RecipeLine(RecipeTextCleanup.heading(text), isHeader = true)
    }

    private val sectionTitles = setOf("ingredients", "instructions", "directions", "method", "steps", "equipment")
}
