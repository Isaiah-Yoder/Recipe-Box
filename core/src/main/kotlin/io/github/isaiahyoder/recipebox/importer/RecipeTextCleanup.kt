package io.github.isaiahyoder.recipebox.importer

import io.github.isaiahyoder.recipebox.model.RecipeLine
import io.github.isaiahyoder.recipebox.ingredients.Fractions
import kotlin.math.abs

/**
 * Tidies text that recipe sites publish for search engines, which often
 * differs from what the page shows: doubled parentheses, "teaspoon(s)",
 * "0.5 cup", shopping-link remarks, and titles with taglines.
 */
object RecipeTextCleanup {
    private val leadingAmount = Regex("""^\s*(\d+(?:\.\d+)?(?:\s+\d+/\d+)?|\d+/\d+|\.\d+|[½¼¾⅓⅔⅛])""")
    private val decimalAmount = Regex("""^(\d*\.\d+)(?=\s)""")
    private val leadingRemark = Regex("""^\s*(to taste|as needed|as required)\s+(.+)$""", RegexOption.IGNORE_CASE)
    private val repeatedAmount = Regex("""^(\S+(?:\s+[a-zA-Z]+\.?)?)\s+(.+?)\s+[-–]\s+\1\s*$""")
    private val dashAfterAmount = Regex("""^([\d½¼¾⅓⅔⅛⅜⅝⅞/.\s]+?)\s+-\s+""")
    private val pluralMarker = Regex("""(\p{L}+)\(s\)""")
    private val doubleWrapped = Regex("""\(\(([^()]*)\)\)""")
    private val linkRemark = Regex("""\s*\([^()]*\b(?:click|affiliate|amazon|my favorite)\b[^()]*\)""", RegexOption.IGNORE_CASE)
    private const val MAX_HEADER_LENGTH = 60

    /** Cleans one ingredient line. A short line ending in a colon, such as "For the crust:", becomes a group heading. */
    fun ingredient(text: String): RecipeLine? {
        var line = squeeze(text)
        // "((low sodium))" and "( (2 cups))" come from a site wrapping notes that already had
        // parentheses. Nested remarks such as "(soft (about 65°F))" are left alone.
        var previous: String
        do {
            previous = line
            line = line.replace(Regex("""\(\s*,?\s*"""), "(")
                .replace(Regex("""\s+\)"""), ")")
                .replace(doubleWrapped, "($1)")
        } while (line != previous)
        line = balanceParentheses(line.replace("()", "").replace(linkRemark, ""))
        val amount = leadingAmount.find(line)?.groupValues?.get(1)?.let(::amountValue)
        line = line.replace(pluralMarker) { match ->
            match.groupValues[1] + if (amount != null && amount > 1.0) "s" else ""
        }
        line = line.replace(dashAfterAmount, "$1 ")
        // "to taste Salt" puts the remark where the amount goes: "Salt, to taste".
        line = line.replace(leadingRemark, "$2, $1")
        // "4 bunches spinach leaves - 4 bunches" repeats the amount after the name.
        line = line.replace(repeatedAmount, "$1 $2")
        line = line.replace(decimalAmount) { match -> kitchenAmount(match.value) ?: match.value }
        line = squeeze(line).trim().trimEnd(',').trim()
        if (line.isEmpty()) return null
        return if (line.endsWith(":") && line.length <= MAX_HEADER_LENGTH) {
            RecipeLine(heading(line.dropLast(1).trim()), isHeader = true)
        } else {
            RecipeLine(line)
        }
    }

    /**
     * Removes what reads like marketing from a title: the word "recipe", a
     * brace remark such as "{Ever}", and a tagline after a dash or colon.
     */
    fun title(text: String): String {
        val original = squeeze(text)
        var title = original.replace(Regex("""\s*\{[^}]*\}"""), "")
        for (separator in listOf(" – ", " — ", ": ", " | ")) {
            val head = title.substringBefore(separator, missingDelimiterValue = title)
            if (head != title && head.length >= 3) title = head
        }
        title = title.replace(Regex("""\s*\brecipe\b""", RegexOption.IGNORE_CASE), "")
        return squeeze(title).trim(' ', '-', '–', ':').ifBlank { original }
    }

    /** A group heading written in capitals, such as "TOPPINGS", reads as "Toppings". */
    fun heading(text: String): String =
        if (text.any { it.isLetter() } && text == text.uppercase()) text.lowercase().replaceFirstChar { it.uppercase() } else text

    fun siteName(text: String): String = squeeze(text.replace(Regex("""[®™©]"""), "")).trim()

    /** "4 serving(s)" becomes "4 servings". */
    fun yieldText(text: String): String {
        val amount = leadingAmount.find(text)?.groupValues?.get(1)?.let(::amountValue)
        return squeeze(text.replace(pluralMarker) { it.groupValues[1] + if (amount != null && amount > 1.0) "s" else "" })
    }

    /** Closes a parenthesis a site left open, or drops a stray closing one. */
    private fun balanceParentheses(text: String): String {
        val result = StringBuilder()
        var open = 0
        for (ch in text) {
            when (ch) {
                '(' -> open++
                ')' -> if (open == 0) continue else open--
            }
            result.append(ch)
        }
        repeat(open) { result.append(')') }
        return result.toString()
    }

    /** "0.5" becomes "½" when it's a kitchen fraction; other decimals stay. */
    private fun kitchenAmount(text: String): String? {
        val value = text.toDoubleOrNull() ?: return null
        return if (value > 0 && abs(Fractions.rounded(value) - value) < 0.02) Fractions.format(value) else null
    }

    private fun amountValue(text: String): Double? {
        val normalized = Fractions.normalize(text).trim()
        return normalized.split(Regex("""\s+""")).sumOf { part ->
            if ('/' in part) {
                val (top, bottom) = part.split('/').map { it.toDoubleOrNull() ?: return null }
                if (bottom == 0.0) return null else top / bottom
            } else {
                part.toDoubleOrNull() ?: return null
            }
        }
    }

    private fun squeeze(text: String) = text.replace(Regex("""\s+"""), " ").trim()
}
