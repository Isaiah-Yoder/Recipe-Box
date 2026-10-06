package io.github.isaiahyoder.recipebox.ingredients

import io.github.isaiahyoder.recipebox.grocery.GroceryBuilder
import io.github.isaiahyoder.recipebox.grocery.GroceryRecipeInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetricConversionTest {
    private fun metric(line: String, factor: Double = 1.0) = IngredientScaler.display(line, factor, UnitSystem.METRIC).text
    private fun steps(text: String) = StepText.display(text, 1.0, UnitSystem.METRIC).joinToString("") { it.text }

    @Test fun convertsUsVolumesToMilliliters() {
        assertEquals("240 ml milk", metric("1 cup milk"))
        assertEquals("15 ml vanilla extract", metric("1 tablespoon vanilla extract"))
        assertEquals("2.5 ml salt", metric("½ teaspoon salt"))
        assertEquals("1.25 ml ground cloves", metric("¼ teaspoon ground cloves"))
        assertEquals("0.5 ml cayenne", metric("⅛ teaspoon cayenne"))
        assertEquals("1.9 L chicken broth", metric("2 quarts chicken broth"))
        assertEquals("480 ml water", metric("1 cup water", factor = 2.0))
    }

    @Test fun weighsDryIngredientsWithKnownDensities() {
        assertEquals("about 250 g all-purpose flour", metric("2 cups all-purpose flour"))
        assertEquals("about 100 g sugar", metric("½ cup sugar"))
        // Liquids stay in milliliters even when their density is known.
        assertEquals("120 ml heavy cream", metric("½ cup heavy cream"))
    }

    @Test fun convertsUsWeightsToGrams() {
        assertEquals("225 g cream cheese", metric("8 ounces cream cheese"))
        assertEquals("910 g ground beef", metric("2 pounds ground beef"))
        assertEquals("1.4 kg pork shoulder", metric("3 pounds pork shoulder"))
    }

    @Test fun leavesMetricAndCountedAmountsAlone() {
        assertEquals("200 g sugar", metric("200 g sugar"))
        assertEquals("2 cloves garlic", metric("2 cloves garlic"))
    }

    @Test fun convertsStepTemperaturesAndPans() {
        assertEquals("Bake at 175°C for 30 minutes.", steps("Bake at 350°F for 30 minutes."))
        assertEquals("Preheat to 220°C.", steps("Preheat to 425 degrees F."))
        assertEquals("Grease a 23 x 33 cm baking dish.", steps("Grease a 9x13-inch baking dish."))
        assertEquals("Grease a 23 x 33 cm baking dish.", steps("Grease a 9 x 13 baking dish."))
        assertEquals("Cut into 3 cm pieces.", steps("Cut into 1-inch pieces."))
        assertEquals("Whisk in 240 ml milk.", steps("Whisk in 1 cup milk."))
        assertEquals("Bake 2 x 3 hours.", steps("Bake 2 x 3 hours."))
    }

    @Test fun groceryListsCanUseMetric() {
        val groups = GroceryBuilder.build(
            listOf(GroceryRecipeInput("A", 1.0, listOf("1 cup milk", "120 ml milk", "1 pound ground beef"))),
            emptyList(), emptyMap(), emptyMap(), hideStaples = false, units = UnitSystem.METRIC,
        )
        val texts = groups.flatMap { it.lines }.map { it.text }.toSet()
        assertTrue(texts.toString(), "360 ml milk" in texts)
        assertTrue(texts.toString(), "450 g ground beef" in texts)
    }
}
