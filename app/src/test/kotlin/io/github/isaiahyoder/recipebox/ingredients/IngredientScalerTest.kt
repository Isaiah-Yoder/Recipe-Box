package io.github.isaiahyoder.recipebox.ingredients

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FractionsTest {
    @Test fun formatsKitchenFractions() {
        assertEquals("½", Fractions.format(0.5))
        assertEquals("1½", Fractions.format(1.5))
        assertEquals("⅓", Fractions.format(0.3333))
        assertEquals("⅔", Fractions.format(0.6667))
        assertEquals("2", Fractions.format(1.98))
        assertEquals("1⅛", Fractions.format(1.1))
        assertEquals("12½", Fractions.format(12.4))
        assertEquals("⅛", Fractions.format(0.02))
    }

    @Test fun normalizesUnicodeFractions() {
        assertEquals("1 1/2 cups", Fractions.normalize("1½ cups"))
        assertEquals("1/2 cup", Fractions.normalize("½ cup"))
        assertEquals("1 1/4", Fractions.normalize("1 ¼"))
    }
}

class IngredientParserTest {
    private fun parse(line: String) = IngredientParser.parse(line)

    @Test fun parsesWholeNumbersAndUnits() {
        val p = parse("2 cups all-purpose flour")
        assertEquals(Quantity(2.0), p.quantity)
        assertEquals(Unit.CUP, p.unit)
        assertEquals("all-purpose flour", p.rest)
    }

    @Test fun parsesDecimalsAsAllrecipesWritesThem() {
        val p = parse("1.5 pounds ground beef")
        assertEquals(1.5, p.quantity!!.low, 1e-9)
        assertEquals(Unit.POUND, p.unit)
        assertEquals("ground beef", p.rest)
    }

    @Test fun parsesMixedAndUnicodeFractions() {
        assertEquals(1.5, parse("1 1/2 teaspoons salt").quantity!!.low, 1e-9)
        assertEquals(1.5, parse("1½ teaspoons salt").quantity!!.low, 1e-9)
        assertEquals(0.75, parse("¾ cup sugar").quantity!!.low, 1e-9)
    }

    @Test fun parsesRanges() {
        val p = parse("2 to 3 tablespoons olive oil")
        assertEquals(Quantity(2.0, 3.0), p.quantity)
        assertEquals(Unit.TABLESPOON, p.unit)
        assertEquals(Quantity(1.0, 2.0), parse("1-2 cloves garlic").quantity)
    }

    @Test fun distinguishesTablespoonFromTeaspoonAbbreviations() {
        assertEquals(Unit.TABLESPOON, parse("1 T butter").unit)
        assertEquals(Unit.TEASPOON, parse("1 t vanilla").unit)
        assertEquals(Unit.TABLESPOON, parse("1 Tbsp. butter").unit)
    }

    @Test fun requiresUnitsToEndAtAWordBoundary() {
        val carrots = parse("3 carrots, sliced")
        assertNull(carrots.unit)
        assertEquals("carrots, sliced", carrots.rest)
        assertNull(parse("2 large eggs").unit)
        assertNull(parse("1 garlic bulb").unit)
    }

    @Test fun parsesMetricUnitsWithoutASpace() {
        val p = parse("500g pasta")
        assertEquals(500.0, p.quantity!!.low, 1e-9)
        assertEquals(Unit.GRAM, p.unit)
        assertEquals("pasta", p.rest)
    }

    @Test fun leavesPackageSizesInTheText() {
        val p = parse("1 (15 ounce) can black beans")
        assertEquals(Quantity(1.0), p.quantity)
        assertNull(p.unit)
        assertEquals("(15 ounce) can black beans", p.rest)
    }

    @Test fun treatsLinesWithoutAnAmountAsUnscalable() {
        val p = parse("ground black pepper to taste")
        assertNull(p.quantity)
        assertEquals("ground black pepper to taste", p.rest)
    }
}

class IngredientScalerTest {
    private fun show(line: String, factor: Double, us: Boolean = false) =
        IngredientScaler.display(line, factor, us)

