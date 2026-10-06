package io.github.isaiahyoder.recipebox.ingredients

import io.github.isaiahyoder.recipebox.model.RecipeLine

/**
 * One ingredient line of a recipe, read so features can ask questions across
 * recipes, such as which recipes use chicken, what a meal plan needs, or what
 * she can make from her pantry.
 */
data class IndexedIngredient(
    /** The line's place in the recipe's ingredient list, headings included. */
    val position: Int,
    /** The heading the line is under, such as "For the sauce", or null. */
    val group: String?,
    val reading: IngredientReading,
)

/** Reads a recipe's ingredient lines for the ingredient index. Headings and nameless lines are left out. */
object IngredientIndex {
    fun read(lines: List<RecipeLine>): List<IndexedIngredient> {
        var group: String? = null
        return lines.mapIndexedNotNull { position, line ->
            if (line.isHeader) {
                group = line.text
                return@mapIndexedNotNull null
            }
            val reading = IngredientReader.read(line.text)
            if (reading.name.key.isBlank()) null else IndexedIngredient(position, group, reading)
        }
    }
}
