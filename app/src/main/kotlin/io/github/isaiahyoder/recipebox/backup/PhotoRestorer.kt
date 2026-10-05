package io.github.isaiahyoder.recipebox.backup

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
import io.github.isaiahyoder.recipebox.importer.FetchResult
import io.github.isaiahyoder.recipebox.importer.PageFetcher
import io.github.isaiahyoder.recipebox.importer.RecipeExtractor
import io.github.isaiahyoder.recipebox.photos.PhotoStore
import kotlinx.coroutines.delay

/**
 * Downloads cover photos that are missing, such as after a restore. Recipes
 * are usable meanwhile and show a placeholder. Downloads run one at a time
 * with a pause, because recipe sites refuse rapid requests.
 */
class PhotoRestorer(
    private val dao: RecipeDao,
    private val photos: PhotoStore,
    private val fetcher: PageFetcher,
) {
    /**
     * Clears the photo of any recipe whose downloaded cover file is gone, so
     * it downloads again. Returns how many were found.
     */
    suspend fun forgetMissingFiles(): Int {
        var missing = 0
        for (recipe in dao.recipesWithCover()) {
            if (photos.existing(recipe.imageFile) == null) {
                dao.setImageFile(recipe.id, null)
                missing++
            }
        }
        return missing
    }

    /** Returns how many photos are still missing afterward. */
    suspend fun downloadMissing(): Int {
        var first = true
        for (recipe in dao.recipesMissingCover()) {
            if (!first) delay(PAUSE_MS)
            first = false
            val saved = recipe.imageUrl?.let { photos.downloadCover(it, recipe.id) }
            if (saved != null) {
                dao.setImageFile(recipe.id, saved)
                continue
            }
            // The saved address no longer works: read the page again for its current photo.
            val page = recipe.sourceUrl?.let { fetcher.fetch(it) } as? FetchResult.Page ?: continue
            val newUrl = RecipeExtractor.extract(page.html, page.finalUrl)?.imageUrl ?: continue
            photos.downloadCover(newUrl, recipe.id)?.let { dao.setImage(recipe.id, newUrl, it) }
        }
        return dao.recipesMissingCover().size
    }

    companion object {
        private const val PAUSE_MS = 3_000L
        const val WORK_NAME = "photo-restore"

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<PhotoRestoreWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build(),
            )
        }
    }
}

class PhotoRestoreWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        applicationContext.appContainer.photoRestorer.downloadMissing()
        return Result.success()
    }
}
