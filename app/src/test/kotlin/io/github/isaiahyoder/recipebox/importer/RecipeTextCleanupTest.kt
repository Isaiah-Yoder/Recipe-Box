package io.github.isaiahyoder.recipebox.importer

import io.github.isaiahyoder.recipebox.data.RecipeLine
import org.junit.Assert.assertEquals
import org.junit.Test

class RecipeTextCleanupTest {
    private fun clean(text: String) = RecipeTextCleanup.ingredient(text)?.text

    @Test fun tidiesDoubledAndCommaParentheses() {
        assertEquals("3 cups vegetable stock (unsalted)", clean("3 cups vegetable stock ((unsalted))"))
        assertEquals("8 ounces cream cheese (softened)", clean("8 ounces cream cheese (, softened)"))
        assertEquals("8 ounces grated cheddar (2 cups)", clean("8 ounces grated cheddar ( (2 cups))"))
        assertEquals("½ Cup diced pepper (about 75 grams)", clean("½ Cup diced pepper ( about 75 grams)"))
    }

    @Test fun keepsNestedRemarksAndBalancesParentheses() {
        assertEquals("1 cup cream cheese (softened (about 70°F))", clean("1 cup cream cheese (softened (about 70°F))"))
        assertEquals("3 apples (about 2 cups, grated (300 g))", clean("3 apples (about 2 cups, grated (300 g)"))
        assertEquals("1/2 lb green beans (about 2 cups), trimmed", clean("1/2 lb green beans (about 2 cups), trimmed)"))
    }

    @Test fun movesALeadingRemarkAfterTheName() {
        assertEquals("Salt, to taste", clean(" to taste Salt"))
        assertEquals("Turmeric powder, to taste", clean("to taste Turmeric powder "))
    }

    @Test fun dropsAnAmountRepeatedAfterTheName() {
        assertEquals("4 bunches spinach leaves", clean("4 bunches spinach leaves - 4 bunches"))
        assertEquals("2 eggs - beaten", clean("2 eggs - beaten"))
    }

    @Test fun removesShoppingLinkRemarks() {
        assertEquals("1 cup rolled oats", clean("1 cup rolled oats (click to see my favorite brand)"))
    }

    @Test fun fixesPluralMarkersDashesAndDecimals() {
        assertEquals("1/4 teaspoon salt", clean("1/4 teaspoon(s) salt"))
        assertEquals("2 tablespoons butter", clean("2 tablespoon(s) butter"))
        assertEquals("1 whole chicken", clean("1 - whole chicken"))
        assertEquals("2 1/2 cups of rice", clean("2 1/2 - cups of rice"))
        assertEquals("½ cup olive oil", clean("0.5 cup olive oil"))
        assertEquals("¼ cup grated cheese", clean(".25 cup grated cheese"))
        assertEquals("1½ cups all-purpose flour", clean("1.5 cups all-purpose flour"))
        assertEquals("12.7 oz box pasta", clean("12.7 oz box pasta"))
    }

    @Test fun turnsLabelLinesIntoHeadings() {
        assertEquals(RecipeLine("For the Glaze", isHeader = true), RecipeTextCleanup.ingredient("For the Glaze:"))
    }

    @Test fun trimsTitleMarketing() {
        assertEquals("Flatbread", RecipeTextCleanup.title("Flatbread recipe – soft, warm, easy!"))
        assertEquals("The Best Lemon Bars", RecipeTextCleanup.title("The Best Lemon Bars Recipe {Really}"))
        assertEquals("Uncle's Smoked Brisket", RecipeTextCleanup.title("Uncle's Smoked Brisket: A Pitmaster's Secrets"))
        assertEquals("Homemade Pierogi (Potato)", RecipeTextCleanup.title("Homemade Pierogi recipe (Potato)"))
        assertEquals("20-Minute Tomato Soup", RecipeTextCleanup.title("20-Minute Tomato Soup"))
        assertEquals("Recipe", RecipeTextCleanup.title("Recipe"))
    }

    @Test fun tidiesSiteNamesAndYields() {
        assertEquals("Example Kitchen", RecipeTextCleanup.siteName("Example Kitchen ®"))
        assertEquals("4 servings", RecipeTextCleanup.yieldText("4 serving(s)"))
    }
}
