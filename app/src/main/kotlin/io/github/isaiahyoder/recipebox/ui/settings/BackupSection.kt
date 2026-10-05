package io.github.isaiahyoder.recipebox.ui.settings

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.github.isaiahyoder.recipebox.BuildConfigValues
import io.github.isaiahyoder.recipebox.appContainer
import io.github.isaiahyoder.recipebox.ui.copyToClipboard
import io.github.isaiahyoder.recipebox.backup.BackupFile
import io.github.isaiahyoder.recipebox.backup.DriveAuth
import io.github.isaiahyoder.recipebox.backup.DriveBackupResult
import io.github.isaiahyoder.recipebox.backup.DriveFile
import io.github.isaiahyoder.recipebox.backup.PhotoRestorer
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.time.Instant
import java.util.Date

/** Google Drive backup, backup files, and photo downloads after a restore. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackupSection() {
    val context = LocalContext.current
    val container = context.appContainer
    val settings = container.settings
    val drive = container.driveBackup
    val status by settings.driveStatus.collectAsStateWithLifecycle()
    val connectProblem by settings.driveConnectProblem.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var driveBackups by remember { mutableStateOf<List<DriveFile>?>(null) }
    var pendingDriveRestore by remember { mutableStateOf<DriveFile?>(null) }
    var pendingFileRestore by remember { mutableStateOf<Pair<BackupFile, Map<String, ByteArray>>?>(null) }

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()

    suspend fun backUpNow() {
        busy = "Backing up to Google Drive…"
        when (val result = drive.backUp(force = true)) {
            DriveBackupResult.Uploaded, DriveBackupResult.Unchanged -> toast("Backed up to Google Drive.")
            is DriveBackupResult.Failed -> toast(result.message)
        }
        busy = null
    }

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        // The result's data explains a failure even when the screen didn't return OK.
        drive.tokenFromConsent(result.data)
            .onSuccess {
                settings.setDriveConnectProblem(null)
                settings.setDriveEnabled(true)
                drive.schedule()
                scope.launch { backUpNow() }
            }
            .onFailure { error ->
                val problem = "Google Drive wasn't connected. " +
                    (if (result.resultCode == Activity.RESULT_CANCELED && result.data == null) "The Google screen was closed." else drive.describe(error))
                settings.setDriveConnectProblem(problem)
                toast(problem)
            }
    }

    fun connect() = scope.launch {
        busy = "Connecting to Google Drive…"
        runCatching { drive.authorize() }
            .onSuccess { auth ->
                when (auth) {
                    is DriveAuth.NeedsConsent -> consent.launch(IntentSenderRequest.Builder(auth.intent).build())
                    is DriveAuth.Token -> {
                        settings.setDriveConnectProblem(null)
                        settings.setDriveEnabled(true)
                        drive.schedule()
                        busy = null
                        backUpNow()
                    }
                }
            }
            .onFailure { error ->
                val problem = "Couldn't connect Google Drive. " + drive.describe(error)
                settings.setDriveConnectProblem(problem)
                toast(problem)
            }
        busy = null
    }

    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = "Saving backup file…"
            runCatching {
                val backup = container.backupManager.snapshot()
                context.contentResolver.openOutputStream(uri)!!.use {
                    container.backupManager.writeZip(backup, it, includePhotos = true)
                }
            }.onSuccess { toast("Backup file saved.") }.onFailure { toast("Couldn't save the backup: ${it.message}") }
            busy = null
        }
    }

    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { context.contentResolver.openInputStream(uri)!!.use { container.backupManager.readZip(it) } }
                .onSuccess { pendingFileRestore = it }
                .onFailure { toast(it.message ?: "Couldn't read that file.") }
        }
    }

    // Drive
    val now = System.currentTimeMillis()
    ListItem(
        headlineContent = { Text("Google Drive backup") },
        supportingContent = {
            Column {
                Text(
                    when {
                        !status.enabled -> "Off. Turn it on to back up to your Google Drive every day."
                        status.lastSuccess > 0 -> "On. Last backup: ${formatTime(status.lastSuccess)}."
                        else -> "On. No backup yet."
                    }
                )
                connectProblem?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (status.enabled && status.lastError != null) {
                    Text(status.lastError!!, color = MaterialTheme.colorScheme.error)
                }
                if (status.isOverdue(now)) {
                    Text("Backups haven't worked for over a week.", color = MaterialTheme.colorScheme.error)
                }
            }
        },
    )
    FlowRow(Modifier.padding(start = 8.dp, end = 8.dp)) {
        if (!status.enabled || status.lastError?.contains("reconnect", ignoreCase = true) == true) {
            TextButton(onClick = { connect() }, enabled = busy == null) { Text("Connect Google Drive") }
        }
        val problems = listOfNotNull(connectProblem, status.lastError?.takeIf { status.enabled })
        if (problems.isNotEmpty()) {
            TextButton(onClick = {
                val version = BuildConfigValues.versionName(context)
                copyToClipboard(context, "Google Drive problem", (listOf("Recipe Box $version Google Drive") + problems).joinToString("\n"))
            }) { Text("Copy details") }
        }
        if (status.enabled) {
            TextButton(onClick = { scope.launch { backUpNow() } }, enabled = busy == null) { Text("Back up now") }
            TextButton(onClick = {
                scope.launch {
                    busy = "Finding backups…"
                    runCatching { drive.listBackups() }
                        .onSuccess { driveBackups = it }
                        .onFailure { toast(it.message ?: "Couldn't list backups.") }
                    busy = null
                }
            }, enabled = busy == null) { Text("Restore") }
            TextButton(onClick = {
                settings.setDriveEnabled(false)
                drive.cancel()
            }, enabled = busy == null) { Text("Turn off") }
        }
    }

    // Files
    ListItem(
        headlineContent = { Text("Backup file") },
        supportingContent = {
            Text("Save everything, including your own photos, to a file you can keep anywhere, or restore from one.")
        },
    )
    FlowRow(Modifier.padding(start = 8.dp, end = 8.dp)) {
        TextButton(onClick = { exportFile.launch("recipe-box-backup-${Instant.now().toString().take(10)}.zip") }, enabled = busy == null) {
            Text("Save backup file")
        }
        TextButton(onClick = { openFile.launch(arrayOf("application/zip", "application/octet-stream")) }, enabled = busy == null) {
            Text("Restore from file")
        }
    }

    PhotoStatus()

    busy?.let { message ->
        ListItem(
            headlineContent = { Text(message) },
            leadingContent = { CircularProgressIndicator(Modifier.size(24.dp)) },
        )
    }

    driveBackups?.let { files ->
        AlertDialog(
            onDismissRequest = { driveBackups = null },
            title = { Text("Restore from Google Drive") },
            text = {
                if (files.isEmpty()) {
                    Text("There are no backups in Google Drive yet.")
                } else {
                    Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                        files.forEach { file ->
                            Text(
                                file.createdTime?.let { formatTime(Instant.parse(it).toEpochMilli()) } ?: file.name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        driveBackups = null
                                        pendingDriveRestore = file
                                    }
                                    .padding(vertical = 12.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { driveBackups = null }) { Text("Close") } },
        )
    }

    pendingDriveRestore?.let { file ->
        ConfirmRestore(
            description = "the Google Drive backup from ${file.createdTime?.let { formatTime(Instant.parse(it).toEpochMilli()) } ?: file.name}",
            onConfirm = {
                pendingDriveRestore = null
                scope.launch {
                    busy = "Restoring from Google Drive…"
                    runCatching { drive.restore(file) }
                        .onSuccess {
                            PhotoRestorer.schedule(context)
                            toast("Restored. Recipe photos are downloading in the background.")
                        }
                        .onFailure { toast(it.message ?: "The restore didn't finish. Nothing was changed.") }
                    busy = null
                }
            },
            onDismiss = { pendingDriveRestore = null },
        )
    }

    pendingFileRestore?.let { (backup, photos) ->
        ConfirmRestore(
            description = "this backup file from ${formatTime(backup.exportedAt)} with ${backup.recipes.size} recipes",
            onConfirm = {
                pendingFileRestore = null
                scope.launch {
                    busy = "Restoring…"
                    runCatching { container.backupManager.restore(backup) { photos[it] } }
                        .onSuccess {
                            PhotoRestorer.schedule(context)
                            toast("Restored. Recipe photos are downloading in the background.")
                        }
                        .onFailure { toast(it.message ?: "The restore didn't finish. Nothing was changed.") }
                    busy = null
                }
            },
            onDismiss = { pendingFileRestore = null },
        )
    }
}

@Composable
private fun ConfirmRestore(description: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Replace everything?") },
        text = {
            Text(
                "Restoring $description replaces all recipes, grocery lists, tags, and categories in Recipe Box. " +
                    "Recipes added since that backup are removed."
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Restore") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Shows downloads of missing cover photos, such as after a restore. */
@Composable
private fun PhotoStatus() {
    val context = LocalContext.current
    val container = context.appContainer
    val missing by container.database.recipeDao().observeMissingCoverCount().collectAsStateWithLifecycle(initialValue = 0)
    val running by remember {
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(PhotoRestorer.WORK_NAME)
            .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED } }
    }.collectAsStateWithLifecycle(initialValue = false)
    if (missing == 0) return
    ListItem(
        headlineContent = {
            Text(
                if (running) {
                    "Downloading $missing recipe ${if (missing == 1) "photo" else "photos"}…"
                } else {
                    "$missing recipe ${if (missing == 1) "photo is" else "photos are"} missing."
                }
            )
        },
        supportingContent = if (running) null else {
            { Text("Some photos couldn't be downloaded. Try again later.") }
        },
        trailingContent = if (running) {
            { CircularProgressIndicator(Modifier.size(24.dp)) }
        } else {
            { TextButton(onClick = { PhotoRestorer.schedule(context) }) { Text("Try again") } }
        },
    )
}

private fun formatTime(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
