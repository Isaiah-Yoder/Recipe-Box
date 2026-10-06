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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.isaiahyoder.recipebox.BuildFlavor
import io.github.isaiahyoder.recipebox.R
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
            headlineContent = { Text(stringResource(R.string.settings_gemini_key)) },
            supportingContent = {
                Text(
                    stringResource(
                        if (key != null) R.string.settings_gemini_key_added else R.string.settings_gemini_key_missing
                    )
                )
            },
        )
        Row(Modifier.padding(horizontal = 8.dp)) {
            TextButton(onClick = { editing = true }) {
                Text(stringResource(if (key != null) R.string.settings_gemini_key_change else R.string.settings_gemini_key_add))
            }
            if (key != null) {
                TextButton(onClick = { removing = true }) {
                    Text(stringResource(R.string.settings_gemini_key_remove), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_on_device_ai)) },
        supportingContent = {
            Text(
                stringResource(
                    when (onDevice) {
                        OnDeviceAiStatus.CHECKING -> R.string.settings_on_device_checking
                        OnDeviceAiStatus.READY ->
                            if (BuildFlavor.OWN_GEMINI_KEY) R.string.settings_on_device_ready_with_gemini else R.string.settings_on_device_ready
                        OnDeviceAiStatus.DOWNLOADING -> R.string.settings_on_device_downloading
                        OnDeviceAiStatus.UNAVAILABLE ->
                            if (BuildFlavor.OWN_GEMINI_KEY) R.string.settings_on_device_unavailable_with_gemini
                            else R.string.settings_on_device_unavailable
                    }
                )
            )
        },
    )

    if (editing) GeminiKeyDialog(onDismiss = { editing = false })
    if (removing) {
        AlertDialog(
            onDismissRequest = { removing = false },
            title = { Text(stringResource(R.string.settings_gemini_remove_title)) },
            text = { Text(stringResource(R.string.settings_gemini_remove_body)) },
            confirmButton = {
                TextButton(onClick = {
                    container.settings.setGeminiKey(null)
                    removing = false
                }) { Text(stringResource(R.string.settings_remove)) }
            },
            dismissButton = { TextButton(onClick = { removing = false }) { Text(stringResource(R.string.settings_cancel)) } },
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
        title = { Text(stringResource(R.string.settings_gemini_key)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_gemini_key_intro))
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it.trim()
                        problem = null
                        offerSaveAnyway = false
                    },
                    label = { Text(stringResource(R.string.settings_gemini_key_field)) },
                    singleLine = true,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(
                                if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = stringResource(
                                    if (visible) R.string.settings_gemini_key_hide else R.string.settings_gemini_key_show
                                ),
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
                    Text(stringResource(R.string.settings_paste), Modifier.padding(start = 8.dp))
                }
            }
        },
        confirmButton = {
            if (offerSaveAnyway) {
                TextButton(onClick = ::save) { Text(stringResource(R.string.settings_gemini_key_save_anyway)) }
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
                                    context.getString(R.string.settings_gemini_key_rejected)
                                } else {
                                    error.message ?: context.getString(R.string.settings_gemini_key_unchecked)
                                }
                                offerSaveAnyway = !badKey
                            }
                        }
                    },
                ) {
                    Text(stringResource(if (checking) R.string.settings_gemini_key_checking else R.string.settings_save))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    )
}
