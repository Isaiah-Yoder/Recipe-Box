package io.github.isaiahyoder.recipebox.ingredients

enum class Measure { US_VOLUME, METRIC_VOLUME, US_WEIGHT, METRIC_WEIGHT, COUNT }

/**
 * A unit the parser recognizes.
 *
 * [base] is the unit's size in its measure's base unit: teaspoons for US
 * volume, milliliters for metric volume, ounces for US weight, and grams for
 * metric weight. Count units, such as cloves, have no base and never convert.
 */
enum class Unit(
    val measure: Measure,
    val base: Double,
    val singular: String,
    val plural: String,
    val aliases: List<String>,
) {
    TEASPOON(Measure.US_VOLUME, 1.0, "teaspoon", "teaspoons", listOf("teaspoons", "teaspoon", "tsps", "tsp", "t")),
    TABLESPOON(Measure.US_VOLUME, 3.0, "tablespoon", "tablespoons", listOf("tablespoons", "tablespoon", "tbsps", "tbsp", "tbs", "tbl", "T")),
    FLUID_OUNCE(Measure.US_VOLUME, 6.0, "fluid ounce", "fluid ounces", listOf("fluid ounces", "fluid ounce", "fl. oz", "fl oz", "fl.oz")),
    CUP(Measure.US_VOLUME, 48.0, "cup", "cups", listOf("cups", "cup", "c")),
    PINT(Measure.US_VOLUME, 96.0, "pint", "pints", listOf("pints", "pint", "pt")),
    QUART(Measure.US_VOLUME, 192.0, "quart", "quarts", listOf("quarts", "quart", "qt")),
    GALLON(Measure.US_VOLUME, 768.0, "gallon", "gallons", listOf("gallons", "gallon", "gal")),
    MILLILITER(Measure.METRIC_VOLUME, 1.0, "milliliter", "milliliters", listOf("milliliters", "millilitres", "milliliter", "millilitre", "ml", "mL")),
    CENTILITER(Measure.METRIC_VOLUME, 10.0, "centiliter", "centiliters", listOf("centiliters", "centilitres", "cl")),
    DECILITER(Measure.METRIC_VOLUME, 100.0, "deciliter", "deciliters", listOf("deciliters", "decilitres", "dl")),
    LITER(Measure.METRIC_VOLUME, 1000.0, "liter", "liters", listOf("liters", "litres", "liter", "litre", "l", "L")),
    OUNCE(Measure.US_WEIGHT, 1.0, "ounce", "ounces", listOf("ounces", "ounce", "oz")),
    POUND(Measure.US_WEIGHT, 16.0, "pound", "pounds", listOf("pounds", "pound", "lbs", "lb")),
    GRAM(Measure.METRIC_WEIGHT, 1.0, "gram", "grams", listOf("grams", "gram", "grammes", "gramme", "g", "gr")),
    KILOGRAM(Measure.METRIC_WEIGHT, 1000.0, "kilogram", "kilograms", listOf("kilograms", "kilogram", "kilos", "kilo", "kg")),
    PINCH(Measure.COUNT, 0.0, "pinch", "pinches", listOf("pinches", "pinch")),
    DASH(Measure.COUNT, 0.0, "dash", "dashes", listOf("dashes", "dash")),
    CLOVE(Measure.COUNT, 0.0, "clove", "cloves", listOf("cloves", "clove")),
    CAN(Measure.COUNT, 0.0, "can", "cans", listOf("cans", "can")),
    JAR(Measure.COUNT, 0.0, "jar", "jars", listOf("jars", "jar")),
    PACKAGE(Measure.COUNT, 0.0, "package", "packages", listOf("packages", "package", "pkgs", "pkg")),
    PACKET(Measure.COUNT, 0.0, "packet", "packets", listOf("packets", "packet")),
    ENVELOPE(Measure.COUNT, 0.0, "envelope", "envelopes", listOf("envelopes", "envelope")),
    STICK(Measure.COUNT, 0.0, "stick", "sticks", listOf("sticks", "stick")),
    SLICE(Measure.COUNT, 0.0, "slice", "slices", listOf("slices", "slice")),
    SPRIG(Measure.COUNT, 0.0, "sprig", "sprigs", listOf("sprigs", "sprig")),
    BUNCH(Measure.COUNT, 0.0, "bunch", "bunches", listOf("bunches", "bunch")),
    HEAD(Measure.COUNT, 0.0, "head", "heads", listOf("heads", "head")),
    STALK(Measure.COUNT, 0.0, "stalk", "stalks", listOf("stalks", "stalk")),
    PIECE(Measure.COUNT, 0.0, "piece", "pieces", listOf("pieces", "piece")),
    CONTAINER(Measure.COUNT, 0.0, "container", "containers", listOf("containers", "container")),
    BOTTLE(Measure.COUNT, 0.0, "bottle", "bottles", listOf("bottles", "bottle")),
    BAG(Measure.COUNT, 0.0, "bag", "bags", listOf("bags", "bag")),
    BOX(Measure.COUNT, 0.0, "box", "boxes", listOf("boxes", "box")),
    DROP(Measure.COUNT, 0.0, "drop", "drops", listOf("drops", "drop")),
    ;

    val isMetric: Boolean get() = measure == Measure.METRIC_VOLUME || measure == Measure.METRIC_WEIGHT

    /** Singular or plural for the amount as displayed, so 1.06 cups shows as "1 cup". */
    fun word(quantity: Double): String = if (Fractions.isPlural(quantity)) plural else singular

    companion object {
        /** Aliases matched exactly as written, so "T" is a tablespoon and "t" a teaspoon. */
        private val caseSensitive = setOf("T", "t", "L", "l", "c", "g", "mL")

        /** Every alias with its unit, longest first so "fl oz" wins over "oz". */
        val aliasTable: List<Pair<String, Unit>> =
            entries.flatMap { unit -> unit.aliases.map { it to unit } }
                .sortedByDescending { it.first.length }

        fun aliasMatches(alias: String, candidate: String): Boolean =
            if (alias in caseSensitive) alias == candidate else alias.equals(candidate, ignoreCase = true)
    }
}
