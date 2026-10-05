package io.github.isaiahyoder.recipebox

import android.app.Application
import android.content.Context
import android.webkit.WebSettings
import io.github.isaiahyoder.recipebox.backup.BackupManager
import io.github.isaiahyoder.recipebox.cards.CardAssets
import io.github.isaiahyoder.recipebox.cards.CardReader
import io.github.isaiahyoder.recipebox.cards.GeminiCardReader
import io.github.isaiahyoder.recipebox.cards.NanoCardReader
import io.github.isaiahyoder.recipebox.cards.TextCardReader
import io.github.isaiahyoder.recipebox.backup.DriveBackup
import io.github.isaiahyoder.recipebox.backup.PhotoRestorer
import io.github.isaiahyoder.recipebox.data.RecipeDatabase
import io.github.isaiahyoder.recipebox.importer.ImportQueue
import io.github.isaiahyoder.recipebox.importer.PageFetcher
import io.github.isaiahyoder.recipebox.importer.RecipeImporter
import io.github.isaiahyoder.recipebox.importer.RecipeRefresher
import io.github.isaiahyoder.recipebox.importer.WebViewPageLoader
import io.github.isaiahyoder.recipebox.photos.PhotoStore
import io.github.isaiahyoder.recipebox.settings.AppSettings
import io.github.isaiahyoder.recipebox.tags.TagRefresher
import io.github.isaiahyoder.recipebox.update.AppUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class RecipeBoxApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // A cover photo file can go missing; download it again in the background.
        CoroutineScope(Dispatchers.IO).launch {
            if (container.photoRestorer.forgetMissingFiles() > 0) PhotoRestorer.schedule(this@RecipeBoxApp)
            // Card photos from a scan she didn't save.
            container.photos.deleteUnusedCardPhotos(container.database.recipeDao().allCardPhotos().flatMap { it.cardPhotos }.toSet())
        }
    }
}

/** Creates the app's long-lived objects once and shares them. */
class AppContainer(context: Context) {
    val database: RecipeDatabase by lazy { RecipeDatabase.create(context) }

    val settings: AppSettings by lazy { AppSettings(context) }

    val tagRefresher: TagRefresher by lazy { TagRefresher(database) }

    /** The phone's own browser identity, so sites see an ordinary visitor. */
    private val userAgent: String by lazy {
        runCatching { WebSettings.getDefaultUserAgent(context) }.getOrDefault(FALLBACK_USER_AGENT)
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val photos: PhotoStore by lazy { PhotoStore(context, httpClient, userAgent) }

    val importer: RecipeImporter by lazy {
        RecipeImporter(
            dao = database.recipeDao(),
            fetcher = pageFetcher,
            browserLoader = WebViewPageLoader(context),
            photos = photos,
        )
    }

    val importQueue: ImportQueue by lazy { ImportQueue(context, database.importJobDao(), importer) }

    private val pageFetcher: PageFetcher by lazy { PageFetcher(httpClient, userAgent) }

    val photoRestorer: PhotoRestorer by lazy { PhotoRestorer(database.recipeDao(), photos, pageFetcher) }

    val backupManager: BackupManager by lazy {
        BackupManager(database, photos, settings, appVersion = BuildConfigValues.versionName(context))
    }

    val driveBackup: DriveBackup by lazy { DriveBackup(context, httpClient, backupManager, settings, photos) }

    val updater: AppUpdater by lazy { AppUpdater(context, httpClient, settings) }

    val recipeRefresher: RecipeRefresher by lazy {
        RecipeRefresher(context, database.recipeDao(), importer, tagRefresher, settings)
    }

    /** Work that outlives a screen, such as downloading the on-device AI model. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val cardAssets = CardAssets { path -> context.assets.open(path).use { it.readBytes().decodeToString() } }

    val geminiCards: GeminiCardReader by lazy { GeminiCardReader(httpClient, cardAssets) }

    val onDeviceCards: NanoCardReader by lazy { NanoCardReader(cardAssets, appScope) }

    val cardReader: CardReader by lazy { CardReader(settings, geminiCards, onDeviceCards, TextCardReader(context)) }

    private companion object {
        const val FALLBACK_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36"
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as RecipeBoxApp).container

/** The installed version, shown in Settings and written into backups. */
object BuildConfigValues {
    fun versionName(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "unknown"
}
