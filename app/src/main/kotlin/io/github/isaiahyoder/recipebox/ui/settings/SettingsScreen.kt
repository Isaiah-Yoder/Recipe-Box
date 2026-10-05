package io.github.isaiahyoder.recipebox.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.isaiahyoder.recipebox.BuildConfigValues
import io.github.isaiahyoder.recipebox.appContainer
import io.github.isaiahyoder.recipebox.settings.ThemeMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val settings = LocalContext.current.appContainer.settings
    val themeMode by settings.themeMode.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            SectionTitle("Appearance")
            Column(Modifier.selectableGroup()) {
                ThemeOption("Use phone setting", ThemeMode.SYSTEM, themeMode, settings::setThemeMode)
                ThemeOption("Light", ThemeMode.LIGHT, themeMode, settings::setThemeMode)
                ThemeOption("Dark", ThemeMode.DARK, themeMode, settings::setThemeMode)
            }
            SectionTitle("Organization")
            UpdateTagsItem()
            SectionTitle("Backups")
            BackupSection()
            SectionTitle("About")
            ListItem(
                headlineContent = { Text("Recipe Box ${BuildConfigValues.versionName(LocalContext.current)}") },
                supportingContent = { Text("Your recipes stay on this phone and in your own backups.") },
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

/** Recomputes automatic tags on every recipe, after she confirms. */
@Composable
private fun UpdateTagsItem() {
    val refresher = LocalContext.current.appContainer.tagRefresher
    val scope = rememberCoroutineScope()
    var confirming by rememberSaveable { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var result by rememberSaveable { mutableStateOf<String?>(null) }

    ListItem(
        headlineContent = { Text("Update automatic tags") },
        supportingContent = {
            Text(
                result ?: "Checks every recipe with the latest tag rules. Tags you added, tags you " +
                    "removed, and categories stay as they are."
            )
        },
        trailingContent = { if (running) CircularProgressIndicator(Modifier.size(24.dp)) },
        modifier = Modifier.clickable(enabled = !running) { confirming = true },
    )

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Update automatic tags?") },
            text = {
                Text(
                    "Automatic tags on every recipe are recalculated. Tags you added yourself, tags " +
                        "you removed, and your categories don't change."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    running = true
                    scope.launch {
                        val count = runCatching { refresher.refreshAll() }
                        running = false
                        result = count.fold(
                            onSuccess = { "Updated automatic tags on $it ${if (it == 1) "recipe" else "recipes"}." },
                            onFailure = { "Couldn't update tags. Nothing was changed." },
                        )
                    }
                }) { Text("Update") }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ThemeOption(label: String, mode: ThemeMode, selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = mode == selected, onClick = { onSelect(mode) }, role = Role.RadioButton)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = mode == selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
    }
}
