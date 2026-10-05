package io.github.isaiahyoder.recipebox.tags

/** The parts of a recipe that automatic tags are computed from. */
data class TaggableRecipe(
    val title: String,
    val ingredients: List<String>,
    val steps: List<String>,
    val totalMinutes: Int?,
    val siteCategories: List<String>,
    val siteCuisines: List<String>,
    val siteKeywords: List<String>,
)

/**
 * Computes automatic tags with fixed rules, so the same recipe always gets
 * the same tags and no AI is needed.
 */
object AutoTagger {
    const val QUICK = "30 Minutes or Less"
    const val MEATLESS = "Meatless"

    /** Site category values mapped to one tag name; unknown short values pass through. */
    private val categorySynonyms = mapOf(
        "entree" to "Main Dish", "entrees" to "Main Dish", "main dish" to "Main Dish",
        "main course" to "Main Dish", "main dishes" to "Main Dish", "dinner" to "Dinner",
        "lunch" to "Lunch", "breakfast" to "Breakfast", "brunch" to "Brunch",
        "dessert" to "Dessert", "desserts" to "Dessert", "cookies" to "Dessert", "cake" to "Dessert",
        "pie" to "Dessert", "side dish" to "Side Dish", "side dishes" to "Side Dish", "sides" to "Side Dish",
        "appetizer" to "Appetizer", "appetizers" to "Appetizer", "snack" to "Snack", "snacks" to "Snack",
        "soup" to "Soup", "soups" to "Soup", "salad" to "Salad", "salads" to "Salad",
        "sandwich" to "Sandwich", "sandwiches" to "Sandwich", "bread" to "Bread", "breads" to "Bread",
        "drink" to "Drink", "drinks" to "Drink", "beverage" to "Drink", "sauce" to "Sauce",
    )

    private data class Protein(val tag: String, val words: List<String>)

    private val proteins = listOf(
        Protein("Chicken", listOf("chicken")),
        Protein("Beef", listOf("beef", "steak", "brisket", "ground chuck", "sirloin")),
        Protein("Pork", listOf("pork", "bacon", "ham", "sausage", "prosciutto", "pancetta", "chorizo", "pernil")),
        Protein("Turkey", listOf("turkey")),
        Protein("Lamb", listOf("lamb")),
        Protein(
            "Seafood",
            listOf("salmon", "tuna", "cod", "tilapia", "halibut", "shrimp", "prawn", "crab", "lobster",
                "scallop", "clam", "mussel", "fish", "anchov", "sardine", "trout"),
        ),
    )

    /** Meat and fish words that rule out the Meatless tag, including in broths. */
    private val meatWords = proteins.flatMap { it.words } +
        listOf("veal", "venison", "duck", "pepperoni", "salami", "gelatin", "lard", "worcestershire")

    /** "Chicken broth" flavors a dish; it doesn't make chicken the main ingredient. */
    private val flavoringOnly = Regex("""\s*(broth|stock|bouillon|base|soup base|fat|drippings)\b""")

    private data class Method(val tag: String, val pattern: Regex)

    private val methods = listOf(
        Method("Slow Cooker", Regex("""\b(slow cooker|crock ?pot)\b""")),
        Method("Pressure Cooker", Regex("""\b(instant pot|pressure cook(er)?|multi-?cooker)\b""")),
        Method("Air Fryer", Regex("""\bair[ -]?fr(y|yer|ied)\b""")),
        Method("Grilled", Regex("""\b(outdoor grill|grill grates|grill pan|preheat (an |the )?(outdoor |gas |charcoal )?grill)\b""")),
        Method("Sheet Pan", Regex("""\bsheet pan\b""")),
        Method("No Bake", Regex("""\bno[- ]bake\b""")),
        Method("One Pot", Regex("""\bone[- ]pot\b""")),
    )

    fun tags(recipe: TaggableRecipe): Set<String> {
        val tags = linkedSetOf<String>()

        for (value in recipe.siteCategories + recipe.siteKeywords) {
            categorySynonyms[value.lowercase().trim()]?.let(tags::add)
        }
        for (value in recipe.siteCategories) {
            val key = value.lowercase().trim()
            if (key !in categorySynonyms && isShortLabel(value)) tags += titleCase(value)
        }
        for (cuisine in recipe.siteCuisines) {
            if (isShortLabel(cuisine)) tags += titleCase(cuisine)
        }

        val ingredientText = recipe.ingredients.joinToString("\n") { it.lowercase() }
        for (protein in proteins) {
            if (protein.words.any { mainIngredientMention(ingredientText, it) }) tags += protein.tag
        }
        if (recipe.ingredients.isNotEmpty() && meatWords.none { Regex("""\b$it""").containsMatchIn(ingredientText) }) {
            tags += MEATLESS
        }

        val methodText = (listOf(recipe.title) + recipe.steps).joinToString("\n") { it.lowercase() }
        for (method in methods) {
            if (method.pattern.containsMatchIn(methodText)) tags += method.tag
        }

        if (recipe.totalMinutes != null && recipe.totalMinutes <= 30) tags += QUICK
        return tags
    }

    private fun mainIngredientMention(text: String, word: String): Boolean =
        Regex("""\b$word\w*""").findAll(text).any { match ->
            !flavoringOnly.matchesAt(text, match.range.last + 1)
        }

    private fun isShortLabel(value: String): Boolean =
        value.isNotBlank() && value.length <= 25 && value.trim().split(Regex("""\s+""")).size <= 3

    private fun titleCase(value: String): String =
        value.trim().split(Regex("""\s+""")).joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { it.uppercase() }
        }
}
