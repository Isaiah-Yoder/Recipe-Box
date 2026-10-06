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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.isaiahyoder.recipebox.R
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
        headlineContent = { Text(pluralStringResource(R.plurals.diagnostics_stalls_title, reports.size, reports.size)) },
        supportingContent = {
            Text(stringResource(R.string.diagnostics_stalls_summary, longest / 1000.0, formatAgo(reports.first().at)))
        },
    )
    Row(Modifier.padding(horizontal = 8.dp)) {
        // The copied details are for the developer, so they stay in English.
        TextButton(onClick = {
            copyToClipboard(context, context.getString(R.string.diagnostics_stalls_clip_label), StallWatchdog.describe(reports))
        }) {
            Text(stringResource(R.string.diagnostics_copy_details))
        }
        TextButton(onClick = settings::clearStalls) { Text(stringResource(R.string.diagnostics_clear)) }
    }
}
