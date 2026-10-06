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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkManager
import io.github.isaiahyoder.recipebox.BuildConfigValues
import io.github.isaiahyoder.recipebox.R
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
            container.strings,
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
        headlineContent = { Text(stringResource(R.string.backup_drive_title)) },
        supportingContent = {
            Column {
                Text(
                    when {
                        !status.enabled -> stringResource(R.string.backup_drive_off)
                        status.lastSuccess > 0 -> stringResource(R.string.backup_drive_on_last, formatTime(status.lastSuccess))
                        else -> stringResource(R.string.backup_drive_on_none)
                    }
                )
                connectProblem?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (status.enabled && status.lastError != null) {
                    Text(status.lastError!!, color = MaterialTheme.colorScheme.error)
                }
                if (status.isOverdue(now)) {
                    Text(stringResource(R.string.backup_drive_overdue), color = MaterialTheme.colorScheme.error)
                }
            }
        },
    )
    FlowRow(Modifier.padding(start = 8.dp, end = 8.dp)) {
        if (!status.enabled || status.needsReconnect) {
            TextButton(
                onClick = { vm.connect { intent -> consent.launch(IntentSenderRequest.Builder(intent).build()) } },
                enabled = busy == null,
            ) { Text(stringResource(R.string.backup_drive_connect)) }
        }
        val problems = listOfNotNull(connectProblem, status.lastError?.takeIf { status.enabled })
        if (problems.isNotEmpty()) {
            TextButton(onClick = {
                val version = BuildConfigValues.versionName(context)
                // The heading line is for the developer, so it stays in English.
                copyToClipboard(
                    context,
                    context.getString(R.string.backup_drive_problem_clip_label),
                    (listOf("Recipe Box $version Google Drive") + problems).joinToString("\n"),
                )
            }) { Text(stringResource(R.string.backup_copy_details)) }
        }
        if (status.enabled) {
            TextButton(onClick = vm::backUpNow, enabled = busy == null) { Text(stringResource(R.string.backup_drive_back_up_now)) }
            TextButton(onClick = { vm.listDriveBackups() }, enabled = busy == null) { Text(stringResource(R.string.backup_restore)) }
            TextButton(onClick = vm::turnOffDrive, enabled = busy == null) { Text(stringResource(R.string.backup_drive_turn_off)) }
        }
    }

    // Files
    ListItem(
        headlineContent = { Text(stringResource(R.string.backup_file_title)) },
        supportingContent = { Text(stringResource(R.string.backup_file_summary)) },
    )
    FlowRow(Modifier.padding(start = 8.dp, end = 8.dp)) {
        TextButton(onClick = { exportFile.launch("recipe-box-backup-${Instant.now().toString().take(10)}.zip") }, enabled = busy == null) {
            Text(stringResource(R.string.backup_file_save))
        }
        TextButton(onClick = { openFile.launch(arrayOf("application/zip", "application/octet-stream")) }, enabled = busy == null) {
            Text(stringResource(R.string.backup_file_restore))
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
            title = { Text(stringResource(R.string.backup_drive_restore_title)) },
            text = {
                if (files.isEmpty()) {
                    Text(stringResource(R.string.backup_drive_none))
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
            confirmButton = { TextButton(onClick = vm::closeDriveBackups) { Text(stringResource(R.string.backup_close)) } },
        )
    }

    when (val restore = pending) {
        is PendingRestore.FromDrive -> ConfirmRestore(
            message = stringResource(
                R.string.backup_restore_confirm_drive,
                restore.file.createdTime?.let { formatTime(Instant.parse(it).toEpochMilli()) } ?: restore.file.name,
            ),
            onConfirm = vm::confirmRestore,
            onDismiss = vm::cancelRestore,
        )
        is PendingRestore.FromFile -> ConfirmRestore(
            message = pluralStringResource(
                R.plurals.backup_restore_confirm_file,
                restore.backup.recipes.size,
                formatTime(restore.backup.exportedAt),
                restore.backup.recipes.size,
            ),
            onConfirm = vm::confirmRestore,
            onDismiss = vm::cancelRestore,
        )
        null -> Unit
    }
}

@Composable
private fun ConfirmRestore(message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_restore_confirm_title)) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.backup_restore)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.backup_cancel)) } },
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
                pluralStringResource(
                    if (running) R.plurals.backup_photos_downloading else R.plurals.backup_photos_missing,
                    missing,
                    missing,
                )
            )
        },
        supportingContent = if (running) null else {
            { Text(stringResource(R.string.backup_photos_failed)) }
        },
        trailingContent = if (running) {
            { CircularProgressIndicator(Modifier.size(24.dp)) }
        } else {
            { TextButton(onClick = { PhotoRestorer.schedule(context) }) { Text(stringResource(R.string.backup_photos_retry)) } }
        },
    )
}

private fun formatTime(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
