package io.github.isaiahyoder.recipebox.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves that her saved data survives each database update. Every new
 * database version adds a test here, starting from the oldest shipped schema
 * in app/schemas.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), RecipeDatabase::class.java)

    @Test fun version1RecipesSurviveTheUpdateToVersion2() {
        helper.createDatabase(DB_NAME, 1).apply {
            execSQL(
                """
                INSERT INTO recipes (id, title, imageIsOwn, ingredients, steps, siteCategories, siteCuisines,
                    siteKeywords, notes, favorite, lastScale, showUsUnits, createdAt, updatedAt)
                VALUES (1, 'Test Soup', 0, '[{"text":"2 cups broth"}]', '[{"text":"Simmer."}]', '["Soup"]',
                    '[]', '[]', 'Use homemade stock', 1, 2.0, 1, 100, 200)
                """.trimIndent()
            )
            execSQL("INSERT INTO tags (id, name) VALUES (1, 'Family Favorite')")
            execSQL("INSERT INTO recipe_tags (recipeId, tagId, source, hidden) VALUES (1, 1, 'MANUAL', 0)")
            execSQL("INSERT INTO categories (id, name, position) VALUES (1, 'Weeknight', 0)")
            execSQL("INSERT INTO recipe_categories (recipeId, categoryId) VALUES (1, 1)")
            close()
        }

        helper.runMigrationsAndValidate(DB_NAME, 2, true).close()

        val database = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            RecipeDatabase::class.java,
            DB_NAME,
        ).build()
        try {
            runBlocking {
                val dao = database.recipeDao()
                val recipe = dao.getRecipe(1)!!
                assertEquals("Test Soup", recipe.title)
                assertEquals("Use homemade stock", recipe.notes)
                assertTrue(recipe.favorite)
                assertEquals(2.0, recipe.lastScale, 0.0)
                assertEquals(listOf(RecipeLine("2 cups broth")), recipe.ingredients)
                assertEquals(listOf("Family Favorite"), dao.observeTagNames(1).first())
                assertEquals(listOf(1L), dao.getCategoryIds(1))
                assertTrue(database.importJobDao().observeAll().first().isEmpty())
            }
        } finally {
            database.close()
        }
    }

    @Test fun version2QueueAndRecipesSurviveTheUpdateToVersion3() {
        helper.createDatabase(DB_NAME_V2, 2).apply {
            execSQL(
                """
                INSERT INTO recipes (id, title, imageIsOwn, ingredients, steps, siteCategories, siteCuisines,
                    siteKeywords, notes, favorite, lastScale, showUsUnits, createdAt, updatedAt)
                VALUES (7, 'Test Bread', 0, '[{"text":"3 cups flour"}]', '[{"text":"Bake."}]', '[]',
                    '[]', '[]', '', 0, 1.0, 1, 100, 200)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO import_jobs (id, url, status, attempts, nextAttemptAt, createdAt, updatedAt)
                VALUES (1, 'https://example.com/a', 'FAILED', 3, 0, 100, 200)
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(DB_NAME_V2, 3, true).close()

        val database = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            RecipeDatabase::class.java,
            DB_NAME_V2,
        ).build()
        try {
            runBlocking {
                assertEquals("Test Bread", database.recipeDao().getRecipe(7)!!.title)
                assertEquals(ImportStatus.FAILED, database.importJobDao().observeAll().first().single().status)
                assertTrue(database.groceryDao().getLists().isEmpty())
            }
        } finally {
            database.close()
        }
    }

    private companion object {
        const val DB_NAME = "migration-test.db"
        const val DB_NAME_V2 = "migration-test-v2.db"
    }
}
