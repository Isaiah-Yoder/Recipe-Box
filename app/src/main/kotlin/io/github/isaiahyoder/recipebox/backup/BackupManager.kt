package io.github.isaiahyoder.recipebox.backup

import androidx.room.withTransaction
import io.github.isaiahyoder.recipebox.data.CategoryEntity
import io.github.isaiahyoder.recipebox.data.GroceryLineStateEntity
import io.github.isaiahyoder.recipebox.data.GroceryListEntity
import io.github.isaiahyoder.recipebox.data.GroceryListRecipeEntity
import io.github.isaiahyoder.recipebox.data.GroceryManualItemEntity
import io.github.isaiahyoder.recipebox.data.RecipeCategoryEntity
import io.github.isaiahyoder.recipebox.data.RecipeDatabase
import io.github.isaiahyoder.recipebox.data.RecipeEntity
import io.github.isaiahyoder.recipebox.data.RecipeTagEntity
import io.github.isaiahyoder.recipebox.data.SectionOverrideEntity
import io.github.isaiahyoder.recipebox.data.TagEntity
import io.github.isaiahyoder.recipebox.photos.PhotoStore
import io.github.isaiahyoder.recipebox.settings.AppSettings
import io.github.isaiahyoder.recipebox.settings.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Everything in a backup. Cover photos aren't included: each recipe keeps
 * the address its photo came from, and the photo downloads again after a
 * restore. Photos she took herself are included, because they can't be
 * downloaded again.
 */
@Serializable
data class BackupFile(
    val format: Int = FORMAT,
    val appVersion: String,
    val exportedAt: Long,
    val recipes: List<RecipeEntity>,
    val tags: List<TagEntity>,
    val recipeTags: List<RecipeTagEntity>,
    val categories: List<CategoryEntity>,
    val recipeCategories: List<RecipeCategoryEntity>,
    val groceryLists: List<GroceryListEntity>,
    val groceryListRecipes: List<GroceryListRecipeEntity>,
    val groceryManualItems: List<GroceryManualItemEntity>,
    val groceryLineStates: List<GroceryLineStateEntity>,
    val sectionOverrides: List<SectionOverrideEntity>,
    val themeMode: String? = null,
) {
    /** File names of her own photos, which travel with the backup. */
    val ownPhotoNames: List<String> get() = recipes.filter { it.imageIsOwn }.mapNotNull { it.imageFile }

    companion object {
        /** Raise when the backup layout changes; restore must keep reading older formats. */
        const val FORMAT = 1
    }
}

class BackupException(message: String) : Exception(message)

