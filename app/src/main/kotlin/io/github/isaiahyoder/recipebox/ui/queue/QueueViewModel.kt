package io.github.isaiahyoder.recipebox.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.isaiahyoder.recipebox.data.ImportJobEntity
import io.github.isaiahyoder.recipebox.importer.ImportQueue
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The import queue screen: its jobs and what she can do with them. */
class QueueViewModel(private val queue: ImportQueue) : ViewModel() {
    /** Null until the first load finishes. */
    val jobs: StateFlow<List<ImportJobEntity>?> = queue.jobs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun retry(jobId: Long) = viewModelScope.launch { queue.retry(jobId) }

    fun retryAllFailed() = viewModelScope.launch { queue.retryAllFailed() }

    fun remove(jobId: Long) = viewModelScope.launch { queue.remove(jobId) }

    fun clearFinished() = viewModelScope.launch { queue.clearFinished() }
}
