package io.github.isaiahyoder.recipebox.importer

import io.github.isaiahyoder.recipebox.AppStrings
import io.github.isaiahyoder.recipebox.R
import android.util.Log
import io.github.isaiahyoder.recipebox.model.RecipeDraft

/**
 * Reads recipes from web pages: downloads the page and reads its recipe,
 * falling back to a hidden browser view when the plain download is refused
 * or has no recipe data. It handles every link, so it's the last source.
 */
class WebPageSource(
    private val fetcher: PageFetcher,
    private val browserLoader: WebViewPageLoader,
    private val strings: AppStrings,
) : LinkSource {
    override fun handles(url: String): Boolean = true

    override suspend fun read(url: String, onStage: (ImportStage) -> Unit): SourceResult {
        onStage(ImportStage.DOWNLOADING)
        val noRecipeData = strings.get(R.string.import_no_recipe)
        var reason = noRecipeData
        var extracted: RecipeDraft? = null
        var pageLoaded = false
        when (val result = fetcher.fetch(url)) {
            is FetchResult.Page -> {
                pageLoaded = true
                extracted = RecipeExtractor.extract(result.html, result.finalUrl)
            }
            is FetchResult.Failed -> reason = result.reason
        }
        Log.i(TAG, "Download: ${describe(extracted)}; reason=$reason")

        if (extracted?.isComplete != true) {
            onStage(ImportStage.TRYING_BROWSER)
            val html = browserLoader.load(url)
            if (html != null) pageLoaded = true
            val fromBrowser = html?.let { RecipeExtractor.extract(it, url) }
            if (fromBrowser != null && (extracted == null || fromBrowser.isComplete)) extracted = fromBrowser
            if (html != null && extracted == null) reason = noRecipeData
            Log.i(TAG, "Browser view: html=${html?.length ?: "none"}, ${describe(fromBrowser)}")
        }
        val recipe = extracted?.takeIf { it.isComplete }
        // The recipe keeps the link she shared, not the page's address after redirects.
        return if (recipe != null) SourceResult.Found(recipe.copy(sourceUrl = url)) else SourceResult.NotFound(reason, retryable = !pageLoaded)
    }

    private fun describe(recipe: RecipeDraft?): String = recipe?.let {
        "title=${it.title.isNotBlank()}, ingredients=${it.ingredients.size}, steps=${it.steps.size}"
    } ?: "no recipe data"

    private companion object {
        const val TAG = "RecipeImport"
    }
}
