package io.github.isaiahyoder.recipebox.ingredients

/**
 * Turns the name part of an ingredient line into something to shop for and
 * a key that matches the same ingredient across recipes.
 *
 * "shredded rotisserie chicken" and "2 eggs, beaten" become "rotisserie
 * chicken" and "egg". Merging is cautious: two lines merge only when their
 * cleaned names match exactly, because a wrong merge hides something she
 * needs while a missed merge only shows a near-duplicate.
 */
object IngredientNames {
    private val preparationWords = setOf(
        "chopped", "diced", "minced", "sliced", "shredded", "grated", "fresh", "freshly", "large", "medium",
        "small", "finely", "coarsely", "thinly", "roughly", "peeled", "crushed", "divided", "softened",
        "melted", "beaten", "cooked", "uncooked", "packed", "sifted", "cubed", "halved", "quartered",
        "optional", "about", "hard", "boiled", "hard-boiled", "lightly", "well", "room", "temperature",
        "cold", "warm", "hot", "torn", "trimmed", "rinsed", "drained", "seeded", "deveined", "julienned",
        "zested", "juiced", "pitted", "toasted", "of", "into", "pieces", "cut", "plus", "more",
        "taste", "to", "for", "serving", "garnish", "needed", "as", "desired", "extra",
        // Loose amounts that aren't units: "small handful cilantro", "dollop of sour cream".
        "handful", "handfuls", "dollop", "dollops", "splash", "drizzle", "sprinkle", "sprinkling", "few",
    )

    /** Joining words kept inside a name ("milk or broth") but not at its ends. */
    private val connectors = setOf("and", "or", "with")

    /** A few common ingredients with two names, mapped to one. */
    private val synonyms = mapOf(
        "scallion" to "green onion",
        "spring onion" to "green onion",
        "garbanzo bean" to "chickpea",
        "coriander leaf" to "cilantro",
        "confectioners sugar" to "powdered sugar",
        "icing sugar" to "powdered sugar",
        "white sugar" to "sugar",
        "granulated sugar" to "sugar",
        "all-purpose flour" to "flour",
        "ground black pepper" to "black pepper",
        // A recipe that says just "pepper" almost always means black pepper.
        "pepper" to "black pepper",
        "ground pepper" to "black pepper",
    )

    /** Names that most kitchens already have; a list can hide them. */
    private val staples = setOf(
        "salt", "kosher salt", "sea salt", "black pepper", "salt and pepper", "salt and black pepper", "water", "ice",
        "cooking spray", "table salt",
    )

    /** [display] keeps the words as written; [key] is singular and matches across recipes. */
    data class Name(val display: String, val key: String)

    /** [rest] is the line after its amount and unit, such as "eggs, beaten". */
    fun clean(rest: String): Name {
        var text = rest.replace(Regex("""\([^)]*\)"""), " ")
        text = text.split(',', ';').first()
        text = text.replace(Regex("""\bto taste\b|\bas needed\b|\bfor serving\b""", RegexOption.IGNORE_CASE), " ")
        val words = text.lowercase()
            .replace(Regex("""[^a-z\-' ]"""), " ")
            .split(Regex("""\s+"""))
            .filter { it.isNotBlank() && it !in preparationWords }
            .dropWhile { it in connectors }
            .dropLastWhile { it in connectors }
        if (words.isEmpty()) {
            val fallback = rest.trim().lowercase()
            return Name(fallback, fallback)
        }
        val singular = (words.dropLast(1) + Nouns.singularize(words.last())).joinToString(" ")
        val key = synonyms[singular] ?: singular
        val display = if (key != singular) key else words.joinToString(" ")
        return Name(display = display, key = key)
    }

    fun isStaple(key: String): Boolean = key in staples
}
