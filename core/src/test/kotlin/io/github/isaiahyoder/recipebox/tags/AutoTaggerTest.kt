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
        fromCard: Boolean = false,
    ) = AutoTagger.tags(TaggableRecipe(title, ingredients, steps, totalMinutes, categories, cuisines, keywords, fromCard))

    @Test fun mapsSiteCoursesToTheFixedNames() {
        val result = recipe(categories = listOf("Lunch", "Entree", "Sandwich"), cuisines = listOf("Puerto Rican"))
        assertEquals(setOf(AutoTagger.MAIN_DISH), result.tags)
    }

    @Test fun neverPassesThroughSiteLabels() {
        val result = recipe(
            keywords = listOf("dessert", "best ever grandma's secret recipe"),
            cuisines = listOf("Butter Cuisine", "Olive Oil Cuisine", "Italian Cuisine"),
        )
        assertEquals(setOf(AutoTagger.DESSERT, "Italian"), result.tags)
        assertTrue(result.tags.all { AutoTagger.groupOf(it) != null })
    }

    @Test fun readsTheCourseFromTheTitle() {
        assertTrue(AutoTagger.DESSERT in recipe(title = "Lemon Sugar Cookies").tags)
        assertTrue(AutoTagger.BREAKFAST in recipe(title = "Fluffy Pancakes").tags)
        assertTrue(AutoTagger.SAUCE in recipe(title = "Turkey Gravy").tags)
        assertTrue(AutoTagger.MAIN_DISH in recipe(title = "Chicken Pot Pie").tags)
        assertFalse(AutoTagger.DESSERT in recipe(title = "Chicken Pot Pie").tags)
        assertTrue(AutoTagger.DOUGH in recipe(title = "Graham Cracker Crust").tags)
    }

    @Test fun muttonAndGoatAreNotVegetarian() {
        val mutton = recipe(title = "Spinach Mutton Curry", ingredients = listOf("½ kg mutton, cubed", "2 onions"))
        assertTrue("Lamb" in mutton.tags)
        assertFalse(AutoTagger.VEGETARIAN in mutton.tags)
        val goat = recipe(title = "Goat Stew", categories = listOf("Main Course"), ingredients = listOf("1 kg goat meat", "2 potatoes"))
        assertFalse(AutoTagger.VEGETARIAN in goat.tags)
    }

    @Test fun aMeatDishWithoutACourseIsAMainDish() {
        val result = recipe(title = "Garlic Pork Chops", ingredients = listOf("4 pork chops", "3 cloves garlic"))
        assertTrue("Pork" in result.tags)
        assertTrue(AutoTagger.MAIN_DISH in result.tags)
    }

    @Test fun ignoresBrothWhenFindingTheProtein() {
        val result = recipe(title = "Rice Pilaf", ingredients = listOf("4 cups chicken broth", "2 cups rice"))
        assertFalse("Chicken" in result.tags)
        assertFalse(AutoTagger.VEGETARIAN in result.tags)
    }

    @Test fun marksVegetarianOnlyOnMainDishes() {
        val pasta = recipe(categories = listOf("Main Dish"), ingredients = listOf("1 lb pasta", "2 cups marinara"))
        assertTrue(AutoTagger.VEGETARIAN in pasta.tags)
        val cookies = recipe(title = "Sugar Cookies", ingredients = listOf("2 cups flour", "1 cup sugar"))
        assertFalse(AutoTagger.VEGETARIAN in cookies.tags)
    }

    @Test fun suggestsOccasionsWithoutTaggingThem() {
        val result = recipe(title = "Pumpkin Pie", ingredients = listOf("15 ounce can pumpkin"))
        assertTrue(AutoTagger.DESSERT in result.tags)
        assertFalse(AutoTagger.THANKSGIVING in result.tags)
        assertEquals(setOf(AutoTagger.THANKSGIVING), result.suggestions)
        // An ingredient alone doesn't suggest an occasion.
        assertTrue(recipe(title = "Spice Cookies", ingredients = listOf("1 tsp pumpkin pie spice")).suggestions.isEmpty())
    }

    @Test fun findsCookingMethodsAndQuickRecipes() {
        val result = recipe(
            title = "Easy Pulled Pork",
            steps = listOf("Place the pork in a slow cooker.", "Cook on Low for 8 hours."),
            totalMinutes = 25,
        )
        assertTrue("Slow Cooker" in result.tags)
        assertTrue(AutoTagger.QUICK in result.tags)
    }

    @Test fun doesNotCallGrilledCheeseGrilled() {
        val result = recipe(title = "Grilled Cheese", steps = listOf("Heat a skillet over medium heat."))
        assertFalse("Grilled" in result.tags)
    }

    @Test fun suggestsFeedersForHerCategoryNames() {
        assertEquals(listOf(AutoTagger.MAIN_DISH), FeederSuggestions.forCategory("Dinner", emptyList()))
        assertEquals(listOf(AutoTagger.BREAD), FeederSuggestions.forCategory("Breads", emptyList()))
        assertEquals(listOf(AutoTagger.SAUCE), FeederSuggestions.forCategory("Sauces", emptyList()))
        assertEquals(listOf(AutoTagger.DESSERT), FeederSuggestions.forCategory("Dessert", emptyList()))
        assertEquals(listOf("Slow Cooker"), FeederSuggestions.forCategory("Slow Cooker", emptyList()))
        assertEquals(listOf(AutoTagger.THANKSGIVING), FeederSuggestions.forCategory("Thanksgiving", emptyList()))
        assertTrue(FeederSuggestions.forCategory("Grandma's", emptyList()).isEmpty())
    }

    @Test fun tagsKindsOfDessertAndBreakfast() {
        assertTrue(AutoTagger.COOKIES in recipe(title = "Oatmeal Raisin Cookies").tags)
        assertTrue(AutoTagger.CAKES in recipe(title = "Lemon Cheesecake").tags)
        assertTrue(AutoTagger.CAKES in recipe(title = "Vanilla Cupcakes").tags)
        assertTrue(AutoTagger.PIES in recipe(title = "Mixed Berry Pie").tags)
        assertTrue(AutoTagger.QUICK_BREADS in recipe(title = "Banana Bread").tags)
        assertTrue(AutoTagger.QUICK_BREADS in recipe(title = "Blueberry Muffins").tags)
        assertTrue(AutoTagger.PANCAKES in recipe(title = "Buttermilk Pancakes").tags)
    }

    @Test fun savoryDishesAreNotDessertKinds() {
        assertFalse(AutoTagger.PIES in recipe(title = "Chicken Pot Pie").tags)
        assertFalse(AutoTagger.CAKES in recipe(title = "Crab Cakes", ingredients = listOf("1 pound crab meat")).tags)
        assertFalse(AutoTagger.CAKES in recipe(title = "Buttermilk Pancakes").tags)
    }

    @Test fun tagsChocolateHighProteinAndCards() {
        assertTrue(AutoTagger.CHOCOLATE in recipe(title = "Fudgy Brownies").tags)
        assertTrue(AutoTagger.CHOCOLATE in recipe(title = "Chocolate Chip Cookies").tags)
        assertTrue(AutoTagger.HIGH_PROTEIN in recipe(title = "Protein Pancakes").tags)
        assertTrue(AutoTagger.HIGH_PROTEIN in recipe(title = "Berry Shake", ingredients = listOf("1 scoop protein powder")).tags)
        assertTrue(AutoTagger.FROM_CARD in recipe(title = "Spice Cake", fromCard = true).tags)
        assertFalse(AutoTagger.FROM_CARD in recipe(title = "Spice Cake").tags)
    }

    @Test fun usesACardReadersCourseAndCuisine() {
        val result = recipe(title = "Grandma's Special", categories = listOf("Dessert"), cuisines = listOf("Eastern European"), fromCard = true)
        assertTrue(AutoTagger.DESSERT in result.tags)
        assertTrue("Eastern European" in result.tags)
    }

    @Test fun guessesTheCourseFromIngredientsWhenNothingNamesIt() {
        val sweet = recipe(title = "Grandma's Special", ingredients = listOf("2 cups flour", "1 cup sugar", "1/2 cup butter"))
        assertTrue(AutoTagger.DESSERT in sweet.tags)
        val dough = recipe(title = "Mom's Rolls", ingredients = listOf("4 cups flour", "1 packet yeast", "2 tablespoons sugar"))
        assertTrue(AutoTagger.BREAD in dough.tags)
        assertFalse(AutoTagger.DESSERT in dough.tags)
        val savory = recipe(title = "Aunt Jo's Bake", ingredients = listOf("2 cups flour", "1 tablespoon sugar", "1 onion", "1 cup cheddar"))
        assertFalse(AutoTagger.DESSERT in savory.tags)
        val named = recipe(title = "Breakfast Bake", ingredients = listOf("2 cups flour", "1 cup sugar"))
        assertFalse(AutoTagger.DESSERT in named.tags)
    }
}
