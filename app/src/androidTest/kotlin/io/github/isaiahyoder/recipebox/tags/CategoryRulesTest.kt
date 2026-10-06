package io.github.isaiahyoder.recipebox.tags

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.isaiahyoder.recipebox.data.RecipeDatabase
import io.github.isaiahyoder.recipebox.data.RecipeEntity
import io.github.isaiahyoder.recipebox.model.RecipeLine
import io.github.isaiahyoder.recipebox.data.TagSource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Feeder tags fill categories, and her own choices always win. */
@RunWith(AndroidJUnit4::class)
class CategoryRulesTest {
    private lateinit var database: RecipeDatabase
    private lateinit var refresher: TagRefresher

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), RecipeDatabase::class.java).build()
        refresher = TagRefresher(database)
    }

    @After fun tearDown() = database.close()

    private suspend fun add(title: String, ingredients: List<String> = emptyList()): Long =
        database.recipeDao().insert(
            RecipeEntity(title = title, ingredients = ingredients.map { RecipeLine(it) }, createdAt = 1, updatedAt = 1)
        )

    @Test fun feederTagsFillAndHerChoicesWin() = runBlocking {
        val dao = database.recipeDao()
        val cookies = add("Sugar Cookies", listOf("2 cups flour"))
        val pie = add("Pumpkin Pie", listOf("1 can pumpkin"))
        val soup = add("Tomato Soup", listOf("4 tomatoes"))
        refresher.refreshAll()

        val dessert = database.categoryDao().createCategory("Dessert")
        database.categoryDao().addToCategoryByHand(soup, dessert) // her odd choice stays
        database.categoryDao().setFeederTags(dessert, listOf(AutoTagger.DESSERT))
        refresher.applyCategories()
        assertEquals(setOf(cookies, pie, soup), database.categoryDao().getCategoryMembers(dessert).filter { !it.hidden }.map { it.recipeId }.toSet())

        // She takes the pie out; feeders don't add it back.
        database.categoryDao().setRecipeCategories(pie, emptyList())
        refresher.refreshAll()
        assertTrue(dessert !in database.categoryDao().getCategoryIds(pie))
        assertTrue(dessert in database.categoryDao().getCategoryIds(soup))

        // Removing the feeder takes out only recipes it added.
        database.categoryDao().setFeederTags(dessert, emptyList())
        refresher.applyCategories()
        assertEquals(listOf(soup), database.categoryDao().getCategoryMembers(dessert).filter { !it.hidden }.map { it.recipeId })
    }

    @Test fun suggestionsCountOnlyOnceConfirmed() = runBlocking {
        val dao = database.recipeDao()
        val pie = add("Pumpkin Pie", listOf("1 can pumpkin"))
        refresher.refreshAll()
        val thanksgiving = database.categoryDao().createCategory("Thanksgiving")
        database.categoryDao().setFeederTags(thanksgiving, listOf(AutoTagger.THANKSGIVING))
        refresher.applyCategories()
        assertTrue(database.categoryDao().getCategoryIds(pie).isEmpty())
        assertEquals(TagSource.SUGGESTED, database.tagDao().getRecipeTags(pie).single { database.tagDao().findTagId(AutoTagger.THANKSGIVING) == it.tagId }.source)

        database.tagDao().acceptSuggestion(pie, AutoTagger.THANKSGIVING)
        refresher.refreshAll()
        assertEquals(listOf(thanksgiving), database.categoryDao().getCategoryIds(pie))
    }

    @Test fun aDismissedSuggestionStaysDismissed() = runBlocking {
        val dao = database.recipeDao()
        val gravy = add("Turkey Gravy", listOf("giblets", "2 tablespoons flour"))
        refresher.refreshAll()
        database.tagDao().removeTag(gravy, AutoTagger.THANKSGIVING)
        refresher.refreshAll()
        val link = database.tagDao().getRecipeTags(gravy).single { database.tagDao().findTagId(AutoTagger.THANKSGIVING) == it.tagId }
        assertTrue(link.hidden)
    }

    @Test fun oneRecipesRefreshFillsOnlyItsCategoriesAndRespectsHerChoices() = runBlocking {
        val dessert = database.categoryDao().createCategory("Dessert")
        database.categoryDao().setFeederTags(dessert, listOf(AutoTagger.DESSERT))
        val cookies = add("Sugar Cookies", listOf("2 cups flour"))
        val pie = add("Pumpkin Pie", listOf("1 can pumpkin"))

        refresher.refreshRecipe(database.recipeDao().getRecipe(cookies)!!)
        assertEquals(listOf(dessert), database.categoryDao().getCategoryIds(cookies))
        // Only the refreshed recipe was checked.
        assertTrue(database.categoryDao().getCategoryIds(pie).isEmpty())

        // She took the cookies out; refreshing them again keeps them out.
        database.categoryDao().setRecipeCategories(cookies, emptyList())
        refresher.refreshRecipe(database.recipeDao().getRecipe(cookies)!!)
        assertTrue(database.categoryDao().getCategoryIds(cookies).isEmpty())
    }
}
