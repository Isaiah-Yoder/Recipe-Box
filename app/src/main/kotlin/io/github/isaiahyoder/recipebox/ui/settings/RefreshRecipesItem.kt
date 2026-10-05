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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
            "Applying this update's reading fixes to ${plural(status.total, "recipe")} when the phone is on Wi-Fi."
        status.running -> "Recipe ${status.done + 1} of ${status.total}. You can keep using the app."
        status.finishedAt > 0 -> buildList {
            add("Refreshed ${plural(status.updated, "recipe")} ${formatAgo(status.finishedAt)}.")
            if (status.failed.isNotEmpty()) add("${plural(status.failed.size, "page")} couldn't be read.")
        }.joinToString(" ")
        else -> "Reads each saved recipe's web page again with the latest fixes, such as ingredient groups and " +
            "complete steps. Parts you edited, notes, favorites, categories, tags, and photos stay."
    }

    ListItem(
        headlineContent = { Text(if (status.running) "Refreshing recipes" else "Refresh recipes") },
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
            TextButton(onClick = refresher::stop) { Text("Stop") }
        } else if (status.failed.isNotEmpty()) {
            TextButton(onClick = refresher::retryFailed) { Text("Try again") }
        }
    }

    confirming?.let { (linked, edited) ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text("Refresh ${plural(linked, "recipe")}?") },
            text = {
                Text(
                    "Each recipe's page is read again, which takes about ${minutes(linked)} and needs an internet " +
                        "connection. You can keep using the app. Your notes, favorites, categories, tags, and " +
                        "photos stay." +
                        if (edited > 0) " In the ${plural(edited, "recipe")} you edited, the parts you changed stay too." else ""
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirming = null
                    scope.launch { refresher.start() }
                }) { Text("Refresh") }
            },
            dismissButton = { TextButton(onClick = { confirming = null }) { Text("Cancel") } },
        )
    }
}

private fun plural(count: Int, noun: String) = "$count ${if (count == 1) noun else noun + "s"}"

/** About six seconds per recipe: the download plus a pause, so sites don't refuse. */
private fun minutes(count: Int): String {
    val minutes = ((count * 6) + 59) / 60
    return if (minutes <= 1) "a minute" else "$minutes minutes"
}