    @Test fun showsTheLineAsWrittenWithKitchenFractionsAtOneTimes() {
        val d = show("1.5 pounds ground beef", 1.0)
        assertEquals("1½ pounds ground beef", d.text)
        assertFalse(d.changed)
        assertEquals("1½ cups flour", show("1 1/2 cups flour", 1.0).text)
        assertEquals("1 to 2 Tablespoons milk", show("1-2 Tablespoons milk", 1.0).text)
        // 0.6 isn't a kitchen fraction, so the line keeps its own wording.
        assertEquals("0.6 cups stock", show("0.6 cups stock", 1.0).text)
    }

    @Test fun usesSpoonsPeopleOwn() {
        assertEquals("4 teaspoons kosher salt", show("2 teaspoons kosher salt", 2.0).text)
        assertEquals("2 to 4 tablespoons milk", show("1-2 Tablespoons milk", 2.0).text)
        assertEquals("1⅛ cups sugar", show("¾ cup sugar", 1.5).text)
        assertEquals("3 tablespoons oil", show("1½ tablespoons oil", 2.0).text)
    }

    @Test fun makesCountedIngredientsAgree() {
        assertEquals("2 medium onions, finely chopped", show("1 medium onion, finely chopped", 2.0).text)
        assertEquals("1 egg, beaten", show("2 eggs, beaten", 0.5).text)
        assertEquals("2 bay leaves", show("1 bay leaf", 2.0).text)
        assertEquals("3 tomatoes", show("1 tomato", 3.0).text)
        assertEquals("2 (15 ounce) cans black beans", show("1 (15 ounce) cans black beans", 2.0).text)
    }

    @Test fun doublesAndHalves() {
        assertEquals("1 cup sugar", show("½ cup sugar", 2.0).text)
        assertEquals("¼ cup sugar", show("½ cup sugar", 0.5).text)
        assertEquals("3 large eggs", show("2 large eggs", 1.5).text)
        assertEquals("3 pounds ground beef", show("1.5 pounds ground beef", 2.0).text)
    }

    @Test fun movesBetweenSpoonsAndCups() {
        assertEquals("1½ teaspoons baking soda", show("1 tablespoon baking soda", 0.5).text)
        assertEquals("2 tablespoons butter", show("¼ cup butter", 0.5).text)
        assertEquals("½ cup milk", show("4 tablespoons milk", 2.0).text)
    }

    @Test fun movesBetweenOuncesAndPounds() {
        assertEquals("1 pound cream cheese", show("8 ounces cream cheese", 2.0).text)
        assertEquals("4 ounces bacon", show("½ pound bacon", 0.5).text)
    }

    @Test fun keepsAbbreviationsAndCountUnits() {
        assertEquals("4 cloves garlic, minced", show("2 cloves garlic, minced", 2.0).text)
        assertEquals("1 clove garlic", show("2 cloves garlic", 0.5).text)
        assertEquals("2 to 4 cloves garlic", show("1-2 cloves garlic", 2.0).text)
    }

    @Test fun marksLinesWithoutAnAmountWhenScaled() {
        val d = show("salt to taste", 2.0)
        assertEquals("salt to taste", d.text)
        assertTrue(d.unscaled)
        assertFalse(show("salt to taste", 1.0).unscaled)
    }

    @Test fun convertsMetricToUsUnits() {
        val milk = show("250 ml milk", 1.0, us = true)
        assertEquals("1 cup milk", milk.text)
        assertTrue(milk.converted)
        assertEquals("1 tablespoon oil", show("15 ml oil", 1.0, us = true).text)
        assertEquals("7 ounces pasta", show("200g pasta", 1.0, us = true).text)
        assertEquals("1 pound potatoes", show("454 g potatoes", 1.0, us = true).text)
        assertEquals("2¼ pounds chicken", show("1 kg chicken", 1.0, us = true).text)
    }

    @Test fun roundsConvertedVolumesToMeasurableAmounts() {
        assertEquals("3½ tablespoons water", show("50 ml water", 1.0, us = true).text)
        assertEquals("1½ teaspoons vanilla", show("7 ml vanilla", 1.0, us = true).text)
        assertEquals("1⅔ cups stock", show("400 ml stock", 1.0, us = true).text)
    }

    @Test fun leavesMetricAloneWhenTheToggleIsOff() {
        assertEquals("250 ml milk", show("250 ml milk", 1.0).text)
        assertEquals("500 ml milk", show("250 ml milk", 2.0).text)
    }
}
