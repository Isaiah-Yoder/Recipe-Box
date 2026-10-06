package io.github.isaiahyoder.recipebox.importer

import io.github.isaiahyoder.recipebox.model.RecipeDraft

/** What an import is doing, shown while she waits. */
enum class ImportStage { DOWNLOADING, TRYING_BROWSER, SAVING }

/** A source's recipe, or why there isn't one. */
sealed interface SourceResult {
    data class Found(val draft: RecipeDraft) : SourceResult

    /**
     * No recipe was read. [retryable] is true when the source never loaded,
     * such as a refused or failed download, so trying later can help. It's
     * false when the source loaded but has no recipe.
     */
    data class NotFound(val reason: String, val retryable: Boolean) : SourceResult
}

/**
 * A kind of link recipes are read from, such as recipe web pages. A shared
 * link goes to the first source that handles it, so a source for videos or
 * social posts goes before the web page source, which handles every link.
 */
interface LinkSource {
    fun handles(url: String): Boolean

    suspend fun read(url: String, onStage: (ImportStage) -> Unit = {}): SourceResult
}
