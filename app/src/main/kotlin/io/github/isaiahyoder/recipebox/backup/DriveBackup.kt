package io.github.isaiahyoder.recipebox.backup

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import io.github.isaiahyoder.recipebox.appContainer
import io.github.isaiahyoder.recipebox.photos.PhotoStore
import io.github.isaiahyoder.recipebox.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

sealed interface DriveAuth {
    data class Token(val value: String) : DriveAuth
    /** Google needs her to choose an account or approve access; launch [intent] from a screen. */
    data class NeedsConsent(val intent: PendingIntent) : DriveAuth
}

sealed interface DriveBackupResult {
    data object Uploaded : DriveBackupResult
    data object Unchanged : DriveBackupResult
    data class Failed(val message: String) : DriveBackupResult
}

/**
 * Backs up to a "Recipe Box backups" folder in her Google Drive.
 *
 * Each backup is a small file with every recipe and list. Her own photos are
 * uploaded once to a "photos" folder and shared by every backup. The 10 most
 * recent backups are kept.
 */
class DriveBackup(
    private val context: Context,
    private val client: OkHttpClient,
    private val backups: BackupManager,
    private val settings: AppSettings,
    private val photos: PhotoStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val running = Mutex()

    private val request = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
        .build()

    suspend fun authorize(): DriveAuth {
        val result = Identity.getAuthorizationClient(context).authorize(request).await()
        val pending = result.pendingIntent
        return if (result.hasResolution() && pending != null) {
            DriveAuth.NeedsConsent(pending)
        } else {
            DriveAuth.Token(result.accessToken ?: throw DriveException(401, "Google didn't grant Drive access."))
        }
    }

    /** Reads the result of the consent screen launched for [DriveAuth.NeedsConsent]. */
    fun tokenFromConsent(data: Intent?): String? =
        runCatching { Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data).accessToken }.getOrNull()

    /**
     * Uploads a backup. Unless [force] is true, it skips the upload when
     * nothing changed since the last backup less than a week ago.
     */
    suspend fun backUp(force: Boolean): DriveBackupResult = running.withLock {
        val now = clock()
        val result = runCatching {
            val token = when (val auth = authorize()) {
                is DriveAuth.Token -> auth.value
                is DriveAuth.NeedsConsent ->
                    return@runCatching DriveBackupResult.Failed("Google Drive needs you to reconnect. Open Settings and tap Connect.")
            }
            val snapshot = backups.snapshot()
            val hash = backups.contentHash(snapshot)
            val status = settings.driveStatus.value
            if (!force && hash == settings.driveLastHash && now - status.lastSuccess < TimeUnit.DAYS.toMillis(6)) {
                return@runCatching DriveBackupResult.Unchanged
            }

            val api = DriveApi(client, token)
            val folder = ensureFolder(api, FOLDER_NAME, parentId = null, saved = settings.driveFolderId) {
                settings.driveFolderId = it
            }
            val photoFolder = ensureFolder(api, PHOTO_FOLDER_NAME, parentId = folder, saved = settings.drivePhotoFolderId) {
                settings.drivePhotoFolderId = it
            }

            val uploaded = api.list("${DriveApi.quote(photoFolder)} in parents and trashed = false").map { it.name }.toSet()
            for (name in snapshot.ownPhotoNames) {
                if (name in uploaded) continue
                val file = photos.file(name)
                if (!file.exists()) continue
                api.upload(name, "image/jpeg", withContext(Dispatchers.IO) { file.readBytes() }, photoFolder)
            }

            val zip = ByteArrayOutputStream().also { backups.writeZip(snapshot, it, includePhotos = false) }.toByteArray()
            val stamp = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date(now))
            api.upload("$BACKUP_PREFIX$stamp.zip", "application/zip", zip, folder)

            listBackups(api, folder).drop(KEEP).forEach { api.delete(it.id) }
            settings.driveLastHash = hash
            DriveBackupResult.Uploaded
        }.getOrElse { DriveBackupResult.Failed(it.message ?: "The backup didn't finish.") }

        when (result) {
            is DriveBackupResult.Failed -> settings.recordDriveFailure(now, result.message)
            else -> settings.recordDriveSuccess(now)
        }
        result
    }

    /** Backups in Drive, newest first. */
    suspend fun listBackups(): List<DriveFile> {
        val token = (authorize() as? DriveAuth.Token)?.value ?: throw DriveException(401, "Connect Google Drive first.")
        val api = DriveApi(client, token)
        val folder = settings.driveFolderId?.let { api.get(it) }?.takeIf { !it.trashed }?.id
            ?: api.list(folderQuery(FOLDER_NAME, null)).firstOrNull()?.id
            ?: return emptyList()
        return listBackups(api, folder)
    }

    /** Replaces everything in the app with [file], including her own photos from the photos folder. */
    suspend fun restore(file: DriveFile) {
        val token = (authorize() as? DriveAuth.Token)?.value ?: throw DriveException(401, "Connect Google Drive first.")
        val api = DriveApi(client, token)
        val (backup, _) = backups.readZip(ByteArrayInputStream(api.download(file.id)))
        val folder = api.list(folderQuery(FOLDER_NAME, null)).firstOrNull()?.id
        val photoFolder = folder?.let { api.list(folderQuery(PHOTO_FOLDER_NAME, it)).firstOrNull()?.id }
        val photoIds = photoFolder?.let { id ->
            api.list("${DriveApi.quote(id)} in parents and trashed = false").associate { it.name to it.id }
        }.orEmpty()
        backups.restore(backup) { name -> photoIds[name]?.let { api.download(it) } }
    }

    private suspend fun listBackups(api: DriveApi, folder: String): List<DriveFile> =
        api.list(
            "${DriveApi.quote(folder)} in parents and trashed = false and name contains '$BACKUP_PREFIX'",
            orderBy = "createdTime desc",
        )

    private suspend fun ensureFolder(
        api: DriveApi,
        name: String,
        parentId: String?,
        saved: String?,
        remember: (String) -> Unit,
    ): String {
        saved?.let { id -> api.get(id)?.takeIf { !it.trashed }?.let { return it.id } }
        val id = api.list(folderQuery(name, parentId)).firstOrNull()?.id ?: api.createFolder(name, parentId)
        remember(id)
        return id
    }

    private fun folderQuery(name: String, parentId: String?): String =
        "mimeType = '${DriveApi.FOLDER_MIME}' and name = ${DriveApi.quote(name)} and trashed = false" +
            (parentId?.let { " and ${DriveApi.quote(it)} in parents" } ?: "")

    fun schedule() {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DriveBackupWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    companion object {
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        const val FOLDER_NAME = "Recipe Box backups"
        const val PHOTO_FOLDER_NAME = "photos"
        const val BACKUP_PREFIX = "recipe-box-backup-"
        const val KEEP = 10
        private const val WORK_NAME = "drive-backup"
    }
}

/** Runs once a day; uploads only when something changed, or weekly regardless. */
class DriveBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = applicationContext.appContainer
        if (!container.settings.driveStatus.value.enabled) return Result.success()
        container.driveBackup.backUp(force = false)
        return Result.success()
    }
}
