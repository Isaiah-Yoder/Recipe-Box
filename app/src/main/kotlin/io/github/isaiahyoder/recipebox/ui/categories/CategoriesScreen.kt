package io.github.isaiahyoder.recipebox.ui.categories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.isaiahyoder.recipebox.R
import io.github.isaiahyoder.recipebox.appContainer
import io.github.isaiahyoder.recipebox.data.CategoryEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(onBack: () -> Unit) {
    val container = LocalContext.current.appContainer
    val vm = viewModel {
        CategoriesViewModel(container.database.categoryDao(), container.database.tagDao(), container.library)
    }
    val loaded by vm.categories.collectAsStateWithLifecycle()
    val categories = loaded.orEmpty()
    val tagsInUse by vm.tagsInUse.collectAsStateWithLifecycle()
    var editingFeeders by rememberSaveable { mutableStateOf<Long?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleting by rememberSaveable { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.library_categories)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.library_back))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.library_category_new)) },
            )
        },
    ) { padding ->
        if (loaded == null) return@Scaffold
        if (categories.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.library_categories_empty),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 96.dp)) {
            itemsIndexed(categories, key = { _, it -> it.id }) { index, category ->
                ListItem(
                    headlineContent = { Text(category.name) },
                    supportingContent = {
                        Text(
                            if (category.feederTags.isEmpty()) {
                                stringResource(R.string.library_category_by_hand)
                            } else {
                                stringResource(
                                    R.string.library_category_fills_from,
                                    category.feederTags.joinToString(stringResource(R.string.library_or_separator)),
                                )
                            }
                        )
                    },
                    modifier = Modifier.clickable { editingFeeders = category.id },
                    trailingContent = {
                        androidx.compose.foundation.layout.Row {
                            IconButton(onClick = { vm.move(index, -1) }, enabled = index > 0) {
                                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.library_category_move_up))
                            }
                            IconButton(onClick = { vm.move(index, 1) }, enabled = index < categories.lastIndex) {
                                Icon(
                                    Icons.Filled.KeyboardArrowDown,
                                    contentDescription = stringResource(R.string.library_category_move_down),
                                )
                            }
                            IconButton(onClick = { renaming = category.id }) {
                                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.library_category_rename, category.name))
                            }
                            IconButton(onClick = { deleting = category.id }) {
                                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.library_category_delete, category.name))
                            }
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }

    categories.firstOrNull { it.id == editingFeeders }?.let { category ->
        FeederDialog(
            category = category,
            tagsInUse = tagsInUse,
            onSave = { tags ->
                editingFeeders = null
                vm.setFeeders(category.id, tags)
            },
            onDismiss = { editingFeeders = null },
        )
    }

    if (creating) {
        NameDialog(
            title = stringResource(R.string.library_category_new),
            initial = "",
            onSave = { name ->
                creating = false
                vm.create(name)
            },
            onDismiss = { creating = false },
            problem = { name -> CategoriesViewModel.takenMessage(categories, name, exceptId = null, container.strings) },
        )
    }
    categories.firstOrNull { it.id == renaming }?.let { category ->
        NameDialog(
            title = stringResource(R.string.library_category_rename_title),
            initial = category.name,
            onSave = { name ->
                renaming = null
                vm.rename(category.id, name)
            },
            onDismiss = { renaming = null },
            problem = { name -> CategoriesViewModel.takenMessage(categories, name, exceptId = category.id, container.strings) },
        )
    }
    categories.firstOrNull { it.id == deleting }?.let { category: CategoryEntity ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.library_category_delete_title, category.name)) },
            text = { Text(stringResource(R.string.library_category_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    vm.delete(category.id)
                }) { Text(stringResource(R.string.library_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.library_cancel)) } },
        )
    }
}

@Composable
fun NameDialog(
    title: String,
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    /** Explains why a name can't be used, such as one already taken, or null when it can. */
    problem: (String) -> String? = { null },
) {
    var name by rememberSaveable { mutableStateOf(initial) }
    val issue = problem(name.trim())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.library_category_name)) },
                singleLine = true,
                isError = issue != null,
                supportingText = issue?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank() && issue == null) {
                Text(stringResource(R.string.library_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_cancel)) } },
    )
}
