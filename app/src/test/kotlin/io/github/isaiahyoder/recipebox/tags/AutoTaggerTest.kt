package io.github.isaiahyoder.recipebox.tags

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoTaggerTest {
    private fun recipe(
        title: String = "Test",
        ingredients: List<String> = emptyList(),
        steps: List<String> = emptyList(),
        totalMinutes: Int? = null,
        categories: List<String> = emptyList(),
        cuisines: List<String> = emptyList(),
        keywords: List<String> = emptyList(),
    ) = AutoTagger.tags(TaggableRecipe(title, ingredients, steps, totalMinutes, categories, cuisines, keywords))

    @Test fun mapsSiteCategoriesAndCuisines() {
        val tags = recipe(categories = listOf("Lunch", "Entree", "Sandwich"), cuisines = listOf("Puerto Rican"))
        assertEquals(setOf("Lunch", "Main Dish", "Sandwich", "Puerto Rican"), tags)
    }

    @Test fun keepsOnlyKnownKeywords() {
        val tags = recipe(keywords = listOf("dessert", "best ever grandma's secret recipe"))
        assertEquals(setOf("Dessert"), tags)
    }

    @Test fun findsTheMainProtein() {
        val tags = recipe(ingredients = listOf("3 pounds pork shoulder", "2 tablespoons olive oil"))
        assertTrue("Pork" in tags)
        assertFalse(AutoTagger.MEATLESS in tags)
    }

    @Test fun ignoresBrothWhenFindingTheProteinButNotForMeatless() {
        val tags = recipe(ingredients = listOf("4 cups chicken broth", "2 cups rice"))
        assertFalse("Chicken" in tags)
        assertFalse(AutoTagger.MEATLESS in tags)
    }

    @Test fun marksMeatlessRecipes() {
        val tags = recipe(ingredients = listOf("2 cups flour", "1 cup milk", "2 eggs"))
        assertTrue(AutoTagger.MEATLESS in tags)
    }

    @Test fun findsCookingMethodsAndQuickRecipes() {
        val tags = recipe(
            title = "Easy Pulled Pork",
            steps = listOf("Place the pork in a slow cooker.", "Cook on Low for 8 hours."),
            totalMinutes = 25,
        )
        assertTrue("Slow Cooker" in tags)
        assertTrue(AutoTagger.QUICK in tags)
    }

    @Test fun doesNotCallGrilledCheeseGrilled() {
        val tags = recipe(title = "Grilled Cheese", steps = listOf("Heat a skillet over medium heat."))
        assertFalse("Grilled" in tags)
    }
}
