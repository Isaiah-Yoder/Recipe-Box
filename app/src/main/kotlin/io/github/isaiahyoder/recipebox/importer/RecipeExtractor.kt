package io.github.isaiahyoder.recipebox.importer

import io.github.isaiahyoder.recipebox.data.RecipeLine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI

/** A recipe read from a web page, before it is saved. */
data class ExtractedRecipe(
    val title: String,
    val description: String? = null,
    val imageUrl: String? = null,
    val siteName: String? = null,
    val yieldText: String? = null,
    val servings: Int? = null,
    val prepMinutes: Int? = null,
    val cookMinutes: Int? = null,
    val totalMinutes: Int? = null,
    val ingredients: List<RecipeLine> = emptyList(),
    val steps: List<RecipeLine> = emptyList(),
    val categories: List<String> = emptyList(),
    val cuisines: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
    val rawJsonLd: String? = null,
    /** The page's structured data and recipe card, small enough to keep and read again later. */
    val pageSnapshot: String? = null,
) {
    /** True when the recipe has everything the requirements call a recipe. */
    val isComplete: Boolean
        get() = title.isNotBlank() && ingredients.isNotEmpty() && steps.isNotEmpty()
}

/**
 * Reads the structured recipe data that sites publish for search engines.
 *
 * It tries schema.org JSON-LD first, then schema.org microdata. When the page
 * shows a recipe card from a known recipe plugin, its ingredients and steps
 * replace the data's, because the card keeps group headings and full steps.
 * It never reads the page's article text, so stories, ads, and comments stay out.
 */
object RecipeExtractor {
    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    fun extract(html: String, pageUrl: String): ExtractedRecipe? {
        val document = Jsoup.parse(html, pageUrl)
        val siteName = siteName(document, pageUrl)
        val fromJsonLd = fromJsonLd(document, siteName)
        val fromData = if (fromJsonLd?.isComplete == true) {
            fromJsonLd
        } else {
            listOfNotNull(fromJsonLd, fromMicrodata(document, siteName)).maxByOrNull { it.ingredients.size + it.steps.size }
        }
        val recipe = withCard(fromData, RecipeCardHtml.read(document), document) ?: return null
        // Some sites put only ingredient names in the data and the amounts on the page.
        val pageList = if (PageIngredientList.lacksAmounts(recipe.ingredients)) {
            PageIngredientList.find(document, recipe.ingredients)
        } else {
            null
        }
        val read = if (pageList != null) recipe.copy(ingredients = PageIngredientList.lines(pageList)) else recipe
        return read.copy(pageSnapshot = snapshot(document, pageList))
    }

    /**
     * Keeps only what [extract] reads: structured data, the site name and
     * title, and the recipe card. Reading the snapshot gives the same recipe
     * as reading the whole page, at a small fraction of its size.
     */
    private fun snapshot(document: Document, ingredientList: Element?): String {
        val head = buildString {
            document.select("script[type*=ld+json]").forEach { append(it.outerHtml()).append('\n') }
            document.select("meta[property=og:site_name], meta[property=og:title]").forEach { append(it.outerHtml()) }
            append("<title>").append(org.jsoup.nodes.Entities.escape(document.title())).append("</title>")
        }
        val card = document.select(".wprm-recipe-container, .wprm-recipe, .tasty-recipes, [itemtype*=schema.org/Recipe]")
            .firstOrNull()?.outerHtml().orEmpty()
        return "<html><head>$head</head><body>$card${ingredientList?.outerHtml().orEmpty()}</body></html>"
    }

    /** Uses the card's lists when they hold at least as much as the data. */
    private fun withCard(recipe: ExtractedRecipe?, card: RecipeCardHtml.Lists?, document: Document): ExtractedRecipe? {
        if (card == null) return recipe
        fun count(lines: List<RecipeLine>) = lines.count { !it.isHeader }
        val base = recipe ?: ExtractedRecipe(
            title = RecipeTextCleanup.title(document.selectFirst("meta[property=og:title]")?.attr("content") ?: document.title()),
        )
        return base.copy(
            ingredients = if (count(card.ingredients) >= count(base.ingredients)) card.ingredients else base.ingredients,
            steps = if (count(card.steps) >= count(base.steps) && card.steps.isNotEmpty()) card.steps else base.steps,
        )
    }

