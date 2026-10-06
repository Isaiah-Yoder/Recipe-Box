package io.github.isaiahyoder.recipebox.ui.settings

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkManager
import io.github.isaiahyoder.recipebox.BuildConfigValues
import io.github.isaiahyoder.recipebox.appContainer
import io.github.isaiahyoder.recipebox.ui.copyToClipboard
import io.github.isaiahyoder.recipebox.backup.PhotoRestorer
import java.text.DateFormat
import java.time.Instant
import java.util.Date

/** Google Drive backup, backup files, and photo downloads after a restore. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackupSection() {
    val context = LocalContext.current
    val container = context.appContainer
    val vm = viewModel {
        BackupViewModel(
            container.backupOperations,
            container.driveBackup,
            container.backupManager,
            container.settings,
            context.applicationContext.contentResolver,
            container.database.recipeDao(),
            WorkManager.getInstance(context.applicationContext),
        )
    }
    val status by vm.driveStatus.collectAsStateWithLifecycle()
    val connectProblem by vm.connectProblem.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val driveBackups by vm.driveBackups.collectAsStateWithLifecycle()
    val pending by vm.pendingRestore.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            vm.messageShown()
        }
    }

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        vm.consentResult(result.data, closedWithoutAnswer = result.resultCode == Activity.RESULT_CANCELED && result.data == null)
    }
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let(vm::saveFile)
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::openFile)
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
            TextButton(
                onClick = { vm.connect { intent -> consent.launch(IntentSenderRequest.Builder(intent).build()) } },
                enabled = busy == null,
            ) { Text("Connect Google Drive") }
        }
        val problems = listOfNotNull(connectProblem, status.lastError?.takeIf { status.enabled })
        if (problems.isNotEmpty()) {
            TextButton(onClick = {
                val version = BuildConfigValues.versionName(context)
                copyToClipboard(context, "Google Drive problem", (listOf("Recipe Box $version Google Drive") + problems).joinToString("\n"))
            }) { Text("Copy details") }
        }
        if (status.enabled) {
            TextButton(onClick = vm::backUpNow, enabled = busy == null) { Text("Back up now") }
            TextButton(onClick = { vm.listDriveBackups() }, enabled = busy == null) { Text("Restore") }
            TextButton(onClick = vm::turnOffDrive, enabled = busy == null) { Text("Turn off") }
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

    PhotoStatus(vm)

    busy?.let { label ->
        ListItem(
            headlineContent = { Text(label) },
            leadingContent = { CircularProgressIndicator(Modifier.size(24.dp)) },
        )
    }

    driveBackups?.let { files ->
        AlertDialog(
            onDismissRequest = vm::closeDriveBackups,
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
                                    .clickable { vm.chooseDriveBackup(file) }
                                    .padding(vertical = 12.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = vm::closeDriveBackups) { Text("Close") } },
        )
    }

    when (val restore = pending) {
        is PendingRestore.FromDrive -> ConfirmRestore(
            description = "the Google Drive backup from " +
                (restore.file.createdTime?.let { formatTime(Instant.parse(it).toEpochMilli()) } ?: restore.file.name),
            onConfirm = vm::confirmRestore,
            onDismiss = vm::cancelRestore,
        )
        is PendingRestore.FromFile -> ConfirmRestore(
            description = "this backup file from ${formatTime(restore.backup.exportedAt)} with ${restore.backup.recipes.size} recipes",
            onConfirm = vm::confirmRestore,
            onDismiss = vm::cancelRestore,
        )
        null -> Unit
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
private fun PhotoStatus(vm: BackupViewModel) {
    val context = LocalContext.current
    val missing by vm.missingPhotos.collectAsStateWithLifecycle()
    val running by vm.downloadingPhotos.collectAsStateWithLifecycle()
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
