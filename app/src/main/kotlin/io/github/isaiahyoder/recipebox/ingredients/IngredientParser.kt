package io.github.isaiahyoder.recipebox.ingredients

/** An amount, or a range such as "2 to 3". */
data class Quantity(val low: Double, val high: Double = low) {
    val isRange: Boolean get() = high != low
    fun times(factor: Double) = Quantity(low * factor, high * factor)
}

/**
 * An ingredient line split into parts.
 *
 * [unitText] is the unit exactly as written, so an unchanged line keeps its
 * own abbreviations. [rest] is everything after the unit, such as
 * "all-purpose flour, sifted". A line without a leading amount, such as
 * "salt to taste", has a null [quantity] and is never scaled.
 */
data class ParsedIngredient(
    val original: String,
    val quantity: Quantity?,
    val unit: Unit?,
    val unitText: String?,
    val rest: String,
)

object IngredientParser {
    private const val NUMBER = """(?:\d+\s+\d+\s*/\s*\d+|\d+\s*/\s*\d+|\d+(?:\.\d+)?|\.\d+)"""
    private val leadingQuantity = Regex(
        """^\s*($NUMBER)(?:\s*(?:-|–|—|to|or)\s*($NUMBER))?(?=\s|$|[a-zA-Z(])"""
    )

    fun parse(line: String): ParsedIngredient {
        val text = Fractions.normalize(line).trim()
        val match = leadingQuantity.find(text)
            ?: return ParsedIngredient(line, null, null, null, line.trim())
        val low = parseNumber(match.groupValues[1])
        val high = match.groupValues[2].takeIf { it.isNotEmpty() }?.let(::parseNumber) ?: low
        if (low == null || high == null) return ParsedIngredient(line, null, null, null, line.trim())

        val afterQuantity = text.substring(match.range.last + 1).trimStart()
        val (unit, unitText, rest) = matchUnit(afterQuantity)
        return ParsedIngredient(line, Quantity(low, high), unit, unitText, rest)
    }

    /** Reads a unit at the start of [text]: the unit, the unit as written, and the text after it. */
    fun matchUnit(text: String): Triple<Unit?, String?, String> {
        for ((alias, unit) in Unit.aliasTable) {
            if (text.length < alias.length) continue
            val candidate = text.substring(0, alias.length)
            if (!Unit.aliasMatches(alias, candidate)) continue
            var end = alias.length
            // A unit must end at a word boundary: "c" in "carrots" isn't a cup.
            if (end < text.length && text[end].isLetter()) continue
            if (end < text.length && text[end] == '.') end++
            return Triple(unit, text.substring(0, end), text.substring(end).trimStart())
        }
        return Triple(null, null, text)
    }

    private fun parseNumber(text: String): Double? {
        val parts = text.trim().split(Regex("""\s+"""))
        return when {
            parts.size >= 2 && '/' in text -> {
                val whole = parts[0].toDoubleOrNull() ?: return null
                val fraction = parseFraction(parts.drop(1).joinToString("")) ?: return null
                whole + fraction
            }
            '/' in text -> parseFraction(text.replace(" ", ""))
            else -> text.toDoubleOrNull()
        }
    }

    private fun parseFraction(text: String): Double? {
        val (n, d) = text.split('/').takeIf { it.size == 2 } ?: return null
        val numerator = n.toDoubleOrNull() ?: return null
        val denominator = d.toDoubleOrNull()?.takeIf { it != 0.0 } ?: return null
        return numerator / denominator
    }
}
