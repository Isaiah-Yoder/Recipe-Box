package io.github.isaiahyoder.recipebox.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RecipeTextTest {
    @Test fun readsOneItemPerLineWithHeadings() {
        val lines = RecipeText.fromText(
            """
            For the dough:
            2 cups flour
              • 1 egg

            For the sauce:
            - 1 cup tomatoes
            """.trimIndent()
        )
        assertEquals(
            listOf(
                RecipeLine("For the dough", isHeader = true),
                RecipeLine("2 cups flour"),
                RecipeLine("1 egg"),
                RecipeLine("For the sauce", isHeader = true),
                RecipeLine("1 cup tomatoes"),
            ),
            lines,
        )
    }

    @Test fun keepsLongLinesEndingInAColonAsSteps() {
        val step = "Whisk the following together until the mixture is smooth and pale, about 3 minutes:"
        assertEquals(listOf(RecipeLine(step)), RecipeText.fromText(step))
    }

    @Test fun roundTrips() {
        val lines = listOf(RecipeLine("Crust", isHeader = true), RecipeLine("Roll the dough."))
        assertEquals(lines, RecipeText.fromText(RecipeText.toText(lines)))
    }
}
