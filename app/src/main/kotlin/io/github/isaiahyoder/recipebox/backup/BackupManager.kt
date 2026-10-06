package io.github.isaiahyoder.recipebox.backup

import androidx.room.withTransaction
import io.github.isaiahyoder.recipebox.data.RecipeDatabase
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
 * restore. Photos she took herself and recipe card photos are included,
 * because they can't be downloaded again. Backups from before card photos
 * existed read as recipes without card photos.
 */
@Serializable
data class BackupFile(
    val format: Int = FORMAT,
    val appVersion: String,
    val exportedAt: Long,
    val recipes: List<BackupRecipe>,
    val tags: List<BackupTag>,
    val recipeTags: List<BackupRecipeTag>,
    val categories: List<BackupCategory>,
    val recipeCategories: List<BackupRecipeCategory>,
    val groceryLists: List<BackupGroceryList>,
    val groceryListRecipes: List<BackupGroceryListRecipe>,
    val groceryManualItems: List<BackupGroceryManualItem>,
    val groceryLineStates: List<BackupGroceryLineState>,
    val sectionOverrides: List<BackupSectionOverride>,
    val themeMode: String? = null,
    val deletions: List<BackupDeletion> = emptyList(),
) {
    /** File names of her own photos and card photos, which travel with the backup. */
    val ownPhotoNames: List<String>
        get() = recipes.flatMap { recipe -> listOfNotNull(recipe.imageFile.takeIf { recipe.imageIsOwn }) + recipe.cardPhotos }

    companion object {
        /**
         * Raise when the backup layout changes; restore must keep reading older formats.
         * Format 2 (0.5.0) adds edited parts, suggested tags, category feeder tags, and
         * where each category membership came from. Format 3 (0.6.0) adds permanent
         * uids, change times, and deletions. Older backups read with defaults.
         */
        const val FORMAT = 3
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
            recipes = dao.recipes().map { (if (it.imageIsOwn) it else it.copy(imageFile = null)).toBackup() },
            tags = dao.tags().map { it.toBackup() },
            recipeTags = dao.recipeTags().map { it.toBackup() },
            categories = dao.categories().map { it.toBackup() },
            recipeCategories = dao.recipeCategories().map { it.toBackup() },
            groceryLists = dao.groceryLists().map { it.toBackup() },
            groceryListRecipes = dao.groceryListRecipes().map { it.toBackup() },
            groceryManualItems = dao.groceryManualItems().map { it.toBackup() },
            groceryLineStates = dao.groceryLineStates().map { it.toBackup() },
            sectionOverrides = dao.sectionOverrides().map { it.toBackup() },
            themeMode = settings.themeMode.value.name,
            deletions = dao.deletions().map { it.toBackup() },
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
            val image = if (recipe.imageIsOwn && recipe.imageFile !in restoredPhotos) null else recipe.imageFile
            recipe.copy(imageFile = image, cardPhotos = recipe.cardPhotos.filter { it in restoredPhotos })
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
            dao.clearDeletions()
            dao.insertRecipes(recipes.map { it.toEntity() })
            dao.insertTags(backup.tags.map { it.toEntity() })
            dao.insertRecipeTags(backup.recipeTags.map { it.toEntity() })
            dao.insertCategories(backup.categories.map { it.toEntity(backup.exportedAt) })
            dao.insertRecipeCategories(backup.recipeCategories.map { it.toEntity() })
            dao.insertGroceryLists(backup.groceryLists.map { it.toEntity() })
            dao.insertGroceryListRecipes(backup.groceryListRecipes.map { it.toEntity() })
            dao.insertGroceryManualItems(backup.groceryManualItems.map { it.toEntity() })
            dao.insertGroceryLineStates(backup.groceryLineStates.map { it.toEntity() })
            dao.insertSectionOverrides(backup.sectionOverrides.map { it.toEntity(backup.exportedAt) })
            dao.insertDeletions(backup.deletions.map { it.toEntity() })
            // The ingredient index isn't in backups; it's rebuilt from the recipes.
            database.recipeDao().indexRecipes()
        }

        // Old photo files no restored recipe uses are removed; cover photos download again.
        val keep = recipes.flatMap { listOfNotNull(it.imageFile) + it.cardPhotos }.toSet()
        withContext(Dispatchers.IO) { photos.deleteAllExcept(keep) }
        backup.themeMode?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }?.let(settings::setThemeMode)
    }

    companion object {
        const val JSON_ENTRY = "backup.json"
        const val PHOTO_PREFIX = "photos/"
    }
}
