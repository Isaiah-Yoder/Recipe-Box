package io.github.isaiahyoder.recipebox

import android.app.Application
import android.content.Context
import android.webkit.WebSettings
import io.github.isaiahyoder.recipebox.data.RecipeDatabase
import io.github.isaiahyoder.recipebox.importer.PageFetcher
import io.github.isaiahyoder.recipebox.importer.RecipeImporter
import io.github.isaiahyoder.recipebox.importer.WebViewPageLoader
import io.github.isaiahyoder.recipebox.photos.PhotoStore
import io.github.isaiahyoder.recipebox.settings.AppSettings
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class RecipeBoxApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Creates the app's long-lived objects once and shares them. */
class AppContainer(context: Context) {
    val database: RecipeDatabase by lazy { RecipeDatabase.create(context) }

    val settings: AppSettings by lazy { AppSettings(context) }

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
            fetcher = PageFetcher(httpClient, userAgent),
            browserLoader = WebViewPageLoader(context),
            photos = photos,
        )
    }

    private companion object {
        const val FALLBACK_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36"
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as RecipeBoxApp).container
