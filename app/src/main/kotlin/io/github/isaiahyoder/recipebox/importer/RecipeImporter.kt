package io.github.isaiahyoder.recipebox.importer

import android.util.Log
import io.github.isaiahyoder.recipebox.data.PageDao
import io.github.isaiahyoder.recipebox.data.RecipeDao
import io.github.isaiahyoder.recipebox.data.RecipeEntity
import io.github.isaiahyoder.recipebox.data.RecipePageEntity
import io.github.isaiahyoder.recipebox.photos.PhotoStore
import io.github.isaiahyoder.recipebox.tags.TagRefresher

enum class ImportStage { DOWNLOADING, TRYING_BROWSER, SAVING }

sealed interface ImportOutcome {
    data class Saved(val recipeId: Long, val title: String) : ImportOutcome
    data class AlreadySaved(val recipeId: Long, val title: String?) : ImportOutcome
    data object NoLink : ImportOutcome

    /**
     * No recipe was saved. [retryable] is true when the page never loaded,
     * such as a refused or failed download, so trying later can help. It is
     * false when the page loaded but has no recipe data.
     */
    data class NotFound(val url: String, val reason: String, val retryable: Boolean) : ImportOutcome
}

/** A page's recipe, or why there isn't one. [pageLoaded] is false when the page never loaded. */
data class PageLoad(val recipe: ExtractedRecipe?, val reason: String, val pageLoaded: Boolean)

/** Turns a shared link into a saved recipe. */
class RecipeImporter(
    private val dao: RecipeDao,
    private val pages: PageDao,
    private val fetcher: PageFetcher,
    private val browserLoader: WebViewPageLoader,
    private val photos: PhotoStore,
    private val tags: TagRefresher,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun import(sharedText: String, onStage: (ImportStage) -> Unit): ImportOutcome {
        val url = Links.findUrl(sharedText)?.let(Links::normalize) ?: return ImportOutcome.NoLink
        dao.findIdBySourceUrl(url)?.let { return ImportOutcome.AlreadySaved(it, dao.getRecipe(it)?.title) }

        val loaded = load(url, onStage)
        val recipe = loaded.recipe ?: return ImportOutcome.NotFound(url, loaded.reason, retryable = !loaded.pageLoaded)

        onStage(ImportStage.SAVING)
        val now = clock()
        val id = dao.insert(
            RecipeEntity(
                title = recipe.title,
                sourceUrl = url,
                siteName = recipe.siteName,
                description = recipe.description,
                imageUrl = recipe.imageUrl,
                yieldText = recipe.yieldText,
                servings = recipe.servings,
                prepMinutes = recipe.prepMinutes,
                cookMinutes = recipe.cookMinutes,
                totalMinutes = recipe.totalMinutes,
                ingredients = recipe.ingredients,
                steps = recipe.steps,
                siteCategories = recipe.categories,
                siteCuisines = recipe.cuisines,
                siteKeywords = recipe.keywords,
                rawJsonLd = recipe.rawJsonLd,
                createdAt = now,
                updatedAt = now,
            )
        )
        recipe.pageSnapshot?.let { pages.savePage(RecipePageEntity(id, it, now)) }
        dao.getRecipe(id)?.let { tags.refreshRecipe(it) }
        // A missing photo doesn't stop the import; the recipe shows a placeholder.
        recipe.imageUrl?.let { imageUrl ->
            photos.downloadCover(imageUrl, id)?.let { dao.setImageFile(id, it) }
        }
        return ImportOutcome.Saved(id, recipe.title)
    }

    /**
     * Downloads [url] and reads its recipe, falling back to a hidden browser
     * view when the plain download is refused or has no recipe data.
     */
    suspend fun load(url: String, onStage: (ImportStage) -> Unit = {}): PageLoad {
        onStage(ImportStage.DOWNLOADING)
        var reason = NO_RECIPE_DATA
        var extracted: ExtractedRecipe? = null
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
            if (html != null && extracted == null) reason = NO_RECIPE_DATA
            Log.i(TAG, "Browser view: html=${html?.length ?: "none"}, ${describe(fromBrowser)}")
        }
        return PageLoad(extracted?.takeIf { it.isComplete }, reason, pageLoaded)
    }

    private fun describe(recipe: ExtractedRecipe?): String = recipe?.let {
        "title=${it.title.isNotBlank()}, ingredients=${it.ingredients.size}, steps=${it.steps.size}"
    } ?: "no recipe data"

    private companion object {
        const val TAG = "RecipeImport"
        const val NO_RECIPE_DATA = "The page loaded, but it doesn't have a recipe Recipe Box can read."
    }
}
