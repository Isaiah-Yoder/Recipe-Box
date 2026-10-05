package io.github.isaiahyoder.recipebox.ingredients

/**
 * Grams per US cup for ingredients that US recipes measure by volume.
 * Conversions with these are approximate, because a cup of flour weighs
 * more or less depending on how it's scooped.
 */
object Densities {
    private val gramsPerCup = listOf(
        "whole wheat flour" to 120.0,
        "bread flour" to 130.0,
        "cake flour" to 115.0,
        "flour" to 125.0,
        "brown sugar" to 213.0,
        "powdered sugar" to 120.0,
        "icing sugar" to 120.0,
        "confectioners" to 120.0,
        "sugar" to 200.0,
        "butter" to 227.0,
        "rolled oats" to 90.0,
        "oats" to 90.0,
        "cocoa" to 85.0,
        "cornstarch" to 128.0,
        "chocolate chips" to 170.0,
        "honey" to 340.0,
        "rice" to 185.0,
        "milk" to 245.0,
        "water" to 240.0,
        "cream" to 240.0,
        "oil" to 218.0,
        "parmesan" to 100.0,
        "shredded cheese" to 113.0,
    )

    /** Grams per cup for the ingredient named in [text], or null when it isn't in the table. */
    fun gramsPerCup(text: String): Double? {
        val lower = text.lowercase()
        return gramsPerCup.firstOrNull { (name, _) -> Regex("""\b${Regex.escape(name)}""").containsMatchIn(lower) }?.second
    }
}
