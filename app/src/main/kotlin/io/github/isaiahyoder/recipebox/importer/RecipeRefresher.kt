package io.github.isaiahyoder.recipebox.importer

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.isaiahyoder.recipebox.appContainer
import io.github.isaiahyoder.recipebox.data.RecipeDao
import io.github.isaiahyoder.recipebox.data.RecipeEntity
import io.github.isaiahyoder.recipebox.settings.AppSettings
import io.github.isaiahyoder.recipebox.settings.RefreshStatus
import io.github.isaiahyoder.recipebox.tags.AutoTagger
import io.github.isaiahyoder.recipebox.tags.TagRefresher
import io.github.isaiahyoder.recipebox.tags.toTaggable
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

/**
 * Reads every saved recipe's web page again, so recipes saved before an
 * update get its reading fixes, such as ingredient groups and full steps.
 *
 * Only what came from the page changes: title, times, servings, ingredients,
 * steps, and the site's own categories. Her notes, favorites, scale,
 * categories, tags she added or removed, photos she took, and card photos
 * stay. Recipes she edited are skipped unless she chooses otherwise, because
 * a refresh would replace her changes. Automatic tags are recomputed for
 * every recipe at the end.
 *
 * Pages are read one at a time with a pause, because sites refuse rapid
 * requests. The run continues in the background and survives the app closing.
 */
class RecipeRefresher(
    private val context: Context,
    private val dao: RecipeDao,
    private val importer: RecipeImporter,
    private val tags: TagRefresher,
    private val settings: AppSettings,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** How many recipes have a page to read again, and how many of those she edited. */
    suspend fun counts(): Pair<Int, Int> {
        val linked = dao.getAllRecipes().filter { it.sourceUrl != null }
        return linked.size to linked.count(::isEdited)
    }

    suspend fun start(includeEdited: Boolean) {
        val linked = dao.getAllRecipes().filter { it.sourceUrl != null }
        val chosen = if (includeEdited) linked else linked.filterNot(::isEdited)
        begin(RefreshStatus(running = true, pending = chosen.map { it.id }, total = chosen.size, skippedEdited = linked.size - chosen.size))
    }

    fun retryFailed() {
        val previous = settings.refreshStatus.value
        begin(RefreshStatus(running = true, pending = previous.failed, total = previous.failed.size, skippedEdited = previous.skippedEdited))
    }

    fun stop() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        val status = settings.refreshStatus.value
        settings.setRefreshStatus(status.copy(running = false, pending = emptyList(), finishedAt = clock()))
    }

    private fun begin(status: RefreshStatus) {
        settings.setRefreshStatus(status)
        schedule(context, continuing = false)
    }

    /** Works through the pending recipes until [deadline]. Returns true when the run is finished. */
    suspend fun runBatch(deadline: Long): Boolean {
        while (true) {
            val id = settings.refreshStatus.value.pending.firstOrNull() ?: break
            if (clock() > deadline) return false
            val outcome = refreshOne(id)
            val status = settings.refreshStatus.value
            settings.setRefreshStatus(
                status.copy(
                    pending = status.pending - id,
                    updated = status.updated + if (outcome == Outcome.UPDATED) 1 else 0,
                    failed = (status.failed - id) + if (outcome == Outcome.FAILED) listOf(id) else emptyList(),
                )
            )
            if (settings.refreshStatus.value.pending.isNotEmpty()) delay(PAUSE_MS)
        }
        // Recipes without a page, such as recipe cards, get the latest tag rules too.
        tags.refreshAll()
        settings.setRefreshStatus(settings.refreshStatus.value.copy(running = false, finishedAt = clock()))
        return true
    }

    private enum class Outcome { UPDATED, FAILED, GONE }

    private suspend fun refreshOne(id: Long): Outcome {
        val recipe = dao.getRecipe(id) ?: return Outcome.GONE
        val url = recipe.sourceUrl ?: return Outcome.GONE
        val page = runCatching { importer.load(url).recipe }.getOrNull() ?: return Outcome.FAILED
        val refreshed = recipe.refreshedWith(page)
        dao.update(refreshed)
        dao.replaceAutoTags(id, AutoTagger.tags(refreshed.toTaggable()))
        return Outcome.UPDATED
    }

    companion object {
        const val WORK_NAME = "recipe-refresh"
        private const val PAUSE_MS = 4_000L

        /** True when she saved changes in the editor after importing it. */
        fun isEdited(recipe: RecipeEntity): Boolean = recipe.updatedAt > recipe.createdAt

        fun schedule(context: Context, continuing: Boolean) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                if (continuing) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<RecipeRefreshWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build(),
            )
        }
    }
}

/**
 * Replaces what came from the page and keeps everything that's hers. The
 * edit time stays, so the recipe keeps its place in the library.
 */
fun RecipeEntity.refreshedWith(page: ExtractedRecipe): RecipeEntity = copy(
    title = page.title.ifBlank { title },
    description = page.description ?: description,
    siteName = page.siteName ?: siteName,
    yieldText = page.yieldText,
    servings = page.servings ?: servings,
    prepMinutes = page.prepMinutes,
    cookMinutes = page.cookMinutes,
    totalMinutes = page.totalMinutes,
    ingredients = page.ingredients,
    steps = page.steps,
    siteCategories = page.categories,
    siteCuisines = page.cuisines,
    siteKeywords = page.keywords,
    rawJsonLd = page.rawJsonLd ?: rawJsonLd,
    imageUrl = if (imageIsOwn) imageUrl else page.imageUrl ?: imageUrl,
)

/**
 * Refreshes recipes in batches of about eight minutes, because Android stops
 * a background job after ten. Each batch schedules the next until all are done.
 */
class RecipeRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val refresher = applicationContext.appContainer.recipeRefresher
        val deadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(8)
        if (!refresher.runBatch(deadline)) RecipeRefresher.schedule(applicationContext, continuing = true)
        return Result.success()
    }
}
