package io.github.isaiahyoder.recipebox.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.isaiahyoder.recipebox.data.RecipeDatabase
import io.github.isaiahyoder.recipebox.data.RecipeEntity
import io.github.isaiahyoder.recipebox.model.RecipeLine
import io.github.isaiahyoder.recipebox.photos.PhotoStore
import io.github.isaiahyoder.recipebox.tags.TagRefresher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Runs on a device or emulator against a real Room database in memory. */
@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: RecipeDatabase
    private lateinit var folder: File
    private lateinit var photos: PhotoStore
    private lateinit var library: LibraryRepository
    private lateinit var recipes: RecipeRepository

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, RecipeDatabase::class.java).build()
        folder = File(context.cacheDir, "repository-test-photos").apply { deleteRecursively() }
        photos = PhotoStore(context, OkHttpClient(), "test", folder)
        val tags = TagRefresher(database)
        library = LibraryRepository(database, tags)
        recipes = RecipeRepository(database, photos, tags)
    }

    @After fun tearDown() {
        database.close()
        folder.deleteRecursively()
    }

    private fun cake(id: Long = 0) = RecipeEntity(
        id = id,
        title = "Test Cake",
        ingredients = listOf(RecipeLine("2 cups flour"), RecipeLine("1 cup sugar")),
        steps = listOf(RecipeLine("Bake.")),
        createdAt = 1,
        updatedAt = 1,
    )

    @Test fun aTagSheAddsFillsTheCategoryItFeeds() = runBlocking {
        val id = recipes.save(cake(), PhotoChange.Keep)
        library.makeCategory("Family Favorites")
        library.addTag(id, "Family Favorites")
        val category = database.categoryDao().getCategories().single()
        assertEquals(listOf("Family Favorites"), category.feederTags)
        assertEquals(listOf(category.id), database.categoryDao().getCategoryIds(id))

        library.removeTag(id, "Family Favorites")
        assertTrue(database.categoryDao().getCategoryIds(id).isEmpty())
    }

    @Test fun savingEditsKeepsACoverDownloadedWhileTheEditorWasOpen() = runBlocking {
        val id = recipes.save(cake(), PhotoChange.Keep)
        val opened = database.recipeDao().getRecipe(id)!!
        // A photo download finishes after she opened the editor.
        database.recipeDao().setImage(id, "https://example.com/cake.jpg", "cover-$id.jpg")
        database.recipeDao().setFavorite(id, true)

        recipes.save(opened.copy(title = "Better Cake"), PhotoChange.Keep)

        val saved = database.recipeDao().getRecipe(id)!!
        assertEquals("Better Cake", saved.title)
        assertEquals("cover-$id.jpg", saved.imageFile)
        assertTrue(saved.favorite)
        assertEquals("2 cups flour\n1 cup sugar", saved.ingredientText)
        assertTrue(database.tagDao().observeTagNames(id).first().contains("Dessert"))
    }

    @Test fun replacingThePhotoDeletesTheOldFileAfterSaving() = runBlocking {
        val id = recipes.save(cake(), PhotoChange.Keep)
        val old = File(folder, "cover-$id.jpg").apply { writeText("old") }
        database.recipeDao().setImage(id, "https://example.com/cake.jpg", old.name)

        recipes.save(database.recipeDao().getRecipe(id)!!, PhotoChange.Replace("own-$id-1.jpg"))

        val saved = database.recipeDao().getRecipe(id)!!
        assertEquals("own-$id-1.jpg", saved.imageFile)
        assertTrue(saved.imageIsOwn)
        assertNull(saved.imageUrl)
        assertFalse(old.exists())
    }

    @Test fun deletingARecipeDeletesItsPhotoFiles() = runBlocking {
        val card = File(folder, "card-1.jpg").apply { writeText("card") }
        val id = recipes.save(cake().copy(cardPhotos = listOf(card.name)), PhotoChange.Keep)

        recipes.delete(id)

        assertNull(database.recipeDao().getRecipe(id))
        assertFalse(card.exists())
    }
}
