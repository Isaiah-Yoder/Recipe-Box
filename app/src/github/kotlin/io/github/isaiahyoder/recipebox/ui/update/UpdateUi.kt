package io.github.isaiahyoder.recipebox.ui.update

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.isaiahyoder.recipebox.R
import io.github.isaiahyoder.recipebox.updater
import io.github.isaiahyoder.recipebox.ui.copyToClipboard
import io.github.isaiahyoder.recipebox.ui.formatAgo
import io.github.isaiahyoder.recipebox.update.AppUpdater
import io.github.isaiahyoder.recipebox.update.AvailableUpdate
import io.github.isaiahyoder.recipebox.update.UpdateState
import kotlinx.coroutines.launch

/**
 * Starts an update, first sending her to Android's "Install unknown apps"
 * switch if it's off. When she comes back with it on, the update starts.
 */
@Composable
private fun rememberStartUpdate(): (AvailableUpdate) -> Unit {
    val context = LocalContext.current
    val updater = context.updater
    var waiting by remember { mutableStateOf<AvailableUpdate?>(null) }
    var explaining by remember { mutableStateOf<AvailableUpdate?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val update = waiting
        waiting = null
        if (update != null && updater.canInstall()) updater.start(update)
    }

    explaining?.let { update ->
        AlertDialog(
            onDismissRequest = { explaining = null },
            title = { Text(stringResource(R.string.update_permission_title)) },
            text = { Text(stringResource(R.string.update_permission_message)) },
            confirmButton = {
                TextButton(onClick = {
                    explaining = null
                    waiting = update
                    permission.launch(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
                    )
                }) { Text(stringResource(R.string.update_continue)) }
            },
            dismissButton = { TextButton(onClick = { explaining = null }) { Text(stringResource(R.string.update_cancel)) } },
        )
    }

    return { update -> if (updater.canInstall()) updater.start(update) else explaining = update }
}

@Composable
private fun WhatsNewDialog(update: AvailableUpdate, onDismiss: () -> Unit, onUpdate: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_whats_new_title, update.versionName)) },
        text = {
            Text(
                update.notes.ifBlank { stringResource(R.string.update_no_notes) },
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = { TextButton(onClick = onUpdate) { Text(stringResource(R.string.update_update)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_not_now)) } },
    )
}

