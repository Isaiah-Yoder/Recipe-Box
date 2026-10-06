package io.github.isaiahyoder.recipebox

import android.content.Context
import io.github.isaiahyoder.recipebox.cards.GeminiCardReader
import io.github.isaiahyoder.recipebox.cards.RecipeCardReader
import io.github.isaiahyoder.recipebox.settings.AppSettings
import okhttp3.OkHttpClient

/**
 * What the Google Play build changes in the shared app. Google Play installs
 * updates, so the app has no updater and no install permission. Cards are
 * read on the phone; a cloud reader paid for by the app can be added here,
 * turned off until the app pays for it.
 */
object BuildFlavor {
    /** Play users don't bring their own Gemini key. */
    const val OWN_GEMINI_KEY = false

    @Suppress("UNUSED_PARAMETER")
    fun distribution(context: Context, httpClient: OkHttpClient, settings: AppSettings): AppDistribution = PlayDistribution

    @Suppress("UNUSED_PARAMETER")
    fun cardReaders(
        settings: AppSettings,
        gemini: GeminiCardReader,
        strings: AppStrings,
        shared: List<RecipeCardReader>,
    ): List<RecipeCardReader> =
        shared
}