    // JSON-LD

    private fun fromJsonLd(document: Document, siteName: String?): ExtractedRecipe? {
        val candidates = mutableListOf<JsonObject>()
        for (script in document.select("script[type*=ld+json]")) {
            val element = parseJson(script.data()) ?: continue
            collectRecipes(element, candidates)
        }
        val recipe = candidates.maxByOrNull { (it["recipeIngredient"] as? JsonArray)?.size ?: 0 }
            ?: return null
        return toExtracted(recipe, siteName)
    }

    private fun parseJson(text: String): JsonElement? {
        val trimmed = text.trim().removePrefix("<!--").removeSuffix("-->").trim().removeSuffix(";")
        return runCatching { json.parseToJsonElement(trimmed) }.getOrNull()
    }

    private fun collectRecipes(element: JsonElement, into: MutableList<JsonObject>) {
        when (element) {
            is JsonArray -> element.forEach { collectRecipes(it, into) }
            is JsonObject -> {
                if (types(element).any { it.equals("Recipe", ignoreCase = true) }) into += element
                element.values.forEach { collectRecipes(it, into) }
            }
            else -> Unit
        }
    }

    private fun types(obj: JsonObject): List<String> = strings(obj["@type"])

    private fun toExtracted(recipe: JsonObject, siteName: String?): ExtractedRecipe {
        val yields = strings(recipe["recipeYield"])
        val servings = yields.firstNotNullOfOrNull { Regex("""\d+""").find(it)?.value?.toIntOrNull() }
        val yieldText = yields.filter { it.toIntOrNull() == null }.map(RecipeTextCleanup::yieldText).distinct()
            .joinToString(", ").ifBlank { null }
        val ingredients = strings(recipe["recipeIngredient"] ?: recipe["ingredients"])
            .mapNotNull { RecipeTextCleanup.ingredient(cleanText(it)) }
        return ExtractedRecipe(
            title = RecipeTextCleanup.title(cleanText(text(recipe["name"]) ?: "")),
            description = text(recipe["description"])?.let(::cleanText)?.ifBlank { null },
            imageUrl = image(recipe["image"]),
            siteName = siteName,
            yieldText = yieldText,
            servings = servings,
            prepMinutes = IsoDuration.minutes(text(recipe["prepTime"])),
            cookMinutes = IsoDuration.minutes(text(recipe["cookTime"])),
            totalMinutes = IsoDuration.minutes(text(recipe["totalTime"])),
            ingredients = ingredients,
            steps = instructions(recipe["recipeInstructions"]),
            categories = splitList(recipe["recipeCategory"]),
            cuisines = splitList(recipe["recipeCuisine"]),
            keywords = splitList(recipe["keywords"]),
            rawJsonLd = recipe.toString(),
        )
    }

    /** Flattens steps, keeping section names as header lines. */
    private fun instructions(element: JsonElement?): List<RecipeLine> {
        val lines = mutableListOf<RecipeLine>()
        fun visit(node: JsonElement?) {
            when (node) {
                null, JsonNull -> Unit
                is JsonPrimitive -> node.contentOrNull?.let { text ->
                    // A single string may hold every step, one per line or paragraph.
                    splitInstructionText(text).forEach { lines += RecipeLine(it) }
                }
                is JsonArray -> node.forEach { visit(it) }
                is JsonObject -> {
                    val types = types(node)
                    when {
                        types.any { it == "HowToSection" } -> {
                            text(node["name"])?.let(::cleanText)?.takeIf { it.isNotBlank() }
                                ?.let { lines += RecipeLine(RecipeTextCleanup.heading(it.trimEnd(':').trim()), isHeader = true) }
                            visit(node["itemListElement"])
                        }
                        node["itemListElement"] != null -> visit(node["itemListElement"])
                        else -> {
                            val text = stepText(text(node["name"]), text(node["text"])) ?: text(node["description"])
                            text?.let { visit(JsonPrimitive(it)) }
                        }
                    }
                }
            }
        }
        visit(element)
        return lines
    }

