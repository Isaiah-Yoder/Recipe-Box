package io.github.isaiahyoder.recipebox.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.isaiahyoder.recipebox.R
import io.github.isaiahyoder.recipebox.appContainer
import io.github.isaiahyoder.recipebox.ui.formatAgo
import kotlinx.coroutines.launch

/**
 * Reads every saved recipe's page again with the latest fixes, then updates
 * automatic tags. It replaces the older "Update automatic tags" item.
 */
@Composable
fun RefreshRecipesItem() {
    val container = LocalContext.current.appContainer
    val refresher = container.recipeRefresher
    val status by container.settings.refreshStatus.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    // Recipe counts for the confirmation: (with a page, edited by her).
    var confirming by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    val summary = when {
        status.running && status.wifiOnly && status.done == 0 ->
            pluralStringResource(R.plurals.settings_refresh_waiting, status.total, status.total)
        status.running -> stringResource(R.string.settings_refresh_progress, status.done + 1, status.total)
        status.finishedAt > 0 -> listOfNotNull(
            pluralStringResource(R.plurals.settings_refresh_done, status.updated, status.updated, formatAgo(status.finishedAt)),
            if (status.failed.isNotEmpty()) {
                pluralStringResource(R.plurals.settings_refresh_failed, status.failed.size, status.failed.size)
            } else {
                null
            },
        ).joinToString(" ")
        else -> stringResource(R.string.settings_refresh_summary)
    }

    ListItem(
        headlineContent = {
            Text(stringResource(if (status.running) R.string.settings_refresh_running else R.string.settings_refresh))
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(summary)
                if (status.running && status.total > 0) {
                    LinearProgressIndicator(
                        progress = { status.done.toFloat() / status.total },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        modifier = Modifier.clickable(enabled = !status.running) {
            scope.launch { confirming = refresher.counts() }
        },
    )
    Row(Modifier.padding(horizontal = 8.dp)) {
        if (status.running) {
            TextButton(onClick = refresher::stop) { Text(stringResource(R.string.settings_refresh_stop)) }
        } else if (status.failed.isNotEmpty()) {
            TextButton(onClick = refresher::retryFailed) { Text(stringResource(R.string.settings_refresh_retry)) }
        }
    }

    confirming?.let { (linked, edited) ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text(pluralStringResource(R.plurals.settings_refresh_confirm_title, linked, linked)) },
            text = {
                val minutes = minutes(linked)
                val body = pluralStringResource(R.plurals.settings_refresh_confirm_body, minutes, minutes)
                Text(
                    if (edited > 0) {
                        body + " " + pluralStringResource(R.plurals.settings_refresh_confirm_edited, edited, edited)
                    } else {
                        body
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirming = null
                    scope.launch { refresher.start() }
                }) { Text(stringResource(R.string.settings_refresh_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirming = null }) { Text(stringResource(R.string.settings_cancel)) } },
        )
    }
}

/**
 * About six seconds per recipe: the download plus a pause, so sites don't
 * refuse. It's at least 1, which the text shows as "a minute".
 */
private fun minutes(count: Int): Int = maxOf(1, ((count * 6) + 59) / 60)
