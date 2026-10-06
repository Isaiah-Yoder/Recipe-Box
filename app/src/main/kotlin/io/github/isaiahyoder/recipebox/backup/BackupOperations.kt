package io.github.isaiahyoder.recipebox.backup

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import io.github.isaiahyoder.recipebox.settings.AppSettings
import io.github.isaiahyoder.recipebox.util.runCatchingCancellable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Backups and restores that keep going when she leaves Settings.
 *
 * Each operation runs in the app's own scope, one at a time, and reports its
 * result with a short message, so a restore that finishes after she has gone
 * back to her recipes still says so.
 */
class BackupOperations(
    private val context: Context,
    private val scope: CoroutineScope,
    private val manager: BackupManager,
    private val drive: DriveBackup,
    private val settings: AppSettings,
) {
    private val _busy = MutableStateFlow<String?>(null)

    /** What's running, such as "Restoring…", or null. */
    val busy: StateFlow<String?> = _busy.asStateFlow()

    private val main = Handler(Looper.getMainLooper())

    private fun tell(message: String) = main.post { Toast.makeText(context, message, Toast.LENGTH_LONG).show() }

    /** Runs [work] unless another operation is running. */
    private fun run(label: String, work: suspend () -> Unit) {
        if (_busy.value != null) return
        _busy.value = label
        scope.launch {
            try {
                work()
            } finally {
                _busy.value = null
            }
        }
    }

    /** Turns on daily Drive backups after she connected her account, and backs up once now. */
    fun driveConnected() {
        settings.setDriveConnectProblem(null)
        settings.setDriveEnabled(true)
        drive.schedule()
        backUpNow()
    }

    fun turnOffDrive() {
        settings.setDriveEnabled(false)
        drive.cancel()
    }

    fun backUpNow() = run("Backing up to Google Drive…") {
        when (val result = drive.backUp(force = true)) {
            DriveBackupResult.Uploaded, DriveBackupResult.Unchanged -> tell("Backed up to Google Drive.")
            is DriveBackupResult.Failed -> tell(result.message)
        }
    }

    fun saveFile(uri: Uri) = run("Saving backup file…") {
        runCatchingCancellable {
            val backup = manager.snapshot()
            context.contentResolver.openOutputStream(uri)!!.use { manager.writeZip(backup, it, includePhotos = true) }
        }.onSuccess { tell("Backup file saved.") }.onFailure { tell("Couldn't save the backup: ${it.message}") }
    }

    fun restoreFile(backup: BackupFile, photos: Map<String, ByteArray>) = run("Restoring…") {
        runCatchingCancellable { manager.restore(backup) { photos[it] } }
            .onSuccess { restored() }
            .onFailure { tell(it.message ?: "The restore didn't finish. Nothing was changed.") }
    }

    fun restoreDrive(file: DriveFile) = run("Restoring from Google Drive…") {
        runCatchingCancellable { drive.restore(file) }
            .onSuccess { restored() }
            .onFailure { tell(it.message ?: "The restore didn't finish. Nothing was changed.") }
    }

    private fun restored() {
        PhotoRestorer.schedule(context)
        tell("Restored. Recipe photos are downloading in the background.")
    }
}
