package io.github.isaiahyoder.recipebox.importer

import io.github.isaiahyoder.recipebox.data.RecipeLine
import io.github.isaiahyoder.recipebox.ingredients.IngredientParser
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Finds the ingredient list a page shows when its structured data names the
 * ingredients without amounts, as some news and magazine sites publish
 * ("Lamb meat" in the data, "250 Gram Lamb meat" on the page).
 *
 * A list counts only when its items contain the data's names in the same
 * order, so a related-recipes list or a shopping widget is never read.
 */
internal object PageIngredientList {
    /** True when fewer than a quarter of [lines] start with an amount. */
    fun lacksAmounts(lines: List<RecipeLine>): Boolean {
        val items = lines.filterNot { it.isHeader }
        if (items.size < MIN_ITEMS) return false
        val withAmount = items.count { IngredientParser.parse(it.text).quantity != null }
        return withAmount * 4 < items.size
    }

    /** The page's list matching [data], when it holds the amounts the data left out. */
    fun find(document: Document, data: List<RecipeLine>): Element? {
        val wanted = data.map { normalize(it.text) }.filter { it.isNotEmpty() }
        if (wanted.size < MIN_ITEMS) return null
        return document.select("ul, ol")
            .filter { items(it).size in wanted.size - SIZE_SLACK..wanted.size + SIZE_SLACK }
            .map { it to matchesInOrder(it, wanted) }
            .filter { (_, matches) -> matches >= wanted.size * MIN_MATCH_SHARE }
            .maxByOrNull { (_, matches) -> matches }
            ?.first
            ?.takeIf { !lacksAmounts(lines(it)) }
    }

    fun lines(list: Element): List<RecipeLine> = items(list).mapNotNull { RecipeTextCleanup.ingredient(it.text()) }

    private fun items(list: Element): List<Element> = list.children().filter { it.tagName() == "li" }

    private fun matchesInOrder(list: Element, wanted: List<String>): Int {
        val texts = items(list).map { normalize(it.text()) }
        var next = 0
        var matches = 0
        for (name in wanted) {
            val at = (next until texts.size).firstOrNull { name in texts[it] } ?: continue
            matches++
            next = at + 1
        }
        return matches
    }

    private fun normalize(text: String): String =
        text.lowercase().replace(Regex("""[^a-z]+"""), " ").trim()

    private const val MIN_ITEMS = 3
    private const val SIZE_SLACK = 2
    private const val MIN_MATCH_SHARE = 0.8
}
