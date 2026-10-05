package io.github.isaiahyoder.recipebox.ingredients

/**
 * Makes a counted ingredient agree with its new amount, so "1 medium onion"
 * doubled reads "2 medium onions" and "2 eggs" halved reads "1 egg".
 *
 * Only the word before the first comma or parenthesis changes, because that
 * is where ingredient lines put the thing being counted.
 */
object Nouns {
    private val uncountable = setOf(
        "garlic", "salt", "pepper", "butter", "rice", "water", "flour", "sugar", "oil", "milk", "cream",
        "cheese", "spinach", "parsley", "cilantro", "basil", "corn", "broth", "stock", "honey", "yeast",
    )
    private val irregularPlurals = mapOf("leaf" to "leaves", "loaf" to "loaves", "half" to "halves", "knife" to "knives")
    private val irregularSingulars = irregularPlurals.entries.associate { (one, many) -> many to one }
    private val esAfterO = setOf("tomato", "potato", "mango")

    fun matchCount(rest: String, wasPlural: Boolean, isPlural: Boolean): String {
        if (wasPlural == isPlural || rest.isEmpty() || rest.startsWith("(")) return rest
        val end = rest.indexOfAny(charArrayOf(',', '(', ';')).let { if (it < 0) rest.length else it }
        val head = rest.substring(0, end).trimEnd()
        val tail = rest.substring(head.length)
        val start = head.lastIndexOf(' ') + 1
        val word = head.substring(start)
        if (word.isEmpty() || !word.all { it.isLetter() || it == '-' }) return rest
        val changed = if (isPlural) pluralize(word) else singularize(word)
        return head.substring(0, start) + changed + tail
    }

    fun pluralize(word: String): String {
        val lower = word.lowercase()
        return when {
            lower in uncountable -> word
            lower.endsWith("s") -> word
            lower in irregularPlurals -> keepCase(word, irregularPlurals.getValue(lower))
            lower in esAfterO -> word + "es"
            lower.endsWith("ch") || lower.endsWith("sh") || lower.endsWith("x") || lower.endsWith("z") -> word + "es"
            lower.length > 1 && lower.endsWith("y") && lower[lower.length - 2] !in "aeiou" -> word.dropLast(1) + "ies"
            else -> word + "s"
        }
    }

    fun singularize(word: String): String {
        val lower = word.lowercase()
        return when {
            lower in irregularSingulars -> keepCase(word, irregularSingulars.getValue(lower))
            lower.endsWith("ss") || !lower.endsWith("s") -> word
            lower.endsWith("ies") -> word.dropLast(3) + "y"
            lower.endsWith("oes") && lower.dropLast(2) in esAfterO -> word.dropLast(2)
            lower.endsWith("ches") || lower.endsWith("shes") || lower.endsWith("xes") -> word.dropLast(2)
            else -> word.dropLast(1)
        }
    }

    private fun keepCase(original: String, replacement: String) =
        if (original.first().isUpperCase()) replacement.replaceFirstChar { it.uppercase() } else replacement
}
