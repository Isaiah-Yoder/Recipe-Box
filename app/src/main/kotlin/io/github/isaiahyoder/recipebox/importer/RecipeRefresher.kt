package io.github.isaiahyoder.recipebox.importer

import io.github.isaiahyoder.recipebox.util.runCatchingCancellable
import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.isaiahyoder.recipebox.appContainer
import io.github.isaiahyoder.recipebox.data.EditedField
import io.github.isaiahyoder.recipebox.data.PageDao
import io.github.isaiahyoder.recipebox.data.RecipeDao
import io.github.isaiahyoder.recipebox.data.RecipeEntity
import io.github.isaiahyoder.recipebox.data.RecipePageEntity
import io.github.isaiahyoder.recipebox.settings.AppSettings
import io.github.isaiahyoder.recipebox.settings.RefreshStatus
import io.github.isaiahyoder.recipebox.tags.TagRefresher
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

/**
 * Reads saved recipes' web pages again, so recipes saved before an update
 * get its reading fixes, such as ingredient groups and full steps.
 *
 * Only what came from the page changes, and only the parts she hasn't
 * edited: if she fixed the ingredients, they stay, and the steps can still
 * update. Her notes, favorites, scale, categories, tags she added or
 * removed, photos she took, and card photos always stay.
 *
 * Each read keeps a small copy of the page, so the next reading fix runs on
 * the phone in seconds instead of downloading every page again. Downloads
 * go one at a time with a pause, because sites refuse rapid requests, and
 * the run continues in the background.
 */
class RecipeRefresher(
    private val context: Context,
    private val dao: RecipeDao,
    private val pages: PageDao,
    private val importer: RecipeImporter,
    private val tags: TagRefresher,
    private val settings: AppSettings,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** How many recipes have a page to read again, and how many of those she edited. */
    suspend fun counts(): Pair<Int, Int> {
        val linked = dao.getAllRecipes().filter { it.sourceUrl != null }
        return linked.size to linked.count { it.editedFields.isNotEmpty() }
    }

    /** Downloads every linked recipe's page again, on any connection. */
    suspend fun start() {
        val linked = dao.getAllRecipes().filter { it.sourceUrl != null }.map { it.id }
        begin(RefreshStatus(running = true, pending = linked, total = linked.size))
    }

    fun retryFailed() {
        val previous = settings.refreshStatus.value
        begin(RefreshStatus(running = true, pending = previous.failed, total = previous.failed.size, wifiOnly = previous.wifiOnly))
    }

    fun stop() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        val status = settings.refreshStatus.value
        settings.setRefreshStatus(status.copy(running = false, pending = emptyList(), finishedAt = clock()))
    }

    private fun begin(status: RefreshStatus) {
        settings.setRefreshStatus(status)
        schedule(context, continuing = false, wifiOnly = status.wifiOnly)
    }

    /**
     * Applies the latest reading rules after an app update: recipes with a
     * saved page are read again right away on the phone, and the rest are
     * downloaded in the background the next time the phone is on Wi-Fi.
     */
    suspend fun upgradeReading() {
        reparseSaved()
        val missing = dao.getAllRecipes().filter { it.sourceUrl != null && pages.getPage(it.id) == null }.map { it.id }
        if (missing.isNotEmpty() && !settings.refreshStatus.value.running) {
            begin(RefreshStatus(running = true, pending = missing, total = missing.size, wifiOnly = true))
        }
    }

    /** Reads every saved page again with the current rules, without downloading. Returns how many changed. */
    suspend fun reparseSaved(): Int {
        var changed = 0
        for (recipe in dao.getAllRecipes()) {
            val url = recipe.sourceUrl ?: continue
            val page = pages.getPage(recipe.id) ?: continue
            val extracted = RecipeExtractor.extract(page.html, url)?.takeIf { it.isComplete } ?: continue
            val refreshed = recipe.refreshedWith(extracted)
            if (refreshed != recipe) {
                dao.update(refreshed)
                changed++
            }
        }
        tags.refreshAll()
        return changed
    }

    /**
     * Drops her edits to a recipe and uses what its page says, read from the
     * saved copy when there is one. Returns false when the page couldn't be read.
     */
    suspend fun useWebsiteVersion(id: Long): Boolean {
        val recipe = dao.getRecipe(id) ?: return false
        val url = recipe.sourceUrl ?: return false
        val unedited = recipe.copy(editedFields = emptyList())
        val saved = pages.getPage(id)?.let { RecipeExtractor.extract(it.html, url) }?.takeIf { it.isComplete }
        val page = saved ?: runCatchingCancellable { importer.load(url).recipe }.getOrNull() ?: return false
        apply(unedited, page)
        return true
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
        val page = runCatchingCancellable { importer.load(url).recipe }.getOrNull() ?: return Outcome.FAILED
        apply(recipe, page)
        return Outcome.UPDATED
    }

    private suspend fun apply(recipe: RecipeEntity, page: ExtractedRecipe) {
        val refreshed = recipe.refreshedWith(page)
        dao.update(refreshed)
        page.pageSnapshot?.let { pages.savePage(RecipePageEntity(recipe.id, it, clock())) }
        tags.refreshRecipe(refreshed)
    }

    companion object {
        const val WORK_NAME = "recipe-refresh"
        private const val PAUSE_MS = 4_000L

        fun schedule(context: Context, continuing: Boolean, wifiOnly: Boolean) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                if (continuing) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<RecipeRefreshWorker>()
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                            .build()
                    )
                    .build(),
            )
        }
    }
}

/**
 * Replaces what came from the page, except the parts she edited, and keeps
 * everything that's hers. The edit time stays, so the recipe keeps its place
 * in the library.
 */
fun RecipeEntity.refreshedWith(page: ExtractedRecipe): RecipeEntity {
    val edited = editedFields.toSet()
    return copy(
        title = if (EditedField.TITLE in edited) title else page.title.ifBlank { title },
        description = page.description ?: description,
        siteName = page.siteName ?: siteName,
        yieldText = if (EditedField.SERVINGS in edited) yieldText else page.yieldText,
        servings = if (EditedField.SERVINGS in edited) servings else page.servings ?: servings,
        prepMinutes = if (EditedField.TIMES in edited) prepMinutes else page.prepMinutes,
        cookMinutes = if (EditedField.TIMES in edited) cookMinutes else page.cookMinutes,
        totalMinutes = if (EditedField.TIMES in edited) totalMinutes else page.totalMinutes,
        ingredients = if (EditedField.INGREDIENTS in edited) ingredients else page.ingredients,
        steps = if (EditedField.STEPS in edited) steps else page.steps,
        siteCategories = page.categories,
        siteCuisines = page.cuisines,
        siteKeywords = page.keywords,
        rawJsonLd = page.rawJsonLd ?: rawJsonLd,
        imageUrl = if (imageIsOwn) imageUrl else page.imageUrl ?: imageUrl,
    )
}

/**
 * Refreshes recipes in batches of about eight minutes, because Android stops
 * a background job after ten. Each batch schedules the next until all are done.
 */
class RecipeRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = applicationContext.appContainer
        val deadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(8)
        if (!container.recipeRefresher.runBatch(deadline)) {
            RecipeRefresher.schedule(applicationContext, continuing = true, wifiOnly = container.settings.refreshStatus.value.wifiOnly)
        }
        return Result.success()
    }
}
