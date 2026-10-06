package io.github.isaiahyoder.recipebox.grocery

import io.github.isaiahyoder.recipebox.ingredients.Fractions
import io.github.isaiahyoder.recipebox.ingredients.IngredientParser
import io.github.isaiahyoder.recipebox.ingredients.IngredientScaler
import io.github.isaiahyoder.recipebox.ingredients.Measure
import io.github.isaiahyoder.recipebox.ingredients.Nouns
import io.github.isaiahyoder.recipebox.ingredients.Unit
import io.github.isaiahyoder.recipebox.ingredients.UnitSystem

data class GroceryRecipeInput(val title: String, val scale: Double, val lines: List<String>)

data class GroceryManualInput(val id: Long, val text: String, val section: StoreSection?, val checked: Boolean)

data class GroceryLineStateInput(val checked: Boolean, val hidden: Boolean, val customText: String?)

data class GroceryLine(
    /** "r:" plus the ingredient key for lines from recipes, "m:" plus the id for typed items. */
    val key: String,
    val text: String,
    /** Titles of the recipes the line came from. */
    val sources: List<String>,
    val checked: Boolean,
    val section: StoreSection,
    /** The cleaned ingredient name, used to remember her section corrections. */
    val nameKey: String,
    val manualId: Long? = null,
) {
    val isManual: Boolean get() = manualId != null
}

data class GrocerySectionGroup(val section: StoreSection, val lines: List<GroceryLine>)

/**
 * Builds a grocery list from scaled recipes and typed items.
 *
 * The same ingredient from several recipes becomes one line. Amounts add up
 * within volume (US and metric together), within weight, and within each
 * counted unit; different kinds of amounts share the line, such as
 * "2 cloves + 1 teaspoon garlic". Lines without an amount, such as "salt to
 * taste", appear once with no amount.
 */
object GroceryBuilder {
    private class Accumulator(val display: String, val original: String) {
        var plain = 0.0
        /** A size written with a plain count, such as "9-inch" in "1 9-inch pie crust". */
        var plainSize: String? = null
        var volumeTeaspoons = 0.0
        var weightOunces = 0.0
        val counts = linkedMapOf<Pair<Unit, String?>, Double>()
        var unmeasured = false
        val sources = linkedSetOf<String>()
    }

    fun build(
        recipes: List<GroceryRecipeInput>,
        manual: List<GroceryManualInput>,
        states: Map<String, GroceryLineStateInput>,
        sectionOverrides: Map<String, StoreSection>,
        hideStaples: Boolean,
        units: UnitSystem = UnitSystem.US,
    ): List<GrocerySectionGroup> {
        val byKey = linkedMapOf<String, Accumulator>()
        for (recipe in recipes) {
            for (line in recipe.lines) add(line, recipe, byKey)
        }

        val lines = mutableListOf<GroceryLine>()
        for ((nameKey, acc) in byKey) {
            if (hideStaples && IngredientNames.isStaple(nameKey)) continue
            val key = "r:$nameKey"
            val state = states[key]
            if (state?.hidden == true) continue
            lines += GroceryLine(
                key = key,
                text = state?.customText ?: describe(acc, units),
                sources = acc.sources.toList(),
                checked = state?.checked ?: false,
                section = sectionOverrides[nameKey] ?: StoreSection.guess(nameKey, acc.original),
                nameKey = nameKey,
            )
        }
        for (item in manual) {
            val nameKey = IngredientNames.clean(IngredientParser.parse(item.text).rest).key
            lines += GroceryLine(
                key = "m:${item.id}",
                text = item.text,
                sources = emptyList(),
                checked = item.checked,
                section = item.section ?: sectionOverrides[nameKey] ?: StoreSection.guess(nameKey, item.text),
                nameKey = nameKey,
                manualId = item.id,
            )
        }

        return lines.groupBy { it.section }
            .toSortedMap(compareBy { it.ordinal })
            .map { (section, sectionLines) ->
                GrocerySectionGroup(
                    section,
                    // Checked lines move to the end of their section.
                    sectionLines.sortedWith(compareBy<GroceryLine> { it.checked }.thenBy { it.text.lowercase() }),
                )
            }
    }

