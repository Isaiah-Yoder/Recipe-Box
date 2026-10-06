package io.github.isaiahyoder.recipebox.ingredients

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/** How a displayed line differs from the line as written. */
data class DisplayIngredient(
    val text: String,
    /** True when the amount or unit shown differs from the original line. */
    val changed: Boolean,
    /** True when the line has no amount the app could read, so it isn't scaled. */
    val unscaled: Boolean,
    /** True when the amount was converted to the other unit system. */
    val converted: Boolean,
)

/**
 * Scales an ingredient and, when asked, shows it in US or metric units.
 *
 * Conversion stays within volume or within weight, except for dry
 * ingredients with a known density, such as flour and sugar: grams of flour
 * show as about so many cups, and cups of flour as about so many grams,
 * because that's how each kind of recipe measures them.
 */
object IngredientScaler {
    private const val ML_PER_TEASPOON = 4.928922
    private const val GRAMS_PER_OUNCE = 28.349523
    private const val TEASPOONS_PER_CUP = 48.0

    fun display(line: String, factor: Double, toUsUnits: Boolean): DisplayIngredient =
        display(IngredientParser.parse(line), factor, if (toUsUnits) UnitSystem.US else null)

    fun display(line: String, factor: Double, units: UnitSystem?): DisplayIngredient =
        display(IngredientParser.parse(line), factor, units)

    fun display(parsed: ParsedIngredient, factor: Double, toUsUnits: Boolean): DisplayIngredient =
        display(parsed, factor, if (toUsUnits) UnitSystem.US else null)

