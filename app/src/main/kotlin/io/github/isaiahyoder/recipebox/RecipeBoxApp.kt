package io.github.isaiahyoder.recipebox

import io.github.isaiahyoder.recipebox.util.runCatchingCancellable
import io.github.isaiahyoder.recipebox.diagnostics.StallWatchdog
import io.github.isaiahyoder.recipebox.repository.GroceryRepository
import io.github.isaiahyoder.recipebox.repository.LibraryRepository
import io.github.isaiahyoder.recipebox.repository.RecipeRepository
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
import io.github.isaiahyoder.recipebox.backup.BackupOperations
import io.github.isaiahyoder.recipebox.backup.DriveBackup
import io.github.isaiahyoder.recipebox.backup.PhotoRestorer
import io.github.isaiahyoder.recipebox.data.RecipeDatabase
import io.github.isaiahyoder.recipebox.importer.ImportQueue
import io.github.isaiahyoder.recipebox.importer.LinkSource
import io.github.isaiahyoder.recipebox.importer.WebPageSource
import io.github.isaiahyoder.recipebox.importer.PageFetcher
import io.github.isaiahyoder.recipebox.importer.RecipeImporter
import io.github.isaiahyoder.recipebox.importer.RecipeRefresher
import io.github.isaiahyoder.recipebox.importer.WebViewPageLoader
import io.github.isaiahyoder.recipebox.photos.PhotoStore
import io.github.isaiahyoder.recipebox.settings.AppSettings
import io.github.isaiahyoder.recipebox.tags.TagRefresher
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
            applyNewRules(container)
            // Recipes saved before search text or the ingredient index existed, or restored
            // from a backup, get them once.
            runCatchingCancellable { container.database.recipeDao().indexRecipes() }
                .onFailure { Log.w(TAG, "Filling search text and the ingredient index failed", it) }
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
    if (settings.ingredientIndexVersion < ContentVersions.INGREDIENT_INDEX) {
        // Emptied here and filled again by indexRecipes, right after.
        runCatchingCancellable { container.database.recipeDao().clearIngredientIndex() }
            .onSuccess { settings.ingredientIndexVersion = ContentVersions.INGREDIENT_INDEX }
            .onFailure { Log.w(TAG, "Clearing the ingredient index failed", it) }
    }
    if (settings.tagRulesVersion < ContentVersions.TAG_RULES) {
        runCatchingCancellable { container.tagRefresher.refreshAll() }
            .onSuccess { settings.tagRulesVersion = ContentVersions.TAG_RULES }
            .onFailure { Log.w(TAG, "Applying new tag rules failed", it) }
    }
}

private const val TAG = "RecipeBox"

/**
 * Creates the app's long-lived objects once and shares them.
 *
 * Each object is built lazily under a lock, and both the startup work in the
 * background and screens on the main thread build them. An initializer must
 * therefore never wait for the main thread: no WebView, no runBlocking, and
 * no withContext(Dispatchers.Main). Doing so froze a cold start from Share
 * until 0.5.1. Do such work later, in a method, not while building the object.
 *
 * Screens reach data through ViewModels and the repositories and services
 * here, never through the database or its DAOs directly.
 */
class AppContainer(context: Context) {
    val database: RecipeDatabase by lazy { RecipeDatabase.create(context) }

    /** Text resources for code without a Context. */
    val strings: AppStrings = ResourceStrings(context)

    val settings: AppSettings by lazy { AppSettings(context) }

    val tagRefresher: TagRefresher by lazy { TagRefresher(database) }

    /** Tag and category changes, each with the category update it causes. */
    val library: LibraryRepository by lazy { LibraryRepository(database, tagRefresher) }

    val groceries: GroceryRepository by lazy { GroceryRepository(database, settings) }

    /** Saving and deleting whole recipes with their tags and photo files. */
    val recipes: RecipeRepository by lazy { RecipeRepository(database, photos, tagRefresher) }

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

    /**
     * Where shared links are read from, in the order they're tried. A source
     * for videos or social posts goes before web pages, which take any link.
     */
    private val linkSources: List<LinkSource> by lazy { listOf(WebPageSource(pageFetcher, browserLoader, strings)) }

    val importer: RecipeImporter by lazy {
        RecipeImporter(dao = database.recipeDao(), sources = linkSources, recipes = recipes, photos = photos)
    }

    val importQueue: ImportQueue by lazy { ImportQueue(context, database.importJobDao(), importer) }

    private val pageFetcher: PageFetcher by lazy { PageFetcher(httpClient, userAgent, strings) }

    val photoRestorer: PhotoRestorer by lazy { PhotoRestorer(database.recipeDao(), photos, pageFetcher) }

    val backupManager: BackupManager by lazy {
        BackupManager(database, photos, settings, appVersion = BuildConfigValues.versionName(context), strings = strings)
    }

    val driveBackup: DriveBackup by lazy { DriveBackup(context, httpClient, backupManager, settings, photos) }

    /** Backups and restores that finish even when she leaves Settings. */
    val backupOperations: BackupOperations by lazy {
        BackupOperations(context, appScope, backupManager, driveBackup, settings)
    }

    /** How this build gets updates, from [BuildFlavor]. */
    val distribution: AppDistribution by lazy { BuildFlavor.distribution(context, httpClient, settings) }

    val recipeRefresher: RecipeRefresher by lazy {
        RecipeRefresher(context, database.recipeDao(), database.pageDao(), importer, tagRefresher, settings)
    }

    /** Work that outlives a screen, such as downloading the on-device AI model. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val cardAssets = CardAssets { path -> context.assets.open(path).use { it.readBytes().decodeToString() } }

    val geminiCards: GeminiCardReader by lazy { GeminiCardReader(httpClient, cardAssets, strings) }

    val onDeviceCards: NanoCardReader by lazy { NanoCardReader(cardAssets, appScope, strings) }

    /** Card readers in the order they're tried. Each build adds its own to the shared ones. */
    val cardReader: CardReader by lazy {
        CardReader(
            BuildFlavor.cardReaders(settings, geminiCards, strings, shared = listOf(onDeviceCards, TextCardReader(context))),
            strings,
        )
    }

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
