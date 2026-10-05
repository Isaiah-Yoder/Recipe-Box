package io.github.isaiahyoder.recipebox.ui.queue

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.isaiahyoder.recipebox.appContainer
import io.github.isaiahyoder.recipebox.data.ImportJobEntity
import io.github.isaiahyoder.recipebox.data.ImportStatus
import kotlinx.coroutines.launch
import java.net.URI
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(onOpenRecipe: (Long) -> Unit, onBack: () -> Unit) {
    val container = LocalContext.current.appContainer
    val jobs by container.database.importJobDao().observeAll().collectAsStateWithLifecycle(initialValue = null)
    val queue = container.importQueue
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import queue") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val current = jobs.orEmpty()
                    if (current.any { it.status == ImportStatus.FAILED }) {
                        TextButton(onClick = { scope.launch { queue.retryAllFailed() } }) { Text("Retry all") }
                    }
                    if (current.any { it.status == ImportStatus.DONE || it.status == ImportStatus.DUPLICATE }) {
                        TextButton(onClick = { scope.launch { queue.clearFinished() } }) { Text("Clear finished") }
                    }
                },
            )
        },
    ) { padding ->
        val current = jobs ?: return@Scaffold
        if (current.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "The queue is empty.\n\nShare recipe pages to Recipe Box, or tap Add recipe and paste links.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 32.dp)) {
            items(current, key = { it.id }) { job ->
                JobRow(
                    job = job,
                    onOpenRecipe = onOpenRecipe,
                    onRetry = { scope.launch { queue.retry(job.id) } },
                    onRemove = { scope.launch { queue.remove(job.id) } },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun JobRow(
    job: ImportJobEntity,
    onOpenRecipe: (Long) -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val recipeId = job.recipeId
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = recipeId != null) { recipeId?.let(onOpenRecipe) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatusIcon(job)
            Column(Modifier.weight(1f)) {
                Text(
                    job.title ?: shortLink(job.url),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    statusText(job),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (job.status == ImportStatus.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
        if (job.status == ImportStatus.FAILED) {
            Row(Modifier.padding(start = 36.dp)) {
                TextButton(onClick = onRetry) { Text("Retry") }
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, job.url.toUri())) }) {
                    Text("Open page")
                }
                TextButton(onClick = onRemove) { Text("Remove") }
            }
        } else if (job.status == ImportStatus.PENDING && job.attempts > 0) {
            Row(Modifier.padding(start = 36.dp)) {
                TextButton(onClick = onRemove) { Text("Remove") }
            }
        }
    }
}

@Composable
private fun StatusIcon(job: ImportJobEntity) {
    val modifier = Modifier.size(24.dp)
    when (job.status) {
        ImportStatus.RUNNING -> CircularProgressIndicator(modifier, strokeWidth = 3.dp)
        ImportStatus.PENDING -> Icon(Icons.Filled.Schedule, null, modifier, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        ImportStatus.DONE, ImportStatus.DUPLICATE ->
            Icon(Icons.Filled.CheckCircle, null, modifier, tint = MaterialTheme.colorScheme.primary)
        ImportStatus.FAILED -> Icon(Icons.Filled.ErrorOutline, null, modifier, tint = MaterialTheme.colorScheme.error)
    }
}

private fun statusText(job: ImportJobEntity): String = when (job.status) {
    ImportStatus.PENDING -> if (job.attempts == 0) {
        "Waiting"
    } else {
        val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(job.nextAttemptAt))
        "Trying again at $time. ${job.lastError.orEmpty()}".trim()
    }
    ImportStatus.RUNNING -> "Adding…"
    ImportStatus.DONE -> "Added"
    ImportStatus.DUPLICATE -> "Already in your recipes"
    ImportStatus.FAILED -> "Couldn't add this page. ${job.lastError.orEmpty()}".trim()
}

/** "allrecipes.com/recipe/123/soup" reads better than the full address. */
private fun shortLink(url: String): String = runCatching {
    val uri = URI(url)
    (uri.host.removePrefix("www.") + uri.path.trimEnd('/'))
}.getOrDefault(url)
