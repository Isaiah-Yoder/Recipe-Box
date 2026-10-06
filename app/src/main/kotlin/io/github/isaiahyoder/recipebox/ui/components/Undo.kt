package io.github.isaiahyoder.recipebox.ui.components

import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import io.github.isaiahyoder.recipebox.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** A change she can take back, with the message that reports it, such as "Removed Chili from this list". */
class Undoable(
    @param:StringRes val message: Int,
    val messageArgs: List<Any> = emptyList(),
    val undo: suspend () -> Unit,
)

/**
 * Changes a ViewModel reports so she can undo them. The undo runs in the
 * ViewModel's [scope], so it finishes even if the screen changes.
 */
class UndoReports(private val scope: CoroutineScope) {
    private val channel = Channel<Undoable>(Channel.BUFFERED)

    val reports: Flow<Undoable> = channel.receiveAsFlow()

    fun report(change: Undoable) {
        channel.trySend(change)
    }

    fun undo(change: Undoable) {
        scope.launch { change.undo() }
    }
}

/**
 * Shows each reported change in a snackbar with **Undo**. A newer change
 * replaces the snackbar of an older one, which can then no longer be undone.
 */
@Composable
fun UndoSnackbars(undo: UndoReports, host: SnackbarHostState) {
    val context = LocalContext.current
    LaunchedEffect(undo, host) {
        undo.reports.collectLatest { change ->
            val result = host.showSnackbar(
                message = context.getString(change.message, *change.messageArgs.toTypedArray()),
                actionLabel = context.getString(R.string.common_undo),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) undo.undo(change)
        }
    }
}