class BackupManager(
    private val database: RecipeDatabase,
    private val photos: PhotoStore,
    private val settings: AppSettings,
    private val appVersion: String,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun snapshot(): BackupFile = database.withTransaction {
        val dao = database.backupDao()
        BackupFile(
            appVersion = appVersion,
            exportedAt = clock(),
            // Downloaded cover photos are left out; only the address is kept.
            recipes = dao.recipes().map { if (it.imageIsOwn) it else it.copy(imageFile = null) },
            tags = dao.tags(),
            recipeTags = dao.recipeTags(),
            categories = dao.categories(),
            recipeCategories = dao.recipeCategories(),
            groceryLists = dao.groceryLists(),
            groceryListRecipes = dao.groceryListRecipes(),
            groceryManualItems = dao.groceryManualItems(),
            groceryLineStates = dao.groceryLineStates(),
            sectionOverrides = dao.sectionOverrides(),
            themeMode = settings.themeMode.value.name,
        )
    }

    fun encode(backup: BackupFile): ByteArray = json.encodeToString(backup).toByteArray()

    /** Changes only when her data changes, so an unchanged library isn't uploaded again. */
    fun contentHash(backup: BackupFile): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(encode(backup.copy(exportedAt = 0)))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** Writes a ZIP with backup.json and, when [includePhotos] is true, her own photos. */
    suspend fun writeZip(backup: BackupFile, output: OutputStream, includePhotos: Boolean) = withContext(Dispatchers.IO) {
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(JSON_ENTRY))
            zip.write(encode(backup))
            zip.closeEntry()
            if (includePhotos) {
                for (name in backup.ownPhotoNames) {
                    val file = photos.file(name)
                    if (!file.exists()) continue
                    zip.putNextEntry(ZipEntry(PHOTO_PREFIX + name))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    /** Reads a ZIP written by [writeZip]; returns the backup and any photos in it. */
    suspend fun readZip(input: InputStream): Pair<BackupFile, Map<String, ByteArray>> = withContext(Dispatchers.IO) {
        var backup: BackupFile? = null
        val photoBytes = mutableMapOf<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                when {
                    entry.name == JSON_ENTRY -> backup = decode(zip.readBytes())
                    entry.name.startsWith(PHOTO_PREFIX) && !entry.name.contains("..") ->
                        photoBytes[entry.name.removePrefix(PHOTO_PREFIX)] = zip.readBytes()
                }
            }
        }
        (backup ?: throw BackupException("This file isn't a Recipe Box backup.")) to photoBytes
    }

    fun decode(bytes: ByteArray): BackupFile {
        val backup = runCatching { json.decodeFromString<BackupFile>(bytes.decodeToString()) }
            .getOrElse { throw BackupException("This file isn't a Recipe Box backup, or it's damaged.") }
        if (backup.format > BackupFile.FORMAT) {
            throw BackupException("This backup was made by a newer version of Recipe Box. Update the app, then try again.")
        }
        return backup
    }

    /**
     * Replaces everything in the app with [backup]. It runs as one database
     * transaction, so a failure leaves her current recipes untouched.
     * [ownPhoto] supplies the bytes of each of her own photos by file name.
     */
    suspend fun restore(backup: BackupFile, ownPhoto: suspend (String) -> ByteArray?) {
        val restoredPhotos = mutableSetOf<String>()
        for (name in backup.ownPhotoNames) {
            val bytes = ownPhoto(name) ?: continue
            withContext(Dispatchers.IO) { photos.file(name).writeBytes(bytes) }
            restoredPhotos += name
        }
        val recipes = backup.recipes.map { recipe ->
            // A photo that didn't come with the backup is treated as missing.
            if (recipe.imageIsOwn && recipe.imageFile !in restoredPhotos) recipe.copy(imageFile = null) else recipe
        }

        database.withTransaction {
            val dao = database.backupDao()
            dao.clearGroceryLineStates()
            dao.clearGroceryManualItems()
            dao.clearGroceryListRecipes()
            dao.clearGroceryLists()
            dao.clearSectionOverrides()
            dao.clearRecipeCategories()
            dao.clearCategories()
            dao.clearRecipeTags()
            dao.clearTags()
            dao.clearRecipes()
            dao.insertRecipes(recipes)
            dao.insertTags(backup.tags)
            dao.insertRecipeTags(backup.recipeTags)
            dao.insertCategories(backup.categories)
            dao.insertRecipeCategories(backup.recipeCategories)
            dao.insertGroceryLists(backup.groceryLists)
            dao.insertGroceryListRecipes(backup.groceryListRecipes)
            dao.insertGroceryManualItems(backup.groceryManualItems)
            dao.insertGroceryLineStates(backup.groceryLineStates)
            dao.insertSectionOverrides(backup.sectionOverrides)
        }

        // Old photo files no restored recipe uses are removed; cover photos download again.
        val keep = recipes.mapNotNull { it.imageFile }.toSet()
        withContext(Dispatchers.IO) { photos.deleteAllExcept(keep) }
        backup.themeMode?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }?.let(settings::setThemeMode)
    }

    companion object {
        const val JSON_ENTRY = "backup.json"
        const val PHOTO_PREFIX = "photos/"
    }
}
