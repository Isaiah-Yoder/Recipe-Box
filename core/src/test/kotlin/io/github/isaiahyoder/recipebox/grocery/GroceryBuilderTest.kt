package io.github.isaiahyoder.recipebox.grocery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroceryBuilderTest {
    private fun build(
        vararg recipes: GroceryRecipeInput,
        manual: List<GroceryManualInput> = emptyList(),
        states: Map<String, GroceryLineStateInput> = emptyMap(),
        overrides: Map<String, StoreSection> = emptyMap(),
        hideStaples: Boolean = false,
    ) = GroceryBuilder.build(recipes.toList(), manual, states, overrides, hideStaples)

    private fun lines(groups: List<GrocerySectionGroup>) = groups.flatMap { it.lines }
    private fun texts(groups: List<GrocerySectionGroup>) = lines(groups).map { it.text }.toSet()

    @Test fun addsTheSameIngredientAcrossRecipesAndScales() {
        val groups = build(
            GroceryRecipeInput("Pancakes", 2.0, listOf("½ cup milk", "1 egg")),
            GroceryRecipeInput("Soup", 1.0, listOf("1 cup milk", "2 eggs, beaten", "4 hard boiled eggs, chopped")),
        )
        val all = texts(groups)
        assertTrue(all.toString(), "2 cups milk" in all)
        assertTrue(all.toString(), "8 eggs" in all)
        val milk = lines(groups).single { it.text == "2 cups milk" }
        assertEquals(listOf("Pancakes", "Soup"), milk.sources)
        assertEquals(StoreSection.DAIRY_EGGS, milk.section)
    }

    @Test fun keepsDifferentKindsOfAmountsOnOneLine() {
        val all = texts(build(GroceryRecipeInput("A", 1.0, listOf("2 cloves garlic, minced", "1 teaspoon garlic"))))
        assertTrue(all.toString(), "2 cloves + 1 teaspoon garlic" in all)
    }

    @Test fun mergesMetricAndUsVolumes() {
        val all = texts(build(GroceryRecipeInput("A", 1.0, listOf("120 ml cream", "½ cup cream"))))
        assertTrue(all.toString(), "1 cup cream" in all)
    }

    @Test fun keepsCanSizes() {
        val all = texts(
            build(
                GroceryRecipeInput("Chili", 2.0, listOf("1 (15 ounce) can black beans, drained")),
                GroceryRecipeInput("Tacos", 1.0, listOf("1 (15 ounce) can black beans")),
            )
        )
        assertTrue(all.toString(), "3 cans (15 ounce) black beans" in all)
    }

    @Test fun readsSizesWrittenBeforeTheContainer() {
        val all = texts(
            build(
                GroceryRecipeInput("Pie", 2.0, listOf("12 ounce can evaporated milk")),
                GroceryRecipeInput("Chili", 1.0, listOf("2 15-ounce cans black beans", "1 (15 ounce) can black beans")),
            )
        )
        assertTrue(all.toString(), "2 cans (12 ounce) evaporated milk" in all)
        assertTrue(all.toString(), "3 cans (15 ounce) black beans" in all)
    }

    @Test fun keepsPanSizesWithTheName() {
        val all = texts(build(GroceryRecipeInput("Pie", 1.0, listOf("1 9-inch pie crust (or store-bought, unbaked)"))))
        assertTrue(all.toString(), "1 pie crust (9-inch)" in all)
    }

    @Test fun listsUnmeasuredIngredientsOnce() {
        val groups = build(
            GroceryRecipeInput("A", 1.0, listOf("salt to taste")),
            GroceryRecipeInput("B", 1.0, listOf("salt to taste")),
        )
        assertEquals(listOf("salt"), lines(groups).map { it.text })
        assertEquals(StoreSection.SPICES, lines(groups).single().section)
    }

    @Test fun keepsAlternativesAndMergesPlainPepper() {
        val all = texts(
            build(
                GroceryRecipeInput("A", 1.0, listOf("1-2 Tablespoons milk or broth", "½ teaspoon pepper, plus more to taste")),
                GroceryRecipeInput("B", 1.0, listOf("⅛ teaspoon ground black pepper")),
            )
        )
        assertTrue(all.toString(), "2 tablespoons milk or broth" in all)
        assertTrue(all.toString(), "⅝ teaspoon black pepper" in all)
    }

    @Test fun mergesLooseAmountsOfTheSameHerb() {
        val all = texts(
            build(
                GroceryRecipeInput(
                    "Tacos", 1.0,
                    listOf("small handful fresh cilantro (chopped)", "chopped fresh cilantro", "dollop of sour cream", "8 oz sour cream"),
                )
            )
        )
        assertTrue(all.toString(), "cilantro" in all)
        assertEquals(all.toString(), 2, all.size)
    }

    @Test fun hidesStaplesWhenAsked() {
        val recipe = GroceryRecipeInput("A", 1.0, listOf("1 teaspoon kosher salt", "ground black pepper to taste", "2 onions"))
        assertEquals(setOf("2 onions"), texts(build(recipe, hideStaples = true)))
        assertEquals(3, lines(build(recipe)).size)
    }

    @Test fun appliesCheckedHiddenAndRewrittenLines() {
        val recipe = GroceryRecipeInput("A", 1.0, listOf("1 onion", "2 carrots", "1 cup rice"))
        val groups = build(
            recipe,
            states = mapOf(
                "r:onion" to GroceryLineStateInput(checked = true, hidden = false, customText = null),
                "r:carrot" to GroceryLineStateInput(checked = false, hidden = true, customText = null),
                "r:rice" to GroceryLineStateInput(checked = false, hidden = false, customText = "1 bag jasmine rice"),
            ),
        )
        val all = lines(groups)
        assertTrue(all.single { it.key == "r:onion" }.checked)
        assertFalse(all.any { it.key == "r:carrot" })
        assertEquals("1 bag jasmine rice", all.single { it.key == "r:rice" }.text)
    }

    @Test fun putsCheckedLinesLastInTheirSection() {
        val groups = build(
            GroceryRecipeInput("A", 1.0, listOf("1 apple", "1 banana", "1 carrot")),
            states = mapOf("r:apple" to GroceryLineStateInput(checked = true, hidden = false, customText = null)),
        )
        assertEquals(listOf("1 banana", "1 carrot", "1 apple"), groups.single().lines.map { it.text })
    }

    @Test fun includesTypedItemsAndHerSectionChoices() {
        val groups = build(
            GroceryRecipeInput("A", 1.0, listOf("1 cup sugar")),
            manual = listOf(GroceryManualInput(5, "paper towels", null, checked = false)),
            overrides = mapOf("sugar" to StoreSection.OTHER),
        )
        val all = lines(groups)
        assertEquals(StoreSection.OTHER, all.single { it.nameKey == "sugar" }.section)
        assertTrue(all.single { it.key == "m:5" }.isManual)
    }

    @Test fun guessesCommonSections() {
        assertEquals(StoreSection.PANTRY, StoreSection.guess("chicken broth"))
        assertEquals(StoreSection.MEAT_SEAFOOD, StoreSection.guess("rotisserie chicken"))
        assertEquals(StoreSection.PRODUCE, StoreSection.guess("red bell pepper"))
        assertEquals(StoreSection.SPICES, StoreSection.guess("black pepper"))
        assertEquals(StoreSection.SPICES, StoreSection.guess("ground ginger"))
        assertEquals(StoreSection.SPICES, StoreSection.guess("ground clove"))
        assertEquals(StoreSection.PRODUCE, StoreSection.guess("ginger"))
        assertEquals(StoreSection.PRODUCE, StoreSection.guess("garlic clove"))
        assertEquals(StoreSection.FROZEN, StoreSection.guess("corn", "3 cups frozen corn"))
        assertEquals(StoreSection.BAKERY, StoreSection.guess("hamburger bun"))
    }

    @Test fun sharesUncheckedLinesBySection() {
        val groups = build(
            GroceryRecipeInput("A", 1.0, listOf("1 onion", "1 cup milk")),
            states = mapOf("r:milk" to GroceryLineStateInput(checked = true, hidden = false, customText = null)),
        )
        val labels = mapOf(StoreSection.PRODUCE to "Produce", StoreSection.DAIRY_EGGS to "Dairy and eggs")
        assertEquals("Weekend\n\nProduce:\n- 1 onion", GroceryBuilder.shareText("Weekend", groups) { labels.getValue(it) })
    }
}
