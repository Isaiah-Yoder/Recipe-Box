package io.github.isaiahyoder.recipebox.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.isaiahyoder.recipebox.appContainer
import io.github.isaiahyoder.recipebox.diagnostics.StallWatchdog
import io.github.isaiahyoder.recipebox.ui.copyToClipboard
import io.github.isaiahyoder.recipebox.ui.formatAgo

/** Shows recorded freezes, if any, so their details can be sent for help. */
@Composable
fun StallReportsItem() {
    val context = LocalContext.current
    val settings = context.appContainer.settings
    val reports by settings.stallReports.collectAsStateWithLifecycle()
    if (reports.isEmpty()) return
    val longest = reports.maxOf { it.millis }
    ListItem(
        headlineContent = { Text(if (reports.size == 1) "The app paused once" else "The app paused ${reports.size} times") },
        supportingContent = {
            Text(
                "Longest %.1f seconds, last ${formatAgo(reports.first().at)}. ".format(longest / 1000.0) +
                    "Copy the details to send them for help.",
            )
        },
    )
    Row(Modifier.padding(horizontal = 8.dp)) {
        TextButton(onClick = { copyToClipboard(context, "Recipe Box pauses", StallWatchdog.describe(reports)) }) {
            Text("Copy details")
        }
        TextButton(onClick = settings::clearStalls) { Text("Clear") }
    }
}
