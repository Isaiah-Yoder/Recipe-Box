package io.github.isaiahyoder.recipebox.importer

import io.github.isaiahyoder.recipebox.data.EditedField
import io.github.isaiahyoder.recipebox.data.RecipeEntity
import io.github.isaiahyoder.recipebox.model.RecipeLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeRefresherTest {
    private val saved = RecipeEntity(
        id = 7,
        title = "Chili Recipe",
        sourceUrl = "https://example.com/chili",
        imageUrl = "https://example.com/old.jpg",
        imageFile = "cover-7.jpg",
        ingredients = listOf(RecipeLine("2 cups broth ((low sodium))")),
        steps = listOf(RecipeLine("to 375° F.")),
        notes = "Add extra garlic",
        favorite = true,
        lastScale = 2.0,
        showUsUnits = false,
        cardPhotos = listOf("card-1.jpg"),
        createdAt = 100,
        updatedAt = 100,
    )

    private val page = ExtractedRecipe(
        title = "Chili",
        imageUrl = "https://example.com/new.jpg",
        totalMinutes = 485,
        ingredients = listOf(RecipeLine("2 cups broth (low sodium)"), RecipeLine("Toppings", isHeader = true), RecipeLine("avocado")),
        steps = listOf(RecipeLine("Heat oven to 375° F.")),
        categories = listOf("Soup"),
    )

    @Test fun replacesPageContentAndKeepsHerChoices() {
        val refreshed = saved.refreshedWith(page)
        assertEquals("Chili", refreshed.title)
        assertEquals(page.ingredients, refreshed.ingredients)
        assertEquals(page.steps, refreshed.steps)
        assertEquals(485, refreshed.totalMinutes)
        assertEquals(listOf("Soup"), refreshed.siteCategories)
        assertEquals("https://example.com/new.jpg", refreshed.imageUrl)
        // Hers:
        assertEquals("Add extra garlic", refreshed.notes)
        assertTrue(refreshed.favorite)
        assertEquals(2.0, refreshed.lastScale, 0.0)
        assertFalse(refreshed.showUsUnits)
        assertEquals(listOf("card-1.jpg"), refreshed.cardPhotos)
        assertEquals("cover-7.jpg", refreshed.imageFile)
        assertEquals(100, refreshed.updatedAt)
    }

    @Test fun keepsHerOwnPhotoAddress() {
        val own = saved.copy(imageIsOwn = true, imageUrl = null, imageFile = "own-7.jpg")
        val refreshed = own.refreshedWith(page)
        assertEquals(null, refreshed.imageUrl)
        assertEquals("own-7.jpg", refreshed.imageFile)
    }

    @Test fun keepsThePartsSheEdited() {
        val edited = saved.copy(
            title = "Grandma's Chili",
            ingredients = listOf(RecipeLine("2 cups homemade broth")),
            editedFields = listOf(EditedField.TITLE, EditedField.INGREDIENTS),
        )
        val refreshed = edited.refreshedWith(page)
        assertEquals("Grandma's Chili", refreshed.title)
        assertEquals(listOf(RecipeLine("2 cups homemade broth")), refreshed.ingredients)
        // Parts she didn't edit still update.
        assertEquals(page.steps, refreshed.steps)
        assertEquals(485, refreshed.totalMinutes)
    }
}