    /** Shows [parsed] scaled by [factor] in [units], or in its own units when [units] is null. */
    fun display(parsed: ParsedIngredient, factor: Double, units: UnitSystem?): DisplayIngredient {
        val quantity = parsed.quantity
            ?: return DisplayIngredient(parsed.original, changed = false, unscaled = factor != 1.0, converted = false)
        val unit = parsed.unit
        val toMetric = units == UnitSystem.METRIC && (unit?.measure == Measure.US_VOLUME || unit?.measure == Measure.US_WEIGHT)
        if (toMetric) return displayMetric(parsed, quantity.times(factor), unit!!)
        val convert = units == UnitSystem.US && unit?.isMetric == true
        if (abs(factor - 1.0) < 1e-9 && !convert) {
            return DisplayIngredient(asWritten(parsed, quantity), changed = false, unscaled = false, converted = false)
        }

        val scaled = quantity.times(factor)
        // Grams of flour, sugar, and similar ingredients read best as approximate cups.
        val density = if (convert && unit?.measure == Measure.METRIC_WEIGHT) Densities.gramsPerCup(parsed.rest) else null
        val (amount, unitWord) = when {
            unit == null -> scaled to null
            density != null -> tidyUsVolume(scaled.times(unit.base / density * TEASPOONS_PER_CUP), converted = true)
            convert && unit.measure == Measure.METRIC_VOLUME ->
                tidyUsVolume(scaled.times(unit.base / ML_PER_TEASPOON), converted = true)
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

        val parts = listOfNotNull(
            "about".takeIf { density != null },
            formatQuantity(amount),
            unitWord,
            rest.takeIf { it.isNotEmpty() },
        )
        return DisplayIngredient(parts.joinToString(" "), changed = true, unscaled = false, converted = convert)
    }

    /**
     * US volumes become milliliters and US weights grams. A dry ingredient
     * measured by volume, such as cups of flour, becomes about so many grams
     * when its density is known, because metric recipes weigh it.
     */
    private fun displayMetric(parsed: ParsedIngredient, scaled: Quantity, unit: Unit): DisplayIngredient {
        val density = if (unit.measure == Measure.US_VOLUME && !Densities.isLiquid(parsed.rest)) Densities.gramsPerCup(parsed.rest) else null
        val text = when {
            density != null -> "about " + metricWeight(scaled.times(unit.base / TEASPOONS_PER_CUP * density))
            unit.measure == Measure.US_VOLUME -> metricVolume(scaled.times(unit.base * KITCHEN_ML_PER_TEASPOON))
            else -> metricWeight(scaled.times(unit.base * GRAMS_PER_OUNCE))
        }
        val parts = listOf(text, parsed.rest).filter { it.isNotEmpty() }
        return DisplayIngredient(parts.joinToString(" "), changed = true, unscaled = false, converted = true)
    }

    /** "15 ml", "240 ml", or "1.5 L", rounded the way metric recipes write them. */
    fun metricVolumeText(milliliters: Double): String = metricVolume(Quantity(milliliters))

    /** "30 g", "225 g", or "1.4 kg". */
    fun metricWeightText(grams: Double): String = metricWeight(Quantity(grams))

    private fun metricVolume(ml: Quantity): String =
        if (ml.high >= 1000 - 1e-9) metricRange(ml.times(0.001), "L", ::tenths) else metricRange(ml, "ml", ::roundMilliliters)

    private fun metricWeight(grams: Quantity): String =
        if (grams.high >= 1000 - 1e-9) metricRange(grams.times(0.001), "kg", ::tenths) else metricRange(grams, "g", ::roundGrams)

    private fun metricRange(amount: Quantity, unit: String, round: (Double) -> String): String {
        val low = round(amount.low)
        val high = round(amount.high)
        return if (amount.isRange && low != high) "$low to $high $unit" else "$high $unit"
    }

    /** Teaspoons and tablespoons land on 2.5, 5, and 15 ml; cups on tens. */
    private fun roundMilliliters(ml: Double): String = when {
        // Quarter and half teaspoons are 1.25 and 2.5 ml. Halves round to even, so ⅛ teaspoon is 0.5 ml.
        ml < 2.5 -> trimZero(Math.rint(ml * 4) / 4).ifEmpty { "0.25" }
        ml < 5 -> trimZero(Math.rint(ml * 2) / 2)
        ml < 100 -> ((ml / 5).roundToLong() * 5).toString()
        else -> ((ml / 10).roundToLong() * 10).toString()
    }

    private fun roundGrams(grams: Double): String = when {
        grams < 20 -> grams.roundToLong().coerceAtLeast(1).toString()
        grams < 250 -> ((grams / 5).roundToLong() * 5).toString()
        else -> ((grams / 10).roundToLong() * 10).toString()
    }

    private fun tenths(value: Double): String = trimZero((value * 10).roundToLong() / 10.0)

    private fun trimZero(value: Double): String =
        if (value == 0.0) "" else if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

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

    /** "1½ cups" for an amount in teaspoons, using the spoon and cup sizes people own. */
    fun usVolumeText(teaspoons: Double): String {
        val (amount, word) = tidyUsVolume(Quantity(teaspoons))
        return "${formatQuantity(amount)} $word"
    }

    /** "12 ounces" or "1½ pounds" for an amount in ounces. */
    fun usWeightText(ounces: Double): String {
        val (amount, word) = tidyUsWeight(Quantity(ounces))
        return "${formatQuantity(amount)} $word"
    }

    const val TEASPOONS_PER_ML = 1 / ML_PER_TEASPOON

    /**
     * Metric recipes use 5 ml teaspoons, 15 ml tablespoons, and 240 ml cups,
     * slightly rounder than the exact US sizes, so conversions to metric use them.
     */
    const val KITCHEN_ML_PER_TEASPOON = 5.0
    const val OUNCES_PER_GRAM = 1 / GRAMS_PER_OUNCE

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
    private fun tidyUsVolume(teaspoons: Quantity, converted: Boolean = false): Pair<Quantity, String> {
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
        val exact = teaspoons.times(1.0 / unit.base)
        // A converted metric amount is rounded to what the spoon or cup set can measure:
        // 50 ml is 3½ tablespoons, not 3⅜. Scaled US amounts stay exact.
        val amount = if (measurer != null || !converted) {
            exact
        } else {
            val fractions = measurers.first { it.unit == unit }.fractions
            Quantity(measurable(exact.low, fractions), measurable(exact.high, fractions))
        }
        return amount to unit.word(amount.high)
    }

    /** The closest amount made of whole units and one of [fractions], never zero. */
    private fun measurable(amount: Double, fractions: List<Double>): Double {
        val whole = floor(amount)
        val candidates = (fractions.map { whole + it } + (whole + 1)).filter { it > 0 }
        return candidates.minBy { abs(it - amount) }
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