    /**
     * Some recipe plugins put a step's bold first words in "name" and the rest
     * in "text", such as "Heat oven" and "to 375° F". Text that starts mid-sentence
     * gets its name back; a name that only repeats or titles the step is dropped.
     */
    internal fun stepText(name: String?, text: String?): String? {
        val body = text?.let(::cleanText).orEmpty()
        val title = name?.let(::cleanText).orEmpty()
        if (body.isEmpty()) return title.ifEmpty { null }
        if (title.isEmpty() || Regex("""(?i)step\s*\d+""").matches(title)) return body
        if (body.startsWith(title.removeSuffix("...").removeSuffix("…").trim())) return body
        val first = body.first()
        return when {
            first.isLowerCase() -> "$title $body"
            first in ",.;:" -> title + body
            else -> body
        }
    }

    private fun splitInstructionText(text: String): List<String> {
        val document = Jsoup.parseBodyFragment(text)
        val items = document.select("li, p").map { it.text().trim() }.filter { it.isNotEmpty() }
        if (items.isNotEmpty()) return items
        return document.wholeText().split(Regex("""\n\s*\n|\r?\n"""))
            .map { cleanText(it) }.filter { it.isNotBlank() }
    }

    private fun image(element: JsonElement?): String? = when (element) {
        is JsonPrimitive -> element.contentOrNull
        is JsonArray -> element.firstNotNullOfOrNull { image(it) }
        is JsonObject -> text(element["url"]) ?: text(element["contentUrl"])
        else -> null
    }?.takeIf { it.startsWith("http") }

    private fun text(element: JsonElement?): String? = when (element) {
        is JsonPrimitive -> element.contentOrNull
        is JsonArray -> element.firstNotNullOfOrNull { text(it) }
        is JsonObject -> text(element["@value"]) ?: text(element["name"])
        else -> null
    }

    private fun strings(element: JsonElement?): List<String> = when (element) {
        is JsonPrimitive -> listOfNotNull(element.contentOrNull)
        is JsonArray -> element.flatMap { strings(it) }
        is JsonObject -> listOfNotNull(text(element))
        else -> emptyList()
    }

    /** Category, cuisine, and keyword values arrive as lists or comma-separated text. */
    private fun splitList(element: JsonElement?): List<String> =
        strings(element).flatMap { it.split(',') }.map(::cleanText).filter { it.isNotBlank() }.distinct()

    // Microdata

    private fun fromMicrodata(document: Document, siteName: String?): ExtractedRecipe? {
        val scope = document.select("[itemtype*=schema.org/Recipe]").firstOrNull() ?: return null
        fun props(name: String): List<Element> = scope.select("[itemprop=$name]")
        val ingredients = (props("recipeIngredient") + props("ingredients"))
            .mapNotNull { RecipeTextCleanup.ingredient(cleanText(it.text())) }
        val steps = props("recipeInstructions").flatMap { element ->
            val items = element.select("li").map { it.text() }
            (items.ifEmpty { listOf(element.text()) }).map(::cleanText)
        }.filter { it.isNotBlank() }.map { RecipeLine(it) }
        return ExtractedRecipe(
            title = cleanText(props("name").firstOrNull()?.text() ?: document.title()),
            imageUrl = props("image").firstOrNull()?.let { it.absUrl("src").ifBlank { it.attr("content") } },
            siteName = siteName,
            ingredients = ingredients,
            steps = steps,
            totalMinutes = IsoDuration.minutes(props("totalTime").firstOrNull()?.attr("content")),
        )
    }

    // Shared

    private fun siteName(document: Document, pageUrl: String): String? {
        document.selectFirst("meta[property=og:site_name]")?.attr("content")?.takeIf { it.isNotBlank() }
            ?.let { return RecipeTextCleanup.siteName(cleanText(it)) }
        return runCatching { URI(pageUrl).host?.removePrefix("www.") }.getOrNull()
    }

    /** Decodes HTML entities, removes tags, and collapses whitespace. */
    fun cleanText(text: String): String = Jsoup.parseBodyFragment(text).text().trim()
}
