package io.github.isaiahyoder.recipebox.importer

import io.github.isaiahyoder.recipebox.data.RecipeDao
import io.github.isaiahyoder.recipebox.data.RecipeEntity
import io.github.isaiahyoder.recipebox.photos.PhotoStore
import io.github.isaiahyoder.recipebox.tags.AutoTagger
import io.github.isaiahyoder.recipebox.tags.TaggableRecipe

enum class ImportStage { DOWNLOADING, TRYING_BROWSER, SAVING }

sealed interface ImportOutcome {
    data class Saved(val recipeId: Long) : ImportOutcome
    data class AlreadySaved(val recipeId: Long) : ImportOutcome
    data object NoLink : ImportOutcome
    data class NotFound(val url: String, val reason: String) : ImportOutcome
}

/** Turns a shared link into a saved recipe. */
class RecipeImporter(
    private val dao: RecipeDao,
    private val fetcher: PageFetcher,
    private val browserLoader: WebViewPageLoader,
    private val photos: PhotoStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun import(sharedText: String, onStage: (ImportStage) -> Unit): ImportOutcome {
        val url = Links.findUrl(sharedText)?.let(Links::normalize) ?: return ImportOutcome.NoLink
        dao.findIdBySourceUrl(url)?.let { return ImportOutcome.AlreadySaved(it) }

        onStage(ImportStage.DOWNLOADING)
        var reason = "The page has no recipe data the app can read."
        var extracted: ExtractedRecipe? = null
        when (val result = fetcher.fetch(url)) {
            is FetchResult.Page -> extracted = RecipeExtractor.extract(result.html, result.finalUrl)
            is FetchResult.Failed -> reason = result.reason
        }

        if (extracted?.isComplete != true) {
            onStage(ImportStage.TRYING_BROWSER)
            val html = browserLoader.load(url)
            val fromBrowser = html?.let { RecipeExtractor.extract(it, url) }
            if (fromBrowser != null && (extracted == null || fromBrowser.isComplete)) extracted = fromBrowser
            if (html == null && extracted == null) reason = "The page didn't load, or it has no recipe data."
        }

        val recipe = extracted?.takeIf { it.isComplete }
            ?: return ImportOutcome.NotFound(url, reason)

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
        dao.replaceAutoTags(id, AutoTagger.tags(recipe.toTaggable()))
        // A missing photo doesn't stop the import; the recipe shows a placeholder.
        recipe.imageUrl?.let { imageUrl ->
            photos.downloadCover(imageUrl, id)?.let { dao.setImageFile(id, it) }
        }
        return ImportOutcome.Saved(id)
    }

    private fun ExtractedRecipe.toTaggable() = TaggableRecipe(
        title = title,
        ingredients = ingredients.filterNot { it.isHeader }.map { it.text },
        steps = steps.filterNot { it.isHeader }.map { it.text },
        totalMinutes = totalMinutes,
        siteCategories = categories,
        siteCuisines = cuisines,
        siteKeywords = keywords,
    )
}
