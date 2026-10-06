package io.github.isaiahyoder.recipebox.ui.settings

import android.content.ClipboardManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.isaiahyoder.recipebox.BuildFlavor
import io.github.isaiahyoder.recipebox.appContainer
import io.github.isaiahyoder.recipebox.cards.CardReadException
import io.github.isaiahyoder.recipebox.cards.OnDeviceAiStatus
import kotlinx.coroutines.launch

/** The Gemini key, where the build offers one, and on-device AI status, which decide how recipe cards are read. */
@Composable
fun CardReadingSection() {
    val container = LocalContext.current.appContainer
    val key by container.settings.geminiKey.collectAsStateWithLifecycle()
    val onDevice by container.onDeviceCards.status.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf(false) }
    var removing by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { container.onDeviceCards.refresh() }

    if (BuildFlavor.OWN_GEMINI_KEY) {
        ListItem(
            headlineContent = { Text("Gemini key") },
            supportingContent = {
                Text(
                    if (key != null) {
                        "Added. Recipe cards are read with Gemini, which reads handwriting best."
                    } else {
                        "Not added. A free key from Google AI Studio reads handwritten cards much more accurately."
                    }
                )
            },
        )
        Row(Modifier.padding(horizontal = 8.dp)) {
            TextButton(onClick = { editing = true }) { Text(if (key != null) "Change key" else "Add key") }
            if (key != null) {
                TextButton(onClick = { removing = true }) { Text("Remove key", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    ListItem(
        headlineContent = { Text("On-device AI") },
        supportingContent = {
            Text(
                when (onDevice) {
                    OnDeviceAiStatus.CHECKING -> "Checking this phone…"
                    OnDeviceAiStatus.READY ->
                        if (BuildFlavor.OWN_GEMINI_KEY) "Ready. Reads cards on this phone when Gemini isn't available." else "Ready. Reads cards on this phone."
                    OnDeviceAiStatus.DOWNLOADING -> "Downloading to this phone. It's used once the download finishes."
                    OnDeviceAiStatus.UNAVAILABLE ->
                        if (BuildFlavor.OWN_GEMINI_KEY) "Not available on this phone. Cards without Gemini use basic text recognition."
                        else "Not available on this phone. Cards are read with basic text recognition, which often misreads handwriting."
                }
            )
        },
    )

    if (editing) GeminiKeyDialog(onDismiss = { editing = false })
    if (removing) {
        AlertDialog(
            onDismissRequest = { removing = false },
            title = { Text("Remove the Gemini key?") },
            text = { Text("Recipe cards will be read on this phone instead. The key itself keeps working in Google AI Studio.") },
            confirmButton = {
                TextButton(onClick = {
                    container.settings.setGeminiKey(null)
                    removing = false
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { removing = false }) { Text("Cancel") } },
        )
    }
}

/** Takes a pasted key and checks it with Gemini before saving it. */
@Composable
private fun GeminiKeyDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val container = context.appContainer
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    // When the key couldn't be checked, such as while offline, she can still save it.
    var offerSaveAnyway by remember { mutableStateOf(false) }

    fun save() {
        container.settings.setGeminiKey(text)
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gemini key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Paste the key you created in Google AI Studio. It's stored only on this phone. On Gemini's free " +
                        "tier, Google may use the card photos you send to improve its products."
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it.trim()
                        problem = null
                        offerSaveAnyway = false
                    },
                    label = { Text("Key") },
                    singleLine = true,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(
                                if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (visible) "Hide key" else "Show key",
                            )
                        }
                    },
                    isError = problem != null,
                    supportingText = problem?.let { message -> { Text(message) } },
                )
                TextButton(onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()?.let { text = it }
                }) {
                    Icon(Icons.Filled.ContentPaste, contentDescription = null)
                    Text("Paste", Modifier.padding(start = 8.dp))
                }
            }
        },
        confirmButton = {
            if (offerSaveAnyway) {
                TextButton(onClick = ::save) { Text("Save anyway") }
            } else {
                TextButton(
                    enabled = text.isNotBlank() && !checking,
                    onClick = {
                        checking = true
                        scope.launch {
                            val result = container.geminiCards.checkKey(text)
                            checking = false
                            result.onSuccess { save() }.onFailure { error ->
                                val badKey = (error as? CardReadException)?.badKey == true
                                problem = if (badKey) {
                                    "Gemini didn't accept this key. Check that you copied all of it."
                                } else {
                                    error.message ?: "The key couldn't be checked."
                                }
                                offerSaveAnyway = !badKey
                            }
                        }
                    },
                ) { Text(if (checking) "Checking…" else "Save") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
