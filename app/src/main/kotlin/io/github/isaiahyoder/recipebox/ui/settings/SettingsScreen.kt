package io.github.isaiahyoder.recipebox.ui.settings

import io.github.isaiahyoder.recipebox.ingredients.UnitSystem
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.isaiahyoder.recipebox.BuildConfigValues
import io.github.isaiahyoder.recipebox.R
import io.github.isaiahyoder.recipebox.appContainer
import io.github.isaiahyoder.recipebox.settings.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val settings = LocalContext.current.appContainer.settings
    val themeMode by settings.themeMode.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            SectionTitle(stringResource(R.string.settings_section_appearance))
            Column(Modifier.selectableGroup()) {
                ThemeOption(stringResource(R.string.settings_theme_system), ThemeMode.SYSTEM, themeMode, settings::setThemeMode)
                ThemeOption(stringResource(R.string.settings_theme_light), ThemeMode.LIGHT, themeMode, settings::setThemeMode)
                ThemeOption(stringResource(R.string.settings_theme_dark), ThemeMode.DARK, themeMode, settings::setThemeMode)
            }
            SectionTitle(stringResource(R.string.settings_section_units))
            Column(Modifier.selectableGroup()) {
                val units by settings.unitSystem.collectAsStateWithLifecycle()
                UnitOption(stringResource(R.string.settings_units_us), UnitSystem.US, units, settings::setUnitSystem)
                UnitOption(stringResource(R.string.settings_units_metric), UnitSystem.METRIC, units, settings::setUnitSystem)
            }
            Text(
                stringResource(R.string.settings_units_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            SectionTitle(stringResource(R.string.settings_section_organization))
            RefreshRecipesItem()
            SectionTitle(stringResource(R.string.settings_section_recipe_cards))
            CardReadingSection()
            SectionTitle(stringResource(R.string.settings_section_backups))
            BackupSection()
            SectionTitle(stringResource(R.string.settings_section_about))
            ListItem(
                headlineContent = {
                    Text(stringResource(R.string.settings_about_version, BuildConfigValues.versionName(LocalContext.current)))
                },
                supportingContent = { Text(stringResource(R.string.settings_about_privacy)) },
            )
            LocalContext.current.appContainer.distribution.SettingsItem()
            StallReportsItem()
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

@Composable
private fun UnitOption(label: String, units: UnitSystem, selected: UnitSystem, onSelect: (UnitSystem) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = units == selected, onClick = { onSelect(units) }, role = Role.RadioButton)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = units == selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
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
