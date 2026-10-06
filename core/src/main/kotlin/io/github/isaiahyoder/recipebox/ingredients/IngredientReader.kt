package io.github.isaiahyoder.recipebox.ingredients

/**
 * What an ingredient line asks for: how much of what. Grocery lists and the
 * saved ingredient index read lines the same way through [IngredientReader].
 */
data class IngredientReading(
    /** The line as written. */
    val line: String,
    /** The ingredient's name, as written and as a key that matches across recipes. */
    val name: IngredientNames.Name,
    /** The amount, or null for a line without one, such as "salt to taste". */
    val quantity: Quantity?,
    val unit: Unit?,
    /** A container's size, such as "15 ounce" in "1 (15 ounce) can black beans". */
    val size: String? = null,
    /** A size written with a plain count, such as "9-inch" in "1 9-inch pie crust". */
    val plainSize: String? = null,
) {
    /**
     * The larger amount in its measure's base unit, such as teaspoons for US
     * volume; see [Unit.base]. Null for counts and lines without an amount.
     */
    val baseAmount: Double?
        get() = quantity?.high?.let { amount -> unit?.takeIf { it.measure != Measure.COUNT }?.let { amount * it.base } }
}

object IngredientReader {
    fun read(line: String): IngredientReading {
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
        var quantity = parsed.quantity
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
                size = "${Fractions.format(quantity.high)} ${parsed.unitText.orEmpty().lowercase().trimEnd('.')}"
                unit = container
                quantity = Quantity(1.0)
                rest = after
            }
        }
        return IngredientReading(line, IngredientNames.clean(rest), quantity, unit, size, plainSize)
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
}
