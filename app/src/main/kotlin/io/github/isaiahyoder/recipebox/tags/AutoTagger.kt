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
 * Automatic tags for one recipe. [suggestions] are guesses she confirms or
 * dismisses, such as an occasion; they don't count as tags until she does.
 */
data class TagResult(val tags: Set<String>, val suggestions: Set<String>)

/** The groups the fixed tag names belong to, in the order pickers show them. */
enum class TagGroup(val label: String) {
    COURSE("Course"),
    OCCASION("Occasion"),
    METHOD("Method"),
    MAIN_INGREDIENT("Main ingredient"),
    CUISINE("Cuisine"),
    TIME("Time"),
}

/**
 * Computes automatic tags with fixed rules from a fixed list of names, so the
 * same recipe always gets the same tags and sites can't add their own
 * labels. Rules read the site's own categories and keywords, the title, the
 * ingredients, and the steps. No AI is needed.
 */
object AutoTagger {
    const val MAIN_DISH = "Main Dish"
    const val SIDE_DISH = "Side Dish"
    const val DESSERT = "Dessert"
    const val BREAKFAST = "Breakfast"
    const val APPETIZER = "Appetizer"
    const val SNACK = "Snack"
    const val BREAD = "Bread"
    const val SOUP = "Soup"
    const val SALAD = "Salad"
    const val SAUCE = "Sauce"
    const val DRINK = "Drink"
    const val DOUGH = "Dough and Crust"
    const val QUICK = "30 Minutes or Less"
    const val VEGETARIAN = "Vegetarian"
    const val THANKSGIVING = "Thanksgiving"

    /** Every name the rules can produce, by group. */
    val VOCABULARY: Map<String, TagGroup> by lazy {
        buildMap {
            listOf(MAIN_DISH, SIDE_DISH, DESSERT, BREAKFAST, APPETIZER, SNACK, BREAD, SOUP, SALAD, SAUCE, DRINK, DOUGH)
                .forEach { put(it, TagGroup.COURSE) }
            occasions.forEach { put(it.tag, TagGroup.OCCASION) }
            methods.forEach { put(it.tag, TagGroup.METHOD) }
            (proteins.map { it.tag } + VEGETARIAN).forEach { put(it, TagGroup.MAIN_INGREDIENT) }
            cuisines.values.distinct().forEach { put(it, TagGroup.CUISINE) }
            put(QUICK, TagGroup.TIME)
        }
    }

    fun groupOf(tag: String): TagGroup? = VOCABULARY[tag]

    // Course

    /** Site category and keyword values that name a course. */
    private val courseLabels = mapOf(
        "entree" to MAIN_DISH, "entrees" to MAIN_DISH, "main" to MAIN_DISH, "main dish" to MAIN_DISH,
        "main dishes" to MAIN_DISH, "main course" to MAIN_DISH, "dinner" to MAIN_DISH, "lunch" to MAIN_DISH,
        "supper" to MAIN_DISH, "breakfast" to BREAKFAST, "brunch" to BREAKFAST, "dessert" to DESSERT,
        "desserts" to DESSERT, "cookies" to DESSERT, "cake" to DESSERT, "cakes" to DESSERT, "pie" to DESSERT,
        "pies" to DESSERT, "pies + tarts" to DESSERT, "candy" to DESSERT, "side" to SIDE_DISH,
        "side dish" to SIDE_DISH, "side dishes" to SIDE_DISH, "sides" to SIDE_DISH, "appetizer" to APPETIZER,
        "appetizers" to APPETIZER, "snack" to SNACK, "snacks" to SNACK, "soup" to SOUP, "soups" to SOUP,
        "stew" to SOUP, "salad" to SALAD, "salads" to SALAD, "bread" to BREAD, "breads" to BREAD,
        "drink" to DRINK, "drinks" to DRINK, "beverage" to DRINK, "beverages" to DRINK, "sauce" to SAUCE,
        "sauces" to SAUCE, "gravy" to SAUCE, "marinade" to SAUCE, "condiment" to SAUCE, "dressing" to SAUCE,
    )

    private class TitleRule(val tag: String, val pattern: Regex)

    private fun words(vararg words: String) = Regex("""\b(${words.joinToString("|")})\b""")

