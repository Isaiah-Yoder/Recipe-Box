package io.github.isaiahyoder.recipebox.ui.settings

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.github.isaiahyoder.recipebox.backup.BackupFile
import io.github.isaiahyoder.recipebox.backup.BackupManager
import io.github.isaiahyoder.recipebox.backup.BackupOperations
import io.github.isaiahyoder.recipebox.backup.DriveAuth
import io.github.isaiahyoder.recipebox.backup.DriveBackup
import io.github.isaiahyoder.recipebox.backup.DriveFile
import io.github.isaiahyoder.recipebox.backup.PhotoRestorer
import io.github.isaiahyoder.recipebox.data.RecipeDao
import io.github.isaiahyoder.recipebox.settings.AppSettings
import io.github.isaiahyoder.recipebox.settings.DriveStatus
import io.github.isaiahyoder.recipebox.util.runCatchingCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A backup she picked and is asked to confirm before it replaces everything. */
sealed interface PendingRestore {
    data class FromFile(val backup: BackupFile, val photos: Map<String, ByteArray>) : PendingRestore
    data class FromDrive(val file: DriveFile) : PendingRestore
}

/**
 * The backup section of Settings. Backups and restores run in
 * [BackupOperations], so they finish even if she leaves Settings.
 */
class BackupViewModel(
    private val operations: BackupOperations,
    private val drive: DriveBackup,
    private val manager: BackupManager,
    private val settings: AppSettings,
    private val resolver: ContentResolver,
    recipeDao: RecipeDao,
    workManager: WorkManager,
) : ViewModel() {
    val driveStatus: StateFlow<DriveStatus> = settings.driveStatus
    val connectProblem: StateFlow<String?> = settings.driveConnectProblem

    private val _localBusy = MutableStateFlow<String?>(null)

    /** What's running: a backup or restore, or connecting and listing in this screen. */
    val busy: StateFlow<String?> = combine(operations.busy, _localBusy) { running, local -> running ?: local }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Drive backups to choose from, or null when the list isn't open. */
    private val _driveBackups = MutableStateFlow<List<DriveFile>?>(null)
    val driveBackups: StateFlow<List<DriveFile>?> = _driveBackups.asStateFlow()

    private val _pending = MutableStateFlow<PendingRestore?>(null)
    val pendingRestore: StateFlow<PendingRestore?> = _pending.asStateFlow()

    /** Short messages for a toast, cleared once shown. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun messageShown() {
        _message.value = null
    }

    /** Cover photos to download again, such as after a restore. */
    val missingPhotos: StateFlow<Int> = recipeDao.observeMissingCoverCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val downloadingPhotos: StateFlow<Boolean> = workManager.getWorkInfosForUniqueWorkFlow(PhotoRestorer.WORK_NAME)
        .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Connects Google Drive; when Google needs her approval, [onNeedsConsent] shows its screen. */
    fun connect(onNeedsConsent: (PendingIntent) -> Unit) = viewModelScope.launch {
        _localBusy.value = "Connecting to Google Drive…"
        runCatchingCancellable { drive.authorize() }
            .onSuccess { auth ->
                when (auth) {
                    is DriveAuth.NeedsConsent -> onNeedsConsent(auth.intent)
                    is DriveAuth.Token -> {
                        _localBusy.value = null
                        operations.driveConnected()
                    }
                }
            }
            .onFailure { error -> connectFailed("Couldn't connect Google Drive. " + drive.describe(error)) }
        _localBusy.value = null
    }

    /** Handles Google's approval screen; its data explains a failure even when it didn't return OK. */
    fun consentResult(data: Intent?, closedWithoutAnswer: Boolean) {
        drive.tokenFromConsent(data)
            .onSuccess { operations.driveConnected() }
            .onFailure { error ->
                connectFailed(
                    "Google Drive wasn't connected. " +
                        if (closedWithoutAnswer) "The Google screen was closed." else drive.describe(error)
                )
            }
    }

    private fun connectFailed(problem: String) {
        settings.setDriveConnectProblem(problem)
        _message.value = problem
    }

    fun backUpNow() = operations.backUpNow()

    fun turnOffDrive() = operations.turnOffDrive()

    fun saveFile(uri: Uri) = operations.saveFile(uri)

    fun openFile(uri: Uri) = viewModelScope.launch {
        _localBusy.value = "Reading backup file…"
        runCatchingCancellable { resolver.openInputStream(uri)!!.use { manager.readZip(it) } }
            .onSuccess { (backup, photos) -> _pending.value = PendingRestore.FromFile(backup, photos) }
            .onFailure { _message.value = it.message ?: "Couldn't read that file." }
        _localBusy.value = null
    }

    fun listDriveBackups() = viewModelScope.launch {
        _localBusy.value = "Finding backups…"
        runCatchingCancellable { drive.listBackups() }
            .onSuccess { _driveBackups.value = it }
            .onFailure { _message.value = it.message ?: "Couldn't list backups." }
        _localBusy.value = null
    }

    fun closeDriveBackups() {
        _driveBackups.value = null
    }

    fun chooseDriveBackup(file: DriveFile) {
        _driveBackups.value = null
        _pending.value = PendingRestore.FromDrive(file)
    }

    fun cancelRestore() {
        _pending.value = null
    }

    fun confirmRestore() {
        when (val pending = _pending.value) {
            is PendingRestore.FromFile -> operations.restoreFile(pending.backup, pending.photos)
            is PendingRestore.FromDrive -> operations.restoreDrive(pending.file)
            null -> Unit
        }
        _pending.value = null
    }
}
