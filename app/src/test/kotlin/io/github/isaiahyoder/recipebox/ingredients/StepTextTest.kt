package io.github.isaiahyoder.recipebox.ingredients

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StepTextTest {
    private fun shown(text: String, factor: Double = 1.0, us: Boolean = false) =
        StepText.display(text, factor, us).joinToString("") { it.text }

    @Test fun scalesAmountsWithUnits() {
        assertEquals("Add 4 cups of flour and stir for 2 minutes.", shown("Add 2 cups of flour and stir for 2 minutes.", 2.0))
        assertEquals("Add 1 to 2 tablespoons of milk.", shown("Add 1 to 2 tablespoons of milk.", 1.0))
        assertEquals("Add 2 to 4 tablespoons of milk.", shown("Add 1 to 2 tablespoons of milk.", 2.0))
    }

    @Test fun leavesTimesTemperaturesAndBareNumbersAlone() {
        assertEquals("Bake at 350 degrees F for 25 minutes. Add 2 eggs.",
            shown("Bake at 350 degrees F for 25 minutes. Add 2 eggs.", 2.0))
    }

    @Test fun marksOnlyTheChangedPieces() {
        val pieces = StepText.display("Whisk in 1 cup milk.", 2.0, false)
        assertEquals(listOf(false, true, false), pieces.map { it.changed })
        assertEquals("2 cups", pieces[1].text)
    }

    @Test fun convertsOvenTemperaturesAndPanSizes() {
        assertEquals("Heat the oven to 350°F.", shown("Heat the oven to 180 °C.", us = true))
        assertEquals("Heat the oven to 400°F.", shown("Heat the oven to 200 degrees Celsius.", us = true))
        assertEquals("Grease a 9 x 13 in pan.", shown("Grease a 23 x 33 cm pan.", us = true))
        assertEquals("Use a 9 in tin.", shown("Use a 23cm tin.", us = true))
        assertEquals("Stir in 1 cup stock.", shown("Stir in 250 ml stock.", us = true))
    }

    @Test fun leavesMetricAloneWhenTheToggleIsOff() {
        assertEquals("Heat the oven to 180 °C.", shown("Heat the oven to 180 °C."))
    }

    @Test fun roundsToOvenSettings() {
        assertEquals(325, StepText.fahrenheit(160))
        assertEquals(425, StepText.fahrenheit(220))
        assertEquals(165, StepText.fahrenheit(74))
    }

    @Test fun showsGramsOfBakingIngredientsAsApproximateCups() {
        assertEquals("about 2 cups all-purpose flour", IngredientScaler.display("250 g all-purpose flour", 1.0, true).text)
        assertEquals("about ½ cup butter", IngredientScaler.display("113 g butter", 1.0, true).text)
        assertTrue(IngredientScaler.display("200 g chicken thighs", 1.0, true).text.startsWith("7 ounces"))
    }
}
