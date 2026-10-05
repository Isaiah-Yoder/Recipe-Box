package io.github.isaiahyoder.recipebox.tags

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.isaiahyoder.recipebox.data.RecipeDatabase
import io.github.isaiahyoder.recipebox.data.RecipeEntity
import io.github.isaiahyoder.recipebox.data.RecipeLine
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

        val dessert = dao.createCategory("Dessert")
        dao.addToCategoryByHand(soup, dessert) // her odd choice stays
        dao.setFeederTags(dessert, listOf(AutoTagger.DESSERT))
        refresher.applyCategories()
        assertEquals(setOf(cookies, pie, soup), dao.getCategoryMembers(dessert).filter { !it.hidden }.map { it.recipeId }.toSet())

        // She takes the pie out; feeders don't add it back.
        dao.setRecipeCategories(pie, emptyList())
        refresher.refreshAll()
        assertTrue(dessert !in dao.getCategoryIds(pie))
        assertTrue(dessert in dao.getCategoryIds(soup))

        // Removing the feeder takes out only recipes it added.
        dao.setFeederTags(dessert, emptyList())
        refresher.applyCategories()
        assertEquals(listOf(soup), dao.getCategoryMembers(dessert).filter { !it.hidden }.map { it.recipeId })
    }

    @Test fun suggestionsCountOnlyOnceConfirmed() = runBlocking {
        val dao = database.recipeDao()
        val pie = add("Pumpkin Pie", listOf("1 can pumpkin"))
        refresher.refreshAll()
        val thanksgiving = dao.createCategory("Thanksgiving")
        dao.setFeederTags(thanksgiving, listOf(AutoTagger.THANKSGIVING))
        refresher.applyCategories()
        assertTrue(dao.getCategoryIds(pie).isEmpty())
        assertEquals(TagSource.SUGGESTED, dao.getRecipeTags(pie).single { dao.findTagId(AutoTagger.THANKSGIVING) == it.tagId }.source)

        dao.acceptSuggestion(pie, AutoTagger.THANKSGIVING)
        refresher.refreshAll()
        assertEquals(listOf(thanksgiving), dao.getCategoryIds(pie))
    }

    @Test fun aDismissedSuggestionStaysDismissed() = runBlocking {
        val dao = database.recipeDao()
        val gravy = add("Turkey Gravy", listOf("giblets", "2 tablespoons flour"))
        refresher.refreshAll()
        dao.removeTag(gravy, AutoTagger.THANKSGIVING)
        refresher.refreshAll()
        val link = dao.getRecipeTags(gravy).single { dao.findTagId(AutoTagger.THANKSGIVING) == it.tagId }
        assertTrue(link.hidden)
    }
}
