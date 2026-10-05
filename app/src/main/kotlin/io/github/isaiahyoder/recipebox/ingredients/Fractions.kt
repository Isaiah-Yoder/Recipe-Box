package io.github.isaiahyoder.recipebox.ingredients

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/** Formats amounts the way a cookbook writes them: 1½, not 1.5 or 1.4999. */
object Fractions {
    private val kitchenFractions = listOf(
        0.0 to "",
        1.0 / 8 to "⅛",
        1.0 / 4 to "¼",
        1.0 / 3 to "⅓",
        3.0 / 8 to "⅜",
        1.0 / 2 to "½",
        5.0 / 8 to "⅝",
        2.0 / 3 to "⅔",
        3.0 / 4 to "¾",
        7.0 / 8 to "⅞",
        1.0 to "",
    )

    private val unicodeFractions = mapOf(
        '½' to "1/2", '⅓' to "1/3", '⅔' to "2/3", '¼' to "1/4", '¾' to "3/4",
        '⅕' to "1/5", '⅖' to "2/5", '⅗' to "3/5", '⅘' to "4/5", '⅙' to "1/6",
        '⅚' to "5/6", '⅛' to "1/8", '⅜' to "3/8", '⅝' to "5/8", '⅞' to "7/8",
    )

    /** Rewrites "1½" and "1 ½" as "1 1/2" so one parser handles every form. */
    fun normalize(text: String): String {
        val out = StringBuilder()
        for (ch in text) {
            val ascii = unicodeFractions[ch]
            if (ascii != null) {
                if (out.isNotEmpty() && out.last().isDigit()) out.append(' ')
                out.append(ascii)
            } else if (ch == '⁄') {
                out.append('/')
            } else {
                out.append(ch)
            }
        }
        return out.toString()
    }

    /** The value [format] displays, so callers can tell whether rounding changed it. */
    fun rounded(value: Double): Double {
        if (value <= 0.0) return 0.0
        if (value >= 10.0) return (value * 2).roundToLong() / 2.0
        val whole = floor(value)
        val fraction = kitchenFractions.minBy { abs(it.first - (value - whole)) }.first
        return (whole + fraction).takeIf { it > 0.0 } ?: 0.125
    }

    /** True when the amount is more than one as displayed, so "1.04 cups" reads as "1 cup". */
    fun isPlural(value: Double): Boolean = rounded(value) > 1.0 + 1e-9

    fun format(value: Double): String {
        if (value <= 0.0) return "0"
        // Large amounts don't need eighths; halves read better.
        if (value >= 10.0) {
            val halves = (value * 2).roundToLong()
            val whole = halves / 2
            return if (halves % 2 == 0L) "$whole" else "$whole½"
        }
        var whole = floor(value).toLong()
        val remainder = value - whole
        val (fraction, glyph) = kitchenFractions.minBy { abs(it.first - remainder) }
        if (fraction == 1.0) whole += 1
        if (whole == 0L && glyph.isEmpty()) {
            // Smaller than a sixteenth: show the closest fraction rather than 0.
            return "⅛"
        }
        return when {
            whole == 0L -> glyph
            glyph.isEmpty() -> "$whole"
            else -> "$whole$glyph"
        }
    }
}
