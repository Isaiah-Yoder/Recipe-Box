package io.github.isaiahyoder.recipebox.ingredients

import kotlin.math.abs
import kotlin.math.floor

/** How a displayed line differs from the line as written. */
data class DisplayIngredient(
    val text: String,
    /** True when the amount or unit shown differs from the original line. */
    val changed: Boolean,
    /** True when the line has no amount the app could read, so it isn't scaled. */
    val unscaled: Boolean,
    /** True when a metric amount was converted to US units. */
    val converted: Boolean,
)

/**
 * Scales an ingredient and, when asked, shows metric amounts in US units.
 *
 * Conversion stays within volume or within weight. Volume never becomes
 * weight, because that needs a density for each ingredient.
 */
object IngredientScaler {
    private const val ML_PER_TEASPOON = 4.928922
    private const val GRAMS_PER_OUNCE = 28.349523

    fun display(line: String, factor: Double, toUsUnits: Boolean): DisplayIngredient =
        display(IngredientParser.parse(line), factor, toUsUnits)

    fun display(parsed: ParsedIngredient, factor: Double, toUsUnits: Boolean): DisplayIngredient {
        val quantity = parsed.quantity
            ?: return DisplayIngredient(parsed.original, changed = false, unscaled = factor != 1.0, converted = false)
        val convert = toUsUnits && parsed.unit?.isMetric == true
        if (abs(factor - 1.0) < 1e-9 && !convert) {
            return DisplayIngredient(asWritten(parsed, quantity), changed = false, unscaled = false, converted = false)
        }

        val scaled = quantity.times(factor)
        val unit = parsed.unit
        val (amount, unitWord) = when {
            unit == null -> scaled to null
            convert && unit.measure == Measure.METRIC_VOLUME ->
                tidyUsVolume(scaled.times(unit.base / ML_PER_TEASPOON))
            convert && unit.measure == Measure.METRIC_WEIGHT ->
                tidyUsWeight(scaled.times(unit.base / GRAMS_PER_OUNCE))
            unit in tidyVolumeUnits -> tidyUsVolume(scaled.times(unit.base))
            unit == Unit.OUNCE || unit == Unit.POUND -> tidyUsWeight(scaled.times(unit.base))
            else -> scaled to sameUnitWord(parsed, scaled.high)
        }
        val rest = if (unit == null) {
            Nouns.matchCount(parsed.rest, Fractions.isPlural(quantity.high), Fractions.isPlural(amount.high))
        } else {
            parsed.rest
        }

        val parts = listOfNotNull(formatQuantity(amount), unitWord, rest.takeIf { it.isNotEmpty() })
        return DisplayIngredient(parts.joinToString(" "), changed = true, unscaled = false, converted = convert)
    }

    /**
     * The line as written, with "1 1/2" or "1.5" shown as "1½". The original
     * text is kept when kitchen fractions can't show the amount exactly.
     */
    private fun asWritten(parsed: ParsedIngredient, quantity: Quantity): String {
        val exact = listOf(quantity.low, quantity.high).all { abs(Fractions.rounded(it) - it) < 0.01 * it }
        if (!exact) return parsed.original
        return listOfNotNull(formatQuantity(quantity), parsed.unitText, parsed.rest.takeIf { it.isNotEmpty() })
            .joinToString(" ")
    }

    private val tidyVolumeUnits = setOf(Unit.TEASPOON, Unit.TABLESPOON, Unit.CUP)

    /**
     * A measuring spoon or cup and the fractions that sets of them can
     * measure. Nobody owns a ⅓-tablespoon spoon, so 4 teaspoons stays in
     * teaspoons rather than becoming 1⅓ tablespoons, and large amounts stay
     * in cups, so 1⅛ cups doesn't become 18 tablespoons.
     */
    private class Measurer(
        val unit: Unit,
        val minimum: Double,
        val maximum: Double,
        val fractions: List<Double>,
    )

    private val measurers = listOf(
        Measurer(Unit.CUP, 0.25, Double.MAX_VALUE, listOf(0.0, 0.25, 1.0 / 3, 0.5, 2.0 / 3, 0.75)),
        Measurer(Unit.TABLESPOON, 1.0, 8.0, listOf(0.0, 0.5)),
        Measurer(Unit.TEASPOON, 0.0, 5.0, listOf(0.0, 0.125, 0.25, 0.5, 0.75)),
    )

    /** Picks teaspoons, tablespoons, or cups for an amount given in teaspoons. */
    private fun tidyUsVolume(teaspoons: Quantity): Pair<Quantity, String> {
        val measurer = measurers.firstOrNull { m ->
            listOf(teaspoons.low, teaspoons.high).all { tsp ->
                val amount = tsp / m.unit.base
                val fraction = amount - floor(amount + 0.02)
                amount >= m.minimum - 0.02 && amount <= m.maximum + 0.02 &&
                    m.fractions.any { abs(it - fraction) < 0.02 }
            }
        }
        val unit = measurer?.unit ?: when {
            // Converted metric amounts rarely land on exact fractions; use the closest sensible unit.
            teaspoons.high < 3.0 - 1e-9 -> Unit.TEASPOON
            teaspoons.high < 12.0 - 1e-9 -> Unit.TABLESPOON
            else -> Unit.CUP
        }
        val amount = teaspoons.times(1.0 / unit.base)
        return amount to unit.word(amount.high)
    }

    /** Picks ounces or pounds for an amount given in ounces. */
    private fun tidyUsWeight(ounces: Quantity): Pair<Quantity, String> {
        val unit = if (ounces.high >= 16.0 - 1e-9) Unit.POUND else Unit.OUNCE
        val amount = ounces.times(1.0 / unit.base)
        return amount to unit.word(amount.high)
    }

    /** Keeps the unit as written, adjusting a full word between singular and plural. */
    private fun sameUnitWord(parsed: ParsedIngredient, amount: Double): String? {
        val unit = parsed.unit ?: return null
        val written = parsed.unitText ?: return null
        val isFullWord = written.equals(unit.singular, ignoreCase = true) ||
            written.equals(unit.plural, ignoreCase = true)
        return if (isFullWord) unit.word(amount) else written
    }

    private fun formatQuantity(quantity: Quantity): String {
        val low = Fractions.format(quantity.low)
        val high = Fractions.format(quantity.high)
        return if (quantity.isRange && low != high) "$low to $high" else high
    }
}
