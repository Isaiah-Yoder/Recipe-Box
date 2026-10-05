package io.github.isaiahyoder.recipebox.backup

import io.github.isaiahyoder.recipebox.tags.TagResult
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.isaiahyoder.recipebox.data.CategoryEntity
import io.github.isaiahyoder.recipebox.data.GroceryLineStateEntity
import io.github.isaiahyoder.recipebox.data.GroceryListEntity
import io.github.isaiahyoder.recipebox.data.GroceryListRecipeEntity
import io.github.isaiahyoder.recipebox.data.GroceryManualItemEntity
import io.github.isaiahyoder.recipebox.data.RecipeCategoryEntity
import io.github.isaiahyoder.recipebox.data.RecipeDatabase
import io.github.isaiahyoder.recipebox.data.RecipeEntity
import io.github.isaiahyoder.recipebox.data.RecipeLine
import io.github.isaiahyoder.recipebox.data.SectionOverrideEntity
import io.github.isaiahyoder.recipebox.photos.PhotoStore
import io.github.isaiahyoder.recipebox.settings.AppSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
class BackupManagerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: RecipeDatabase
    private lateinit var photos: PhotoStore
    private lateinit var manager: BackupManager
    private lateinit var photoFolder: File

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, RecipeDatabase::class.java).build()
        // Its own photo folder and settings file, so the test never touches the app's real data.
        photoFolder = File(context.cacheDir, "backup-test-photos").apply { deleteRecursively() }
        photos = PhotoStore(context, OkHttpClient(), "test", photoFolder)
        manager = BackupManager(database, photos, AppSettings(context, "backup-test-settings"), appVersion = "test")
    }

    @After fun tearDown() {
        database.close()
        photoFolder.deleteRecursively()
    }

    @Test fun aBackupRestoresEverythingAndLeavesCoverPhotosToDownload() = runBlocking {
        val dao = database.recipeDao()
        val grocery = database.groceryDao()
        val imported = dao.insert(
            RecipeEntity(
                title = "Imported Soup", sourceUrl = "https://example.com/soup", imageUrl = "https://example.com/soup.jpg",
                imageFile = "cover-1.jpg", ingredients = listOf(RecipeLine("1 cup milk")), notes = "Add more pepper",
                favorite = true, lastScale = 2.0, createdAt = 1, updatedAt = 2,
            )
        )
        photos.file("own-test.jpg").writeBytes(byteArrayOf(1, 2, 3))
        val typed = dao.insert(
            RecipeEntity(title = "Grandma's Card", imageFile = "own-test.jpg", imageIsOwn = true, createdAt = 3, updatedAt = 4)
        )
        photos.file("card-front.jpg").writeBytes(byteArrayOf(4, 5))
        photos.file("card-back.jpg").writeBytes(byteArrayOf(6))
        val card = dao.insert(
            RecipeEntity(title = "Card Cookies", cardPhotos = listOf("card-front.jpg", "card-back.jpg"), createdAt = 5, updatedAt = 6)
        )
        dao.addManualTag(imported, "Family")
        dao.replaceAutoTags(imported, TagResult(setOf("Soup", "Old"), emptySet()))
        dao.removeTag(imported, "Old")
        val category = dao.insertCategory(CategoryEntity(name = "Weeknight", position = 0))
        dao.addToCategory(RecipeCategoryEntity(imported, category))
        val list = grocery.insertList(GroceryListEntity(name = "Saturday", createdAt = 1, updatedAt = 1))
        grocery.upsertRecipe(GroceryListRecipeEntity(list, imported, 2.0, 1))
        grocery.insertManualItem(GroceryManualItemEntity(listId = list, text = "paper towels", createdAt = 1))
        grocery.upsertLineState(GroceryLineStateEntity(list, "r:milk", checked = true))
        grocery.setSectionOverride(SectionOverrideEntity("milk", "OTHER"))

        val bytes = ByteArrayOutputStream().also { manager.writeZip(manager.snapshot(), it, includePhotos = true) }.toByteArray()
        val (backup, photoBytes) = manager.readZip(ByteArrayInputStream(bytes))
        assertEquals(setOf("own-test.jpg", "card-front.jpg", "card-back.jpg"), photoBytes.keys)

        // A different library is replaced by the restore.
        dao.delete(imported)
        dao.insert(RecipeEntity(title = "Added Later", createdAt = 9, updatedAt = 9))
        photos.file("own-test.jpg").delete()
        photos.file("card-back.jpg").delete()

        manager.restore(backup) { photoBytes[it] }

        val titles = database.backupDao().recipes().map { it.title }.toSet()
        assertEquals(setOf("Imported Soup", "Grandma's Card", "Card Cookies"), titles)
        assertEquals(listOf("card-front.jpg", "card-back.jpg"), dao.getRecipe(card)!!.cardPhotos)
        assertTrue(photos.file("card-back.jpg").exists())
        val soup = dao.getRecipe(imported)!!
        assertNull("Cover photos download again", soup.imageFile)
        assertEquals("https://example.com/soup.jpg", soup.imageUrl)
        assertEquals("Add more pepper", soup.notes)
        assertTrue(soup.favorite)
        assertEquals(2.0, soup.lastScale, 0.0)
        assertEquals("own-test.jpg", dao.getRecipe(typed)!!.imageFile)
        assertTrue(photos.file("own-test.jpg").exists())
        assertEquals(listOf("Family", "Soup"), dao.observeTagNames(imported).first())
        assertEquals(listOf(category), dao.getCategoryIds(imported))
        assertEquals(1, grocery.getLists().size)
        assertEquals(listOf("paper towels"), grocery.observeManualItems(list).first().map { it.text })
        assertTrue(grocery.getLineState(list, "r:milk")!!.checked)
        assertEquals(listOf(imported), dao.recipesMissingCover().map { it.id })
    }

    @Test fun aBackupFromBeforeCardPhotosStillReads() {
        // Written by 0.3.0, whose recipes had no cardPhotos field.
        val backup = manager.decode(
            """
            {"format": 1, "appVersion": "0.3.0", "exportedAt": 1,
             "recipes": [{"id": 1, "title": "Old Soup", "createdAt": 1, "updatedAt": 2}],
             "tags": [], "recipeTags": [], "categories": [], "recipeCategories": [], "groceryLists": [],
             "groceryListRecipes": [], "groceryManualItems": [], "groceryLineStates": [], "sectionOverrides": []}
            """.trimIndent().toByteArray()
        )
        assertEquals("Old Soup", backup.recipes.single().title)
        assertTrue(backup.recipes.single().cardPhotos.isEmpty())
        assertTrue(backup.ownPhotoNames.isEmpty())
    }

    @Test fun theFingerprintIgnoresTheBackupTime() = runBlocking {
        database.recipeDao().insert(RecipeEntity(title = "A", createdAt = 1, updatedAt = 1))
        val first = manager.snapshot()
        val second = first.copy(exportedAt = first.exportedAt + 60_000)
        assertEquals(manager.contentHash(first), manager.contentHash(second))
    }

    @Test(expected = BackupException::class)
    fun aNewerFormatIsRefused() {
        manager.decode("""{"format":99,"appVersion":"9","exportedAt":0,"recipes":[],"tags":[],"recipeTags":[],"categories":[],"recipeCategories":[],"groceryLists":[],"groceryListRecipes":[],"groceryManualItems":[],"groceryLineStates":[],"sectionOverrides":[]}""".toByteArray())
    }
}
