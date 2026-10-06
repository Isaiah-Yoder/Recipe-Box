package io.github.isaiahyoder.recipebox.importer

import io.github.isaiahyoder.recipebox.data.RecipeDao
import io.github.isaiahyoder.recipebox.model.RecipeDraft
import io.github.isaiahyoder.recipebox.photos.PhotoStore
import io.github.isaiahyoder.recipebox.repository.AddResult
import io.github.isaiahyoder.recipebox.repository.RecipeRepository

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

/**
 * Turns a shared link into a saved recipe: the first of [sources] that
 * handles the link reads it, and [recipes] saves the draft like every other.
 */
class RecipeImporter(
    private val dao: RecipeDao,
    private val sources: List<LinkSource>,
    private val recipes: RecipeRepository,
    private val photos: PhotoStore,
) {
    suspend fun import(sharedText: String, onStage: (ImportStage) -> Unit): ImportOutcome {
        val url = Links.findUrl(sharedText)?.let(Links::normalize) ?: return ImportOutcome.NoLink
        // Checked before downloading, so a link she already saved costs nothing.
        dao.findIdBySourceUrl(url)?.let { return ImportOutcome.AlreadySaved(it, dao.getRecipe(it)?.title) }

        val draft = when (val result = read(url, onStage)) {
            is SourceResult.Found -> result.draft
            is SourceResult.NotFound -> return ImportOutcome.NotFound(url, result.reason, result.retryable)
        }
        onStage(ImportStage.SAVING)
        val id = when (val added = recipes.add(draft)) {
            is AddResult.AlreadySaved -> return ImportOutcome.AlreadySaved(added.recipeId, dao.getRecipe(added.recipeId)?.title)
            is AddResult.Saved -> added.recipeId
        }
        // A missing photo doesn't stop the import; the recipe shows a placeholder.
        draft.imageUrl?.let { imageUrl ->
            photos.downloadCover(imageUrl, id)?.let { dao.setImageFile(id, it) }
        }
        return ImportOutcome.Saved(id, draft.title)
    }

    /** Reads [url] with the first source that handles it. */
    suspend fun read(url: String, onStage: (ImportStage) -> Unit = {}): SourceResult =
        sources.first { it.handles(url) }.read(url, onStage)

    /** The recipe at [url], or null when it can't be read. */
    suspend fun readDraft(url: String): RecipeDraft? = (read(url) as? SourceResult.Found)?.draft
}
