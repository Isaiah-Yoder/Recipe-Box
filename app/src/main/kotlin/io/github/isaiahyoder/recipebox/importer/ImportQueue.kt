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
import io.github.isaiahyoder.recipebox.data.ImportJobDao
import io.github.isaiahyoder.recipebox.data.ImportJobEntity
import io.github.isaiahyoder.recipebox.data.ImportStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/**
 * Imports links one at a time in the background, so she can add several and
 * keep going. Jobs live in the database, so the queue survives the app
 * closing or the phone restarting.
 *
 * A refused or failed download is retried automatically after 1, 5, and 20
 * minutes. A page that loaded but has no recipe needs her decision instead.
 */
class ImportQueue(
    private val context: Context,
    private val jobs: ImportJobDao,
    private val importer: RecipeImporter,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val processing = Mutex()

    /** Adds links to the queue and returns how many were new. */
    suspend fun enqueue(urls: List<String>): Int {
        var added = 0
        for (url in urls) {
            if (jobs.countActive(url) > 0) continue
            val now = clock()
            jobs.insert(ImportJobEntity(url = url, createdAt = now, updatedAt = now))
            added++
        }
        if (added > 0) scheduleNow()
        return added
    }

    suspend fun retry(jobId: Long) {
        jobs.retry(jobId, clock())
        scheduleNow()
    }

    suspend fun retryAllFailed() {
        jobs.retryAllFailed(clock())
        scheduleNow()
    }

    suspend fun remove(jobId: Long) = jobs.remove(jobId)

    suspend fun clearFinished() = jobs.clearFinished()

    /** Imports every job that is due, one at a time. Called by [ImportQueueWorker]. */
    suspend fun processDueJobs() = processing.withLock {
        jobs.requeueInterrupted()
        jobs.clearFinishedBefore(clock() - TimeUnit.DAYS.toMillis(7))
        var first = true
        while (true) {
            val job = jobs.nextReady(clock()) ?: break
            if (jobs.claim(job.id, clock()) == 0) continue
            // A pause between pages keeps sites from refusing rapid requests.
            if (!first) delay(PAUSE_BETWEEN_JOBS_MS)
            first = false
            record(job, importer.import(job.url) {})
        }
        scheduleRetry()
    }

    private suspend fun record(job: ImportJobEntity, outcome: ImportOutcome) {
        val now = clock()
        when (outcome) {
            is ImportOutcome.Saved ->
                jobs.finish(job.id, ImportStatus.DONE, job.attempts, 0, null, outcome.recipeId, outcome.title, now)
            is ImportOutcome.AlreadySaved ->
                jobs.finish(job.id, ImportStatus.DUPLICATE, job.attempts, 0, null, outcome.recipeId, outcome.title, now)
            ImportOutcome.NoLink ->
                jobs.finish(job.id, ImportStatus.FAILED, job.attempts, 0, "This isn't a web link.", null, null, now)
            is ImportOutcome.NotFound -> {
                val attempts = job.attempts + 1
                val delayMs = RETRY_DELAYS_MS.getOrNull(attempts - 1)
                if (outcome.retryable && delayMs != null) {
                    jobs.finish(job.id, ImportStatus.PENDING, attempts, now + delayMs, outcome.reason, null, null, now)
                } else {
                    jobs.finish(job.id, ImportStatus.FAILED, attempts, 0, outcome.reason, null, null, now)
                }
            }
        }
    }

    private fun scheduleNow() {
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NOW,
            // Runs after any import already in progress, so new links are never skipped.
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<ImportQueueWorker>().setConstraints(networkConstraint).build(),
        )
    }

    private suspend fun scheduleRetry() {
        val next = jobs.earliestPending() ?: return
        val wait = (next - clock()).coerceAtLeast(0)
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_RETRY,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ImportQueueWorker>()
                .setConstraints(networkConstraint)
                .setInitialDelay(wait, TimeUnit.MILLISECONDS)
                .build(),
        )
    }

    private val networkConstraint =
        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    companion object {
        const val PAUSE_BETWEEN_JOBS_MS = 4_000L
        val RETRY_DELAYS_MS = listOf(1L, 5L, 20L).map { TimeUnit.MINUTES.toMillis(it) }
        private const val WORK_NOW = "import-queue"
        private const val WORK_RETRY = "import-queue-retry"
    }
}

class ImportQueueWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        applicationContext.appContainer.importQueue.processDueJobs()
        return Result.success()
    }
}
