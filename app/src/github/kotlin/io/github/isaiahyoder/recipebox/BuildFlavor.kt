package io.github.isaiahyoder.recipebox

import android.content.Context
import io.github.isaiahyoder.recipebox.cards.GeminiCardReader
import io.github.isaiahyoder.recipebox.cards.GeminiKeyCardReader
import io.github.isaiahyoder.recipebox.cards.RecipeCardReader
import io.github.isaiahyoder.recipebox.settings.AppSettings
import io.github.isaiahyoder.recipebox.update.AppUpdater
import okhttp3.OkHttpClient

/**
 * What the GitHub build adds to the shared app: updates from the GitHub
 * releases, installed by the app itself, and cards read with her own Gemini key.
 */
object BuildFlavor {
    /** Whether Settings offers a Gemini key of her own for reading cards. */
    const val OWN_GEMINI_KEY = true

    fun distribution(context: Context, httpClient: OkHttpClient, settings: AppSettings): AppDistribution =
        GitHubDistribution(AppUpdater(context, httpClient, settings))

    /** Gemini with her key reads handwriting best, so it goes first; the shared readers follow. */
    fun cardReaders(settings: AppSettings, gemini: GeminiCardReader, shared: List<RecipeCardReader>): List<RecipeCardReader> =
        listOf(GeminiKeyCardReader(settings, gemini)) + shared
}
