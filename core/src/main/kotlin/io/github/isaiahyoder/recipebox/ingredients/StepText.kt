package io.github.isaiahyoder.recipebox.ingredients

import kotlin.math.abs
import kotlin.math.roundToInt

/** A piece of a step; [changed] pieces were scaled or converted and are shown highlighted. */
data class StepPiece(val text: String, val changed: Boolean)

/**
 * Scales and converts amounts written inside step text: "Add 2 cups of
 * flour", "Bake at 180 °C", "a 23 x 33 cm pan". Times such as "10 minutes"
 * are never touched, and neither are amounts without a unit, because
 * "Add 2" could mean anything.
 */
object StepText {
    private const val NUMBER = """(?:\d+\s+\d+/\d+|\d+/\d+|\d+(?:\.\d+)?|[½⅓⅔¼¾⅛⅜⅝⅞])"""

    private val temperature = Regex(
        """(\d{2,3})\s*(?:°\s*|º\s*|degrees?\s+)(C|F|Celsius|Fahrenheit)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val panSize = Regex(
        """(\d+(?:\.\d+)?)\s*(?:x|×|by)\s*(\d+(?:\.\d+)?)\s*(?:cm|centimet(?:er|re)s?)\b|(\d+(?:\.\d+)?)\s*-?\s*(?:cm|centimet(?:er|re)s?)\b""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Inch sizes such as "9x13-inch pan", "9 x 13 baking dish", or "1-inch pieces".
     * A bare pair counts only before pan, dish, or sheet, so "2 x 3" alone isn't changed.
     */
    private val inchSize = Regex(
        """(\d+(?:\.\d+)?)\s*(?:x|×|by)\s*(\d+(?:\.\d+)?)(?:\s*-?\s*(?:inch(?:es)?\b|in\.|"|″)|(?=\s*(?:inch|baking|pan|dish|sheet|cake)))""" +
            """|(\d+(?:\.\d+)?)\s*-?\s*(?:inch(?:es)?\b|″)""",
        RegexOption.IGNORE_CASE,
    )

    /** Units that are safe to recognize inside sentences; single letters such as "c" are left out. */
    private val stepUnits = Unit.aliasTable.filter { (alias, _) -> alias.length > 1 || alias == "g" }
        .map { it.first }
    private val quantity = Regex(
        """(?<![\w.])($NUMBER(?:\s*(?:-|–|to)\s*$NUMBER)?)\s*(${stepUnits.joinToString("|") { Regex.escape(it) }})\.?(?![a-zA-Z])""",
    )

    fun display(text: String, factor: Double, toUsUnits: Boolean): List<StepPiece> =
        display(text, factor, if (toUsUnits) UnitSystem.US else null)

    /** Scales amounts in [text] by [factor] and shows them in [units], or as written when [units] is null. */
    fun display(text: String, factor: Double, units: UnitSystem?): List<StepPiece> {
        val replacements = mutableListOf<Pair<IntRange, String>>()
        fun free(range: IntRange) = replacements.none { (r, _) -> r.first <= range.last && range.first <= r.last }

        if (units == UnitSystem.METRIC) {
            temperature.findAll(text).forEach { match ->
                val unit = match.groupValues[2].first().uppercaseChar()
                if (unit == 'F') {
                    replacements += match.range to "${celsius(match.groupValues[1].toInt())}°C"
                } else {
                    replacements += match.range to match.value
                }
            }
            inchSize.findAll(text).forEach { match ->
                if (!free(match.range)) return@forEach
                val converted = if (match.groupValues[1].isNotEmpty()) {
                    "${centimeters(match.groupValues[1].toDouble())} x ${centimeters(match.groupValues[2].toDouble())} cm"
                } else {
                    "${centimeters(match.groupValues[3].toDouble())} cm"
                }
                replacements += match.range to converted
            }
        } else if (units == UnitSystem.US) {
            temperature.findAll(text).forEach { match ->
                val unit = match.groupValues[2].first().uppercaseChar()
                if (unit == 'C') {
                    replacements += match.range to "${fahrenheit(match.groupValues[1].toInt())}°F"
                }
            }
            panSize.findAll(text).forEach { match ->
                if (!free(match.range)) return@forEach
                val converted = if (match.groupValues[1].isNotEmpty()) {
                    "${inches(match.groupValues[1].toDouble())} x ${inches(match.groupValues[2].toDouble())} in"
                } else {
                    "${inches(match.groupValues[3].toDouble())} in"
                }
                replacements += match.range to converted
            }
        } else {
            // Keep temperatures from being read as amounts.
            temperature.findAll(text).forEach { replacements += it.range to it.value }
        }

        quantity.findAll(text).forEach { match ->
            if (!free(match.range)) return@forEach
            val parsed = IngredientParser.parse(match.value)
            if (parsed.unit == null || parsed.quantity == null) return@forEach
            val shown = IngredientScaler.display(parsed, factor, units)
            if (shown.changed) replacements += match.range to shown.text.trimEnd()
        }

        return assemble(text, replacements.filter { (range, new) -> text.substring(range) != new })
    }

    private fun assemble(text: String, replacements: List<Pair<IntRange, String>>): List<StepPiece> {
        val pieces = mutableListOf<StepPiece>()
        var position = 0
        for ((range, new) in replacements.sortedBy { it.first.first }) {
            if (range.first < position) continue
            if (range.first > position) pieces += StepPiece(text.substring(position, range.first), false)
            pieces += StepPiece(new, true)
            position = range.last + 1
        }
        if (position < text.length) pieces += StepPiece(text.substring(position), false)
        return pieces
    }

    /** Oven temperatures and other Celsius values round to the nearest 5, as metric recipes write them. */
    fun celsius(fahrenheit: Int): Int = (((fahrenheit - 32) * 5.0 / 9.0) / 5).roundToInt() * 5

    private fun centimeters(inches: Double): String = (inches * 2.54).roundToInt().toString()

    /** Oven temperatures round to the 25-degree settings ovens use; others to the nearest 5. */
    fun fahrenheit(celsius: Int): Int {
        val exact = celsius * 9.0 / 5.0 + 32
        val step = if (exact in 240.0..560.0) 25 else 5
        return ((exact / step).roundToInt() * step)
    }

    private fun inches(centimeters: Double): String {
        val value = centimeters / 2.54
        // Pan sizes are whole inches; small measurements keep a kitchen fraction.
        return if (value >= 4) value.roundToInt().toString() else Fractions.format(value).let {
            if (abs(value) < 0.06) "⅛" else it
        }
    }
}
