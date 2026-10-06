package io.github.isaiahyoder.recipebox.data

import io.github.isaiahyoder.recipebox.model.RecipeLine
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
        ).addMigrations(*ALL_MIGRATIONS).build()
        try {
            runBlocking {
                val dao = database.recipeDao()
                val recipe = dao.getRecipe(1)!!
                assertEquals("Test Soup", recipe.title)
                assertEquals("Use homemade stock", recipe.notes)
                assertTrue(recipe.favorite)
                assertEquals(2.0, recipe.lastScale, 0.0)
                assertEquals(listOf(RecipeLine("2 cups broth")), recipe.ingredients)
                assertEquals(listOf("Family Favorite"), database.tagDao().observeTagNames(1).first())
                assertEquals(listOf(1L), database.categoryDao().getCategoryIds(1))
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
        ).addMigrations(*ALL_MIGRATIONS).build()
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

    @Test fun version3RecipesAndListsSurviveTheUpdateToVersion4() {
        helper.createDatabase(DB_NAME_V3, 3).apply {
            execSQL(
                """
                INSERT INTO recipes (id, title, imageFile, imageIsOwn, ingredients, steps, siteCategories,
                    siteCuisines, siteKeywords, notes, favorite, lastScale, showUsUnits, createdAt, updatedAt)
                VALUES (3, 'Test Pie', 'own-3.jpg', 1, '[{"text":"1 cup sugar"}]', '[{"text":"Bake."}]', '[]',
                    '[]', '[]', 'Grandma''s', 1, 1.5, 1, 100, 200)
                """.trimIndent()
            )
            execSQL("INSERT INTO grocery_lists (id, name, hideStaples, createdAt, updatedAt) VALUES (1, 'Sunday', 1, 100, 200)")
            execSQL("INSERT INTO grocery_list_recipes (listId, recipeId, scale, addedAt) VALUES (1, 3, 2.0, 100)")
            execSQL("INSERT INTO grocery_line_state (listId, lineKey, checked, hidden, customText) VALUES (1, 'r:sugar', 1, 0, NULL)")
            close()
        }

        helper.runMigrationsAndValidate(DB_NAME_V3, 4, true).close()

        val database = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            RecipeDatabase::class.java,
            DB_NAME_V3,
        ).addMigrations(*ALL_MIGRATIONS).build()
        try {
            runBlocking {
                val dao = database.recipeDao()
                val recipe = dao.getRecipe(3)!!
                assertEquals("Test Pie", recipe.title)
                assertEquals("own-3.jpg", recipe.imageFile)
                assertEquals("Grandma's", recipe.notes)
                assertEquals(1.5, recipe.lastScale, 0.0)
                assertTrue(recipe.cardPhotos.isEmpty())
                dao.update(recipe.copy(cardPhotos = listOf("card-a.jpg", "card-b.jpg")))
                assertEquals(listOf("card-a.jpg", "card-b.jpg"), dao.getRecipe(3)!!.cardPhotos)

                val grocery = database.groceryDao()
                assertEquals(listOf("Sunday"), grocery.getLists().map { it.name })
                assertEquals(2.0, grocery.getRecipeLink(1, 3)!!.scale, 0.0)
            }
        } finally {
            database.close()
        }
    }

    @Test fun version4CategoriesStayHersAfterTheUpdateToVersion5() {
        helper.createDatabase(DB_NAME_V4, 4).apply {
            execSQL(
                """
                INSERT INTO recipes (id, title, imageIsOwn, ingredients, steps, siteCategories, siteCuisines,
                    siteKeywords, notes, favorite, lastScale, showUsUnits, cardPhotos, createdAt, updatedAt)
                VALUES (4, 'Test Cake', 0, '[{"text":"2 cups flour"}]', '[{"text":"Bake."}]', '[]',
                    '[]', '[]', 'Use cake flour', 0, 1.0, 1, '[]', 100, 200)
                """.trimIndent()
            )
            execSQL("INSERT INTO categories (id, name, position) VALUES (1, 'Dessert', 0)")
            execSQL("INSERT INTO recipe_categories (recipeId, categoryId) VALUES (4, 1)")
            close()
        }

        helper.runMigrationsAndValidate(DB_NAME_V4, 5, true).close()

        val database = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            RecipeDatabase::class.java,
            DB_NAME_V4,
        ).addMigrations(*ALL_MIGRATIONS).build()
        try {
            runBlocking {
                val dao = database.recipeDao()
                val recipe = dao.getRecipe(4)!!
                assertEquals("Use cake flour", recipe.notes)
                assertTrue(recipe.editedFields.isEmpty())
                val link = database.categoryDao().getCategoryLinks(4).single()
                assertEquals(TagSource.MANUAL, link.source)
                assertEquals(false, link.hidden)
                assertTrue(database.categoryDao().getCategories().single().feederTags.isEmpty())
                assertEquals(null, database.pageDao().getPage(4))
            }
        } finally {
            database.close()
        }
    }

    @Test fun version5RecipesBecomeSearchableAfterTheUpdateToVersion6() {
        helper.createDatabase(DB_NAME_V5, 5).apply {
            execSQL(
                """
                INSERT INTO recipes (id, title, imageIsOwn, ingredients, steps, siteCategories, siteCuisines,
                    siteKeywords, notes, favorite, lastScale, showUsUnits, cardPhotos, editedFields, createdAt, updatedAt)
                VALUES (5, 'Test Bread', 0, '[{"text":"For the dough","isHeader":true},{"text":"3 cups flour"}]',
                    '[{"text":"Knead."}]', '[]', '[]', '[]', '', 0, 1.0, 1, '[]', '["title"]', 100, 200)
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(DB_NAME_V5, 6, true).close()

        val database = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            RecipeDatabase::class.java,
            DB_NAME_V5,
        ).addMigrations(*ALL_MIGRATIONS).build()
        try {
            runBlocking {
                val dao = database.recipeDao()
                assertEquals(listOf("title"), dao.getRecipe(5)!!.editedFields)
                // The JSON's own keys never match, before or after filling the search text.
                assertTrue(dao.observeSummaries("isHeader").first().isEmpty())
                assertEquals(1, dao.indexRecipes())
                assertEquals("3 cups flour", dao.getRecipe(5)!!.ingredientText)
                assertEquals(listOf(5L), dao.observeSummaries("FLOUR").first().map { it.id })
                assertTrue(dao.observeSummaries("dough").first().isEmpty())
                assertEquals(0, dao.indexRecipes())
            }
        } finally {
            database.close()
        }
    }

    @Test fun version6RecordsGetPermanentIdsInTheUpdateToVersion7() {
        helper.createDatabase(DB_NAME_V6, 6).apply {
            for (id in 1..2) {
                execSQL(
                    """
                    INSERT INTO recipes (id, title, imageIsOwn, ingredients, steps, siteCategories, siteCuisines,
                        siteKeywords, notes, favorite, lastScale, showUsUnits, cardPhotos, editedFields, createdAt,
                        updatedAt, ingredientText)
                    VALUES ($id, 'Test Recipe $id', 0, '[{"text":"1 cup flour"}]', '[]', '[]', '[]', '[]', '', 0, 1.0,
                        1, '[]', '[]', 100, ${200 + id}, '1 cup flour')
                    """.trimIndent()
                )
            }
            execSQL("INSERT INTO categories (id, name, position, feederTags) VALUES (1, 'Weeknight', 0, '[]')")
            execSQL("INSERT INTO grocery_lists (id, name, hideStaples, createdAt, updatedAt, units) VALUES (1, 'Party', 0, 10, 20, 'US')")
            execSQL("INSERT INTO section_overrides (nameKey, section) VALUES ('candle', 'OTHER')")
            close()
        }

        helper.runMigrationsAndValidate(DB_NAME_V6, 7, true, *ALL_MIGRATIONS).close()

        val database = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            RecipeDatabase::class.java,
            DB_NAME_V6,
        ).addMigrations(*ALL_MIGRATIONS).build()
        try {
            runBlocking {
                val dao = database.recipeDao()
                val first = dao.getRecipe(1)!!
                val second = dao.getRecipe(2)!!
                val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
                assertTrue(first.uid, uuid.matches(first.uid))
                assertTrue(uuid.matches(second.uid))
                assertTrue(first.uid != second.uid)
                assertEquals(201L, first.changedAt)
                assertEquals("A recipe without a link or card photos was typed", "typed", first.sourceKind)
                assertTrue(uuid.matches(database.categoryDao().getCategories().single().uid))
                assertTrue(uuid.matches(database.groceryDao().getLists().single().uid))
                assertEquals(20L, database.groceryDao().getLists().single().changedAt)

                // The ingredient index fills at startup.
                val ingredients = database.ingredientDao()
                assertTrue(ingredients.recipeIdsUsing(listOf("flour")).isEmpty())
                assertEquals(2, dao.indexRecipes())
                assertEquals(listOf(1L, 2L), ingredients.recipeIdsUsing(listOf("flour")))
                assertEquals(0, dao.indexRecipes())

                // Her changes count; the uid stays through an update.
                dao.setFavorite(1, true)
                assertTrue(dao.getRecipe(1)!!.changedAt > 201L)
                dao.update(first.copy(title = "Renamed", uid = ""))
                assertEquals(first.uid, dao.getRecipe(1)!!.uid)

                // A deletion is remembered by uid.
                dao.delete(2)
                val deletion = database.backupDao().deletions().single()
                assertEquals(second.uid, deletion.uid)
                assertEquals(DeletedKind.RECIPE, deletion.kind)
                assertEquals(listOf(1L), ingredients.recipeIdsUsing(listOf("flour")))
                dao.update(dao.getRecipe(1)!!.copy(ingredients = listOf(RecipeLine("2 eggs"))))
                assertEquals(listOf("egg"), ingredients.getIngredients(1).map { it.nameKey })
            }
        } finally {
            database.close()
        }
    }

    private companion object {
        const val DB_NAME_V6 = "migration-test-v6.db"
        const val DB_NAME_V5 = "migration-test-v5.db"
        const val DB_NAME_V4 = "migration-test-v4.db"
        const val DB_NAME = "migration-test.db"
        const val DB_NAME_V2 = "migration-test-v2.db"
        const val DB_NAME_V3 = "migration-test-v3.db"
    }
}
