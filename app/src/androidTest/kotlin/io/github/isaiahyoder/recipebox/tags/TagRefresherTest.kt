package io.github.isaiahyoder.recipebox.tags

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.isaiahyoder.recipebox.data.CategoryEntity
import io.github.isaiahyoder.recipebox.data.RecipeCategoryEntity
import io.github.isaiahyoder.recipebox.data.RecipeDao
import io.github.isaiahyoder.recipebox.data.RecipeDatabase
import io.github.isaiahyoder.recipebox.data.RecipeEntity
import io.github.isaiahyoder.recipebox.data.RecipeLine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on a device or emulator against a real Room database in memory. */
@RunWith(AndroidJUnit4::class)
class TagRefresherTest {
    private lateinit var database: RecipeDatabase
    private lateinit var dao: RecipeDao

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            RecipeDatabase::class.java,
        ).build()
        dao = database.recipeDao()
    }

    @After fun tearDown() = database.close()

    private suspend fun insertSoup(): Long = dao.insert(
        RecipeEntity(
            title = "Chicken Soup",
            ingredients = listOf(RecipeLine("2 cups shredded chicken"), RecipeLine("4 cups water")),
            steps = listOf(RecipeLine("Simmer everything in a slow cooker.")),
            totalMinutes = 25,
            siteCategories = listOf("Soup"),
            createdAt = 0,
            updatedAt = 0,
        )
    )

    private suspend fun tags(recipeId: Long) = dao.observeTagNames(recipeId).first().toSet()

    @Test fun keepsManualTagsHiddenTagsAndCategories() = runBlocking {
        val id = insertSoup()
        // An earlier version's rules produced "Old Rule Tag"; the current rules don't.
        dao.replaceAutoTags(id, TagResult(setOf("Soup", "Chicken", "Slow Cooker", "Old Rule Tag"), emptySet()))
        dao.addManualTag(id, "Grandma's")
        dao.addManualTag(id, "Soup") // She also chose a tag the rules produce.
        dao.removeTag(id, "Slow Cooker") // She removed an automatic tag.
        val category = dao.insertCategory(CategoryEntity(name = "Weeknight", position = 0))
        dao.addToCategory(RecipeCategoryEntity(id, category))

        val checked = TagRefresher(database).refreshAll()

        assertEquals(1, checked)
        val after = tags(id)
        assertTrue("Manual tag kept", "Grandma's" in after)
        assertTrue("Manual copy of a rule tag kept", "Soup" in after)
        assertTrue("Rule tag kept", "Chicken" in after)
        assertTrue("New rule tag added", AutoTagger.QUICK in after)
        assertTrue("Removed tag stays removed", "Slow Cooker" !in after)
        assertTrue("Stale automatic tag dropped", "Old Rule Tag" !in after)
        assertEquals(listOf(category), dao.getCategoryIds(id))
    }

    @Test fun runningTwiceGivesTheSameResult() = runBlocking {
        val id = insertSoup()
        dao.addManualTag(id, "Favorite Soup")
        TagRefresher(database).refreshAll()
        val first = tags(id)
        TagRefresher(database).refreshAll()
        assertEquals(first, tags(id))
    }

    @Test fun aRemovedTagStaysRemovedEvenWhenTheRulesProduceIt() = runBlocking {
        val id = insertSoup()
        TagRefresher(database).refreshAll()
        dao.addManualTag(id, "Chicken") // She confirmed a rule tag by hand,
        dao.removeTag(id, "Chicken") // then changed her mind.
        TagRefresher(database).refreshAll()
        assertTrue("Chicken" !in tags(id))

        dao.addManualTag(id, "Chicken") // Adding it again shows it again.
        TagRefresher(database).refreshAll()
        assertTrue("Chicken" in tags(id))
    }
}