    /** Course words in a title. "Chicken pot pie" is a main dish, not a dessert, so savory rules run first. */
    private val titleRules = listOf(
        TitleRule(MAIN_DISH, words("pot pie", "chicken pie", "shepherd'?s pie", "meat pie", "quiche", "casserole")),
        TitleRule(DOUGH, words("crust", "pastry", "pie dough", "puff pastry")),
        TitleRule(
            DESSERT,
            words(
                "cookies?", "cakes?", "cupcakes?", "cheesecake", "pies?", "tarts?", "brownies?", "blondies?",
                "fudge", "pudding", "cobbler", "crisp", "crumble", "ice cream", "frosting", "icing", "truffles?",
                "macarons?", "cannoli", "tiramisu", "candy", "bars", "pumpkin roll", "cake roll", "custard",
            ),
        ),
        TitleRule(
            BREAKFAST,
            words("pancakes?", "waffles?", "french toast", "muffins?", "granola", "oatmeal", "breakfast", "scones?", "crepes?", "puffs"),
        ),
        TitleRule(
            BREAD,
            words("bread", "dinner rolls", "yeast rolls", "crescent rolls", "biscuits?", "naan", "cornbread", "focaccia", "bagels?", "buns", "loaf", "pretzels?", "tortillas?"),
        ),
        TitleRule(SOUP, words("soup", "stew", "chili", "chilli", "chowder", "bisque", "gumbo")),
        TitleRule(SALAD, words("salad", "slaw", "coleslaw")),
        TitleRule(SAUCE, words("sauce", "gravy", "marinade", "dressing", "glaze", "salsa", "pesto", "aioli", "vinaigrette")),
        TitleRule(DRINK, words("shake", "smoothie", "lemonade", "punch", "latte", "cocoa", "tea", "coffee", "cocktail", "mocktail")),
        TitleRule(APPETIZER, words("dip", "deviled eggs", "egg rolls?", "wings", "bruschetta", "sausage rolls", "sliders", "cha gio", "spring rolls?")),
        TitleRule(SIDE_DISH, words("stuffing", "mashed potatoes", "green beans", "roasted vegetables", "mac and cheese", "rice pilaf", "coleslaw")),
    )

    // Occasions are suggestions: they're guesses she confirms.

    private class Occasion(val tag: String, val pattern: Regex)

    private val occasions = listOf(
        Occasion(
            THANKSGIVING,
            words(
                "thanksgiving", "turkey", "stuffing", "giblet", "gravy", "cranberr(y|ies)", "pumpkin",
                "sweet potato casserole", "green bean casserole", "pecan pie", "dinner rolls", "cornbread",
            ),
        ),
        Occasion("Christmas", words("christmas", "gingerbread", "eggnog", "peppermint", "candy cane", "yule", "fruitcake")),
        Occasion("Easter", words("easter", "hot cross")),
        Occasion("Fourth of July", words("4th of july", "fourth of july", "independence day", "red,? white,? (and|&) blue", "patriotic")),
        Occasion("Game Day", words("game day", "super bowl", "tailgate", "tailgating")),
    )

    // Method

    private class Method(val tag: String, val pattern: Regex)

    private val methods = listOf(
        Method("Slow Cooker", Regex("""\b(slow cooker|crock ?pot)\b""")),
        Method("Pressure Cooker", Regex("""\b(instant pot|pressure cook(er)?|multi-?cooker)\b""")),
        Method("Air Fryer", Regex("""\bair[ -]?fr(y|yer|ied)\b""")),
        Method("Grilled", Regex("""\b(outdoor grill|grill grates|grill pan|preheat (an |the )?(outdoor |gas |charcoal )?grill)\b""")),
        Method("Sheet Pan", Regex("""\bsheet pan\b""")),
        Method("No Bake", Regex("""\bno[- ]bake\b""")),
        Method("One Pot", Regex("""\bone[- ]pot\b""")),
    )

    // Main ingredient

    private class Protein(val tag: String, val words: List<String>)

    private val proteins = listOf(
        Protein("Chicken", listOf("chicken")),
        Protein("Beef", listOf("beef", "steak", "brisket", "ground chuck", "sirloin")),
        Protein("Pork", listOf("pork", "bacon", "ham", "sausage", "prosciutto", "pancetta", "chorizo", "pernil", "ribs?")),
        Protein("Turkey", listOf("turkey")),
        Protein("Lamb", listOf("lamb")),
        Protein(
            "Seafood",
            listOf("salmon", "tuna", "cod", "tilapia", "halibut", "shrimp", "prawn", "crab", "lobster",
                "scallop", "clam", "mussel", "fish", "anchov", "sardine", "trout"),
        ),
    )

    /** Meat and fish words that rule out Vegetarian, including in broths. */
    private val meatWords = proteins.flatMap { it.words } +
        listOf("veal", "venison", "duck", "pepperoni", "salami", "gelatin", "lard", "worcestershire", "giblets?")

    /** "Chicken broth" flavors a dish; it doesn't make chicken the main ingredient. */
    private val flavoringOnly = Regex("""(\s+or\s+[a-z]+)?\s*(broth|stock|bouillon|base|soup|fat|drippings|gravy)\b""")

    // Cuisine: only real cuisines, so labels like "Butter Cuisine" never become tags.