    private fun add(line: String, recipe: GroceryRecipeInput, byKey: MutableMap<String, Accumulator>) {
        val parsed = IngredientParser.parse(line)
        var rest = parsed.rest
        var unit = parsed.unit
        var size: String? = null
        // "1 (15 ounce) can black beans": the size is in parentheses before the unit.
        if (parsed.quantity != null && unit == null && rest.startsWith("(") && ")" in rest) {
            size = rest.substring(1, rest.indexOf(')')).trim()
            val (sizedUnit, _, after) = IngredientParser.matchUnit(rest.substring(rest.indexOf(')') + 1).trimStart())
            if (sizedUnit != null) {
                unit = sizedUnit
                rest = after
            } else {
                size = null
            }
        }
        var quantity = parsed.quantity?.high
        var plainSize: String? = null
        // "1 9-inch pie crust" and "2 15-ounce cans black beans": a size written before the name or container.
        if (quantity != null && unit == null) {
            sizePrefix.find(rest)?.let { match ->
                val after = rest.substring(match.range.last + 1).trimStart()
                val (container, _, afterContainer) = IngredientParser.matchUnit(after)
                if (container in containers) {
                    unit = container
                    size = sizeText(match)
                    rest = afterContainer
                } else {
                    plainSize = sizeText(match)
                    rest = after
                }
            }
        }
        // "12 ounce can evaporated milk": one can that holds 12 ounces.
        if (quantity != null && unit != null && unit.measure != Measure.COUNT) {
            val (container, _, after) = IngredientParser.matchUnit(rest)
            if (container in containers) {
                size = "${Fractions.format(quantity)} ${parsed.unitText.orEmpty().lowercase().trimEnd('.')}"
                unit = container
                quantity = 1.0
                rest = after
            }
        }
        val name = IngredientNames.clean(rest)
        if (name.key.isBlank()) return
        val acc = byKey.getOrPut(name.key) { Accumulator(name.display, line) }
        acc.sources += recipe.title
        val amount = quantity?.times(recipe.scale)
        when {
            amount == null -> acc.unmeasured = true
            unit == null -> {
                acc.plain += amount
                if (plainSize != null) acc.plainSize = acc.plainSize ?: plainSize
            }
            unit.measure == Measure.US_VOLUME -> acc.volumeTeaspoons += amount * unit.base
            unit.measure == Measure.METRIC_VOLUME ->
                acc.volumeTeaspoons += amount * unit.base * IngredientScaler.TEASPOONS_PER_ML
            unit.measure == Measure.US_WEIGHT -> acc.weightOunces += amount * unit.base
            unit.measure == Measure.METRIC_WEIGHT ->
                acc.weightOunces += amount * unit.base * IngredientScaler.OUNCES_PER_GRAM
            else -> acc.counts.merge(unit to size, amount, Double::plus)
        }
    }

    private fun describe(acc: Accumulator, units: UnitSystem): String {
        val parts = mutableListOf<String>()
        if (acc.plain > 0) parts += Fractions.format(acc.plain)
        for ((unitAndSize, amount) in acc.counts) {
            val (unit, size) = unitAndSize
            parts += "${Fractions.format(amount)} ${unit.word(amount)}" + (size?.let { " ($it)" } ?: "")
        }
        if (units == UnitSystem.METRIC) {
            if (acc.volumeTeaspoons > 0) parts += IngredientScaler.metricVolumeText(acc.volumeTeaspoons * IngredientScaler.KITCHEN_ML_PER_TEASPOON)
            if (acc.weightOunces > 0) parts += IngredientScaler.metricWeightText(acc.weightOunces / IngredientScaler.OUNCES_PER_GRAM)
        } else {
            if (acc.volumeTeaspoons > 0) parts += IngredientScaler.usVolumeText(acc.volumeTeaspoons)
            if (acc.weightOunces > 0) parts += IngredientScaler.usWeightText(acc.weightOunces)
        }
        if (parts.isEmpty()) return acc.display

        // A plain count names the thing counted: "1 onion", "3 onions".
        val name = if (parts.size == 1 && acc.plain > 0) countedName(acc.display, acc.plain) else acc.display
        val sizeNote = acc.plainSize?.takeIf { acc.plain > 0 }?.let { " ($it)" }.orEmpty()
        return parts.joinToString(" + ") + " " + name + sizeNote
    }

    /** Units that hold an amount: "can" in "12 ounce can evaporated milk". */
    private val containers = setOf(
        Unit.CAN, Unit.JAR, Unit.PACKAGE, Unit.PACKET, Unit.ENVELOPE,
        Unit.CONTAINER, Unit.BOTTLE, Unit.BAG, Unit.BOX,
    )

    private val sizePrefix = Regex(
        """^(\d+(?:\.\d+)?)\s*-?\s*(ounces?|oz|inch(?:es)?|pounds?|lbs?|quarts?|qt)\.?(?![a-z])""",
        RegexOption.IGNORE_CASE,
    )

    /** "9-inch" for pan and crust sizes; "15 ounce" for package sizes, matching "(15 ounce)" as written. */
    private fun sizeText(match: MatchResult): String {
        val (number, unit) = match.destructured
        return if (unit.lowercase().startsWith("inch")) "$number-inch" else "$number ${unit.lowercase()}"
    }

    private fun countedName(display: String, amount: Double): String {
        val start = display.lastIndexOf(' ') + 1
        val word = display.substring(start)
        val changed = if (Fractions.isPlural(amount)) Nouns.pluralize(word) else Nouns.singularize(word)
        return display.substring(0, start) + changed
    }

    /** The unchecked lines as plain text, grouped by section, for sharing in a message. */
    fun shareText(listName: String, groups: List<GrocerySectionGroup>): String = buildString {
        append(listName)
        for (group in groups) {
            val open = group.lines.filterNot { it.checked }
            if (open.isEmpty()) continue
            append("\n\n").append(group.section.label).append(':')
            open.forEach { append("\n- ").append(it.text) }
        }
    }
}
