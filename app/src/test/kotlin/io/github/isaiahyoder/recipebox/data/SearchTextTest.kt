package io.github.isaiahyoder.recipebox.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchTextTest {
    @Test fun escapesLikeWildcards() {
        assertEquals("""50\% off""", escapeLike("50% off"))
        assertEquals("""a\_b""", escapeLike("a_b"))
        assertEquals("""c:\\d""", escapeLike("""c:\d"""))
        assertEquals("flour", escapeLike("flour"))
    }

    @Test fun indexesIngredientLinesWithoutHeadings() {
        val recipe = RecipeEntity(
            title = "Bread",
            ingredients = listOf(RecipeLine("For the dough", isHeader = true), RecipeLine("3 cups flour"), RecipeLine("1 egg")),
            createdAt = 0,
            updatedAt = 0,
        )
        assertEquals(listOf("3 cups flour", "1 egg"), recipe.indexed().ingredientText.lines())
    }
}
