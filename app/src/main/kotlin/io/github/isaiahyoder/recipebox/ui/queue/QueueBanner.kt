package io.github.isaiahyoder.recipebox.ui.queue

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.isaiahyoder.recipebox.appContainer
import io.github.isaiahyoder.recipebox.data.ImportJobEntity
import io.github.isaiahyoder.recipebox.data.ImportStatus
import kotlinx.coroutines.flow.MutableStateFlow

/** Remembers when she swiped the banner away, for as long as the app is open. */
object QueueBannerState {
    val dismissedAt = MutableStateFlow(0L)
}

/** What the banner says about the queue right now. */
data class QueueSummary(val active: Int, val failed: Int) {
    companion object {
        fun of(jobs: List<ImportJobEntity>) = QueueSummary(
            active = jobs.count { it.status == ImportStatus.PENDING || it.status == ImportStatus.RUNNING },
            failed = jobs.count { it.status == ImportStatus.FAILED },
        )
    }
}

/**
 * Shows the banner while links are importing or when some need attention.
 * It disappears when everything finishes. Swiping it away hides it until
 * she adds more links or another link fails.
 */
fun shouldShowBanner(jobs: List<ImportJobEntity>, dismissedAt: Long): Boolean {
    val summary = QueueSummary.of(jobs)
    if (summary.active == 0 && summary.failed == 0) return false
    val newSinceDismissed = jobs.any { job ->
        job.createdAt > dismissedAt || (job.status == ImportStatus.FAILED && job.updatedAt > dismissedAt)
    }
    return dismissedAt == 0L || newSinceDismissed
}

@Composable
fun QueueBanner(onOpenQueue: () -> Unit) {
    val jobs by LocalContext.current.appContainer.importQueue.jobs
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val dismissedAt by QueueBannerState.dismissedAt.collectAsStateWithLifecycle()
    val visible = shouldShowBanner(jobs, dismissedAt)
    val summary = QueueSummary.of(jobs)

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
    ) {
        val swipe = rememberSwipeToDismissBoxState()
        LaunchedEffect(swipe.currentValue) {
            if (swipe.currentValue != SwipeToDismissBoxValue.Settled) {
                QueueBannerState.dismissedAt.value = System.currentTimeMillis()
                swipe.snapTo(SwipeToDismissBoxValue.Settled)
            }
        }
        SwipeToDismissBox(
            state = swipe,
            backgroundContent = {},
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Card(
                onClick = onOpenQueue,
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (summary.active == 0) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (summary.active > 0) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    } else {
                        Icon(Icons.Filled.ErrorOutline, contentDescription = null)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(bannerTitle(summary), style = MaterialTheme.typography.titleSmall)
                        Text("Tap to see the queue", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private fun bannerTitle(summary: QueueSummary): String {
    val adding = when (summary.active) {
        0 -> null
        1 -> "Adding 1 recipe"
        else -> "Adding ${summary.active} recipes"
    }
    val failed = when (summary.failed) {
        0 -> null
        1 -> "1 couldn't be added"
        else -> "${summary.failed} couldn't be added"
    }
    return listOfNotNull(adding, failed).joinToString(" · ")
}