/** A card above the import banner when an update is ready or underway. */
@Composable
fun UpdateBanner() {
    val updater = LocalContext.current.updater
    val state by updater.state.collectAsStateWithLifecycle()
    // Changes when she puts the banner off, so it hides right away.
    var putOffCount by remember { mutableIntStateOf(0) }
    var showNotes by rememberSaveable { mutableStateOf(false) }
    val startUpdate = rememberStartUpdate()
    val context = LocalContext.current

    val current = state
    val visible = remember(current, putOffCount) {
        when (current) {
            is UpdateState.Available -> !updater.isPutOff(current.update)
            is UpdateState.Downloading, is UpdateState.Installing -> true
            is UpdateState.Failed -> current.update != null
            else -> false
        }
    }

    AnimatedVisibility(visible = visible, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(Icons.Filled.SystemUpdate, contentDescription = null)
                    Text(
                        when (current) {
                            is UpdateState.Available -> stringResource(R.string.update_available, current.update.versionName)
                            is UpdateState.Downloading -> stringResource(R.string.update_downloading_version, current.update.versionName)
                            is UpdateState.Installing -> stringResource(R.string.update_installing_version, current.update.versionName)
                            is UpdateState.Failed -> current.message
                            else -> ""
                        },
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                    )
                }
                if (current is UpdateState.Downloading) {
                    LinearProgressIndicator(
                        progress = { current.progress },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, end = 8.dp, bottom = 12.dp),
                    )
                }
                // Play Protect's warning covers this banner, so the hint comes before it appears.
                if (current is UpdateState.Downloading || current is UpdateState.Installing) {
                    Text(
                        stringResource(R.string.update_play_protect_hint),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(end = 8.dp, bottom = 12.dp),
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    when (current) {
                        is UpdateState.Available -> {
                            TextButton(onClick = { showNotes = true }) { Text(stringResource(R.string.update_whats_new)) }
                            TextButton(onClick = {
                                updater.putOff(current.update)
                                putOffCount++
                            }) { Text(stringResource(R.string.update_later)) }
                            TextButton(onClick = { startUpdate(current.update) }) { Text(stringResource(R.string.update_update)) }
                        }
                        is UpdateState.Installing -> current.confirm?.let { confirm ->
                            TextButton(onClick = { runCatching { context.startActivity(confirm) } }) {
                                Text(stringResource(R.string.update_install))
                            }
                        }
                        is UpdateState.Failed -> current.update?.let { update ->
                            TextButton(onClick = { openReleasePage(context) }) { Text(stringResource(R.string.update_download)) }
                            TextButton(onClick = { startUpdate(update) }) { Text(stringResource(R.string.update_try_again)) }
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    if (showNotes && current is UpdateState.Available) {
        WhatsNewDialog(
            current.update,
            onDismiss = { showNotes = false },
            onUpdate = {
                showNotes = false
                startUpdate(current.update)
            },
        )
    }
}

/** The update line in Settings: shows the status and checks or updates when tapped. */
@Composable
fun UpdateSettingsItem() {
    val updater = LocalContext.current.updater
    val state by updater.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val startUpdate = rememberStartUpdate()
    var showNotes by rememberSaveable { mutableStateOf(false) }

    if (!updater.enabled) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.update_settings_title)) },
            supportingContent = { Text(stringResource(R.string.update_test_builds)) },
        )
        return
    }

    val current = state
    ListItem(
        headlineContent = {
            Text(
                if (current is UpdateState.Available) {
                    stringResource(R.string.update_update_to, current.update.versionName)
                } else {
                    stringResource(R.string.update_check)
                }
            )
        },
        supportingContent = {
            Text(
                when (current) {
                    UpdateState.Idle -> stringResource(R.string.update_idle)
                    UpdateState.Checking -> stringResource(R.string.update_checking)
                    is UpdateState.UpToDate -> stringResource(R.string.update_up_to_date, formatAgo(current.checkedAt))
                    is UpdateState.Available -> stringResource(R.string.update_tap_to_update)
                    is UpdateState.Downloading -> stringResource(R.string.update_downloading_percent, (current.progress * 100).toInt())
                    is UpdateState.Installing -> stringResource(R.string.update_installing)
                    is UpdateState.Failed -> updater.lastProblem
                        ?.let { stringResource(R.string.update_failed_details, current.message, it) }
                        ?: current.message
                }
            )
        },
        trailingContent = {
            if (current == UpdateState.Checking || current is UpdateState.Downloading || current is UpdateState.Installing) {
                CircularProgressIndicator(Modifier.size(24.dp))
            }
        },
        modifier = Modifier.clickable {
            when (current) {
                is UpdateState.Available -> showNotes = true
                is UpdateState.Failed -> current.update?.let(startUpdate) ?: scope.launch { updater.check() }
                is UpdateState.Downloading, is UpdateState.Installing, UpdateState.Checking -> Unit
                else -> scope.launch { updater.check() }
            }
        },
    )

    if (current is UpdateState.Failed) {
        val context = LocalContext.current
        Row(Modifier.padding(horizontal = 8.dp)) {
            TextButton(onClick = { openReleasePage(context) }) { Text(stringResource(R.string.update_download_from_github)) }
            TextButton(onClick = {
                val details = listOfNotNull(
                    context.getString(
                        R.string.update_problem_summary,
                        updater.installedVersion,
                        current.update?.versionName ?: context.getString(R.string.update_unknown_version),
                    ),
                    current.message,
                    updater.lastProblem,
                ).joinToString("\n")
                copyToClipboard(context, context.getString(R.string.update_problem_clip_label), details)
            }) { Text(stringResource(R.string.update_copy_details)) }
        }
    }

    if (showNotes && current is UpdateState.Available) {
        WhatsNewDialog(
            current.update,
            onDismiss = { showNotes = false },
            onUpdate = {
                showNotes = false
                startUpdate(current.update)
            },
        )
    }
}

/** Opens the release page in her browser, where she can download and install the APK herself. */
private fun openReleasePage(context: android.content.Context) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, AppUpdater.RELEASE_PAGE_URL.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
