package io.github.isaiahyoder.recipebox.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

enum class ImportStatus {
    /** Waiting for its turn, or for its retry time. */
    PENDING,
    RUNNING,
    /** Saved as a new recipe. */
    DONE,
    /** The recipe was already in her library. */
    DUPLICATE,
    /** Needs her attention: retry, open the page, or remove. */
    FAILED,
}

/** One link waiting to become a recipe. */
@Entity(tableName = "import_jobs", indices = [Index("status")])
data class ImportJobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val status: ImportStatus = ImportStatus.PENDING,
    /** Automatic attempts so far; reset when she taps Retry. */
    val attempts: Int = 0,
    /** Earliest time to try again, in milliseconds since 1970; 0 means now. */
    val nextAttemptAt: Long = 0,
    val lastError: String? = null,
    val recipeId: Long? = null,
    /** The recipe title, once known, so the queue shows names instead of links. */
    val title: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Dao
interface ImportJobDao {
    @Query("SELECT * FROM import_jobs ORDER BY createdAt, id")
    fun observeAll(): Flow<List<ImportJobEntity>>

    @Insert
    suspend fun insert(job: ImportJobEntity): Long

    @Query("SELECT COUNT(*) FROM import_jobs WHERE url = :url AND status IN ('PENDING', 'RUNNING')")
    suspend fun countActive(url: String): Int

    @Query(
        """
        SELECT * FROM import_jobs WHERE status = 'PENDING' AND nextAttemptAt <= :now
        ORDER BY createdAt, id LIMIT 1
        """
    )
    suspend fun nextReady(now: Long): ImportJobEntity?

    /** Claims a job for this run; returns 0 if another run claimed it first. */
    @Query("UPDATE import_jobs SET status = 'RUNNING', updatedAt = :now WHERE id = :id AND status = 'PENDING'")
    suspend fun claim(id: Long, now: Long): Int

    @Query("SELECT MIN(nextAttemptAt) FROM import_jobs WHERE status = 'PENDING'")
    suspend fun earliestPending(): Long?

    /** Jobs left RUNNING by a run that was stopped go back in line. */
    @Query("UPDATE import_jobs SET status = 'PENDING' WHERE status = 'RUNNING'")
    suspend fun requeueInterrupted()

    @Query(
        """
        UPDATE import_jobs SET status = :status, attempts = :attempts, nextAttemptAt = :nextAttemptAt,
            lastError = :error, recipeId = :recipeId, title = :title, updatedAt = :now
        WHERE id = :id
        """
    )
    suspend fun finish(
        id: Long,
        status: ImportStatus,
        attempts: Int,
        nextAttemptAt: Long,
        error: String?,
        recipeId: Long?,
        title: String?,
        now: Long,
    )

    @Query(
        """
        UPDATE import_jobs SET status = 'PENDING', attempts = 0, nextAttemptAt = 0, lastError = NULL,
            updatedAt = :now
        WHERE id = :id AND status = 'FAILED'
        """
    )
    suspend fun retry(id: Long, now: Long)

    @Query(
        """
        UPDATE import_jobs SET status = 'PENDING', attempts = 0, nextAttemptAt = 0, lastError = NULL,
            updatedAt = :now
        WHERE status = 'FAILED'
        """
    )
    suspend fun retryAllFailed(now: Long)

    @Query("DELETE FROM import_jobs WHERE id = :id AND status != 'RUNNING'")
    suspend fun remove(id: Long)

    @Query("DELETE FROM import_jobs WHERE status IN ('DONE', 'DUPLICATE')")
    suspend fun clearFinished()

    @Query("DELETE FROM import_jobs WHERE status IN ('DONE', 'DUPLICATE') AND updatedAt < :before")
    suspend fun clearFinishedBefore(before: Long)
}