    private val cuisines = mapOf(
        "american" to "American", "southern" to "Southern", "cajun" to "Cajun", "creole" to "Cajun",
        "tex-mex" to "Tex-Mex", "mexican" to "Mexican", "italian" to "Italian", "french" to "French",
        "greek" to "Greek", "mediterranean" to "Mediterranean", "middle eastern" to "Middle Eastern",
        "spanish" to "Spanish", "german" to "German", "british" to "British", "english" to "British",
        "irish" to "Irish", "indian" to "Indian", "chinese" to "Chinese", "japanese" to "Japanese",
        "korean" to "Korean", "thai" to "Thai", "vietnamese" to "Vietnamese", "filipino" to "Filipino",
        "asian" to "Asian", "caribbean" to "Caribbean", "jamaican" to "Caribbean", "cuban" to "Caribbean",
        "hawaiian" to "Hawaiian", "polish" to "Eastern European", "russian" to "Eastern European",
        "ukrainian" to "Eastern European", "hungarian" to "Eastern European", "moroccan" to "Middle Eastern",
        "lebanese" to "Middle Eastern",
    )

    /** Dishes whose name says the cuisine when the site doesn't. */
    private val cuisineTitleWords = listOf(
        "Indian" to words("biryani", "curry", "naan", "tikka", "masala", "dal", "samosas?"),
        "Mexican" to words("tacos?", "enchiladas?", "quesadillas?", "burritos?", "tamales?", "carnitas", "pozole"),
        "Italian" to words("lasagna", "risotto", "carbonara", "bolognese", "gnocchi", "marinara", "parmesan chicken", "chicken parmesan"),
        "Chinese" to words("chinese", "lo mein", "chow mein", "fried rice", "kung pao", "char siu"),
        "Vietnamese" to words("vietnamese", "pho", "banh mi", "cha gio"),
        "German" to words("bierocks?", "runza", "schnitzel", "spaetzle", "sauerkraut"),
    )

    fun tags(recipe: TaggableRecipe): TagResult {
        val tags = linkedSetOf<String>()
        val title = recipe.title.lowercase()
        val siteLabels = (recipe.siteCategories + recipe.siteKeywords).map { it.lowercase().trim() }

        // Main ingredient, which some course rules need.
        val ingredientText = recipe.ingredients.joinToString("\n") { it.lowercase() }
        val proteinTags = proteins.filter { protein -> protein.words.any { mainIngredientMention(ingredientText, it) } }.map { it.tag }

        // Course. The title says best what a dish is, so a course it names overrides
        // site labels that contradict it, such as "main dish" on a protein shake.
        siteLabels.mapNotNullTo(tags) { courseLabels[it] }
        val titleCourse = titleRules.firstOrNull { it.tag != DOUGH && it.pattern.containsMatchIn(title) }?.tag
        if (titleCourse != null) {
            tags += titleCourse
            when (titleCourse) {
                MAIN_DISH -> tags -= DESSERT
                DESSERT, BREAKFAST, DRINK, SAUCE, BREAD -> tags -= setOf(MAIN_DISH, APPETIZER, SIDE_DISH)
            }
        }
        // Dough and crust recipes are made to go into something else, unlike sausage rolls.
        val dough = titleRules.first { it.tag == DOUGH }
        if (dough.pattern.containsMatchIn(title) && proteinTags.isEmpty()) tags += DOUGH
        val savory = tags.none { it in setOf(DESSERT, BREAKFAST, BREAD, SAUCE, DRINK, DOUGH, SNACK) }
        if (savory) tags += proteinTags
        // A dish built around meat or fish, with no other course, is a main dish.
        if (savory && proteinTags.isNotEmpty() && tags.none { it in setOf(SOUP, SALAD, APPETIZER, SIDE_DISH, MAIN_DISH) }) {
            tags += MAIN_DISH
        }
        val hasMeat = meatWords.any { Regex("""\b$it""").containsMatchIn(ingredientText) }
        if (MAIN_DISH in tags && recipe.ingredients.isNotEmpty() && !hasMeat) tags += VEGETARIAN

        // Method
        val methodText = (listOf(recipe.title) + recipe.steps).joinToString("\n") { it.lowercase() }
        methods.filter { it.pattern.containsMatchIn(methodText) }.mapTo(tags) { it.tag }

        // Cuisine
        for (value in recipe.siteCuisines + recipe.siteCategories) {
            val key = value.lowercase().trim().removeSuffix(" cuisine").trim()
            cuisines[key]?.let(tags::add)
        }
        cuisineTitleWords.filter { it.second.containsMatchIn(title) }.mapTo(tags) { it.first }

        // Time
        if (recipe.totalMinutes != null && recipe.totalMinutes <= 30) tags += QUICK

        // Occasions are suggested from the title and the site's own labels, not ingredients,
        // so a pinch of pumpkin pie spice doesn't make a recipe a Thanksgiving dish.
        val occasionText = (listOf(title) + siteLabels).joinToString("\n")
        val suggestions = occasions.filter { it.pattern.containsMatchIn(occasionText) }.map { it.tag }.toSet()
        return TagResult(tags, suggestions - tags)
    }

    private fun mainIngredientMention(text: String, word: String): Boolean =
        Regex("""\b$word\w*""").findAll(text).any { match ->
            !flavoringOnly.matchesAt(text, match.range.last + 1)
        }
}
