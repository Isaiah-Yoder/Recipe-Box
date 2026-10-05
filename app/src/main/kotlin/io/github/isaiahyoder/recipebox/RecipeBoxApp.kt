package io.github.isaiahyoder.recipebox

import io.github.isaiahyoder.recipebox.util.runCatchingCancellable
import io.github.isaiahyoder.recipebox.diagnostics.StallWatchdog
import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import android.webkit.WebView
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
        StallWatchdog(BuildConfigValues.versionName(this), container.settings::recordStall).start()
        // A cover photo file can go missing; download it again in the background.
        CoroutineScope(Dispatchers.IO).launch {
            if (container.photoRestorer.forgetMissingFiles() > 0) PhotoRestorer.schedule(this@RecipeBoxApp)
            // Card photos from a scan she didn't save.
            container.photos.deleteUnusedCardPhotos(container.database.recipeDao().allCardPhotos().flatMap { it.cardPhotos }.toSet())
            // WorkManager's schedule isn't restored with the app's data on a new phone,
            // so daily Drive backups are scheduled again whenever they're on.
            if (container.settings.driveStatus.value.enabled) container.driveBackup.schedule()
            // Recipes saved before search text existed get it once.
            runCatchingCancellable { container.database.recipeDao().indexRecipes() }
                .onFailure { Log.w(TAG, "Filling search text failed", it) }
            applyNewRules(container)
        }
    }
}

/**
 * Applies tag and reading rules that changed in an app update to the saved
 * recipes, once. A version is recorded only after its work succeeds, so work
 * that failed runs again at the next start.
 */
private suspend fun applyNewRules(container: AppContainer) {
    val settings = container.settings
    if (settings.readingVersion < ContentVersions.READING) {
        runCatchingCancellable { container.recipeRefresher.upgradeReading() }
            .onSuccess {
                settings.readingVersion = ContentVersions.READING
                // Reading again recomputes tags too.
                settings.tagRulesVersion = ContentVersions.TAG_RULES
            }
            .onFailure { Log.w(TAG, "Reading saved recipes again failed", it) }
    }
    if (settings.tagRulesVersion < ContentVersions.TAG_RULES) {
        runCatchingCancellable { container.tagRefresher.refreshAll() }
            .onSuccess { settings.tagRulesVersion = ContentVersions.TAG_RULES }
            .onFailure { Log.w(TAG, "Applying new tag rules failed", it) }
    }
}

private const val TAG = "RecipeBox"

/** Creates the app's long-lived objects once and shares them. */
class AppContainer(context: Context) {
    val database: RecipeDatabase by lazy { RecipeDatabase.create(context) }

    val settings: AppSettings by lazy { AppSettings(context) }

    val tagRefresher: TagRefresher by lazy { TagRefresher(database) }

    /**
     * A browser identity like the phone's own Chrome, so sites see an ordinary
     * visitor. It's built from the installed WebView's version instead of asking
     * WebView, which waits for the main thread and froze a cold start from Share.
     */
    private val userAgent: String by lazy {
        val chrome = runCatching { WebView.getCurrentWebViewPackage()?.versionName }.getOrNull()
            ?.substringBefore(' ')?.takeIf { it.isNotBlank() } ?: FALLBACK_CHROME_VERSION
        "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}; ${Build.MODEL}) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chrome Mobile Safari/537.36"
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val browserLoader: WebViewPageLoader by lazy { WebViewPageLoader(context) }

    val photos: PhotoStore by lazy {
        PhotoStore(context, httpClient, userAgent, browserImage = { url, maxEdge -> browserLoader.loadImage(url, maxEdge) })
    }

    val importer: RecipeImporter by lazy {
        RecipeImporter(
            dao = database.recipeDao(),
            fetcher = pageFetcher,
            browserLoader = browserLoader,
            photos = photos,
            tags = tagRefresher,
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
        const val FALLBACK_CHROME_VERSION = "140.0.0.0"
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as RecipeBoxApp).container

/** The installed version, shown in Settings and written into backups. */
object BuildConfigValues {
    fun versionName(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "unknown"
}
