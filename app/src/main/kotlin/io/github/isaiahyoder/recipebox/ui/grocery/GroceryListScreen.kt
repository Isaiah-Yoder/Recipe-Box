package io.github.isaiahyoder.recipebox.ui.grocery

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import io.github.isaiahyoder.recipebox.ui.components.UndoSnackbars
import io.github.isaiahyoder.recipebox.ui.components.UnitToggle
import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.isaiahyoder.recipebox.R
import io.github.isaiahyoder.recipebox.appContainer
import io.github.isaiahyoder.recipebox.data.GroceryRecipeRow
import io.github.isaiahyoder.recipebox.grocery.GroceryLine
import io.github.isaiahyoder.recipebox.grocery.StoreSection
import io.github.isaiahyoder.recipebox.ingredients.Fractions
import io.github.isaiahyoder.recipebox.ui.categories.NameDialog

/** Scales a recipe can have on a list. */
val groceryScales = listOf(0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 4.0)

/** The section's name as the app shows it. */
@get:StringRes
val StoreSection.labelRes: Int
    get() = when (this) {
        StoreSection.PRODUCE -> R.string.grocery_section_produce
        StoreSection.MEAT_SEAFOOD -> R.string.grocery_section_meat_seafood
        StoreSection.DAIRY_EGGS -> R.string.grocery_section_dairy_eggs
        StoreSection.BAKERY -> R.string.grocery_section_bakery
        StoreSection.PANTRY -> R.string.grocery_section_pantry
        StoreSection.SPICES -> R.string.grocery_section_spices
        StoreSection.FROZEN -> R.string.grocery_section_frozen
        StoreSection.OTHER -> R.string.grocery_section_other
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroceryListScreen(listId: Long, onOpenRecipe: (Long) -> Unit, onBack: () -> Unit) {
    val container = LocalContext.current.appContainer
    val vm = viewModel(key = "grocery-$listId") { GroceryListViewModel(container.database.groceryDao(), listId) }
    val list by vm.list.collectAsStateWithLifecycle()
    val recipes by vm.recipes.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var editingKey by rememberSaveable { mutableStateOf<String?>(null) }
    var newItem by rememberSaveable { mutableStateOf("") }
    val snackbars = remember { SnackbarHostState() }
    UndoSnackbars(vm.undo, snackbars)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbars) },
        topBar = {
            TopAppBar(
                title = { Text(list?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.grocery_back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val text = vm.shareText(context.getString(R.string.grocery_default_name)) { context.getString(it.labelRes) }
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                        context.startActivity(Intent.createChooser(send, context.getString(R.string.grocery_share_chooser)))
                    }) { Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.grocery_share_list)) }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.grocery_more_options))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.grocery_hide_staples)) },
                                trailingIcon = { Checkbox(checked = list?.hideStaples == true, onCheckedChange = null) },
                                onClick = {
                                    menuOpen = false
                                    vm.setHideStaples(list?.hideStaples != true)
                                },
                            )
                            DropdownMenuItem(text = { Text(stringResource(R.string.grocery_uncheck_all)) }, onClick = {
                                menuOpen = false
                                vm.uncheckAll()
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.grocery_rename_list)) }, onClick = {
                                menuOpen = false
                                renaming = true
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.grocery_delete_list)) }, onClick = {
                                menuOpen = false
                                confirmDelete = true
                            })
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 48.dp)) {
            list?.let { current ->
                item(key = "units") {
                    UnitToggle(current.units, vm::setUnits, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newItem,
                        onValueChange = { newItem = it },
                        placeholder = { Text(stringResource(R.string.grocery_add_item_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            vm.addItem(newItem)
                            newItem = ""
                        }),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = {
                        vm.addItem(newItem)
                        newItem = ""
                    }, enabled = newItem.isNotBlank()) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.grocery_add_item))
                    }
                }
            }
            if (recipes.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.grocery_recipes_on_list)) }
                items(recipes, key = { "recipe-${it.recipeId}" }) { row ->
                    RecipeOnList(row, onOpen = { onOpenRecipe(row.recipeId) }, onScale = { vm.setScale(row.recipeId, it) }, onRemove = { vm.removeRecipe(row.recipeId, row.title) })
                }
            }
            val current = groups
            if (current != null && current.isEmpty() && recipes.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.grocery_list_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            current.orEmpty().forEach { group ->
                item(key = "section-${group.section}") { SectionTitle(stringResource(group.section.labelRes)) }
                items(group.lines, key = { it.key }) { line ->
                    LineRow(line, onToggle = { vm.toggle(line) }, onEdit = { editingKey = line.key })
                }
            }
        }
    }

    groups.orEmpty().flatMap { it.lines }.firstOrNull { it.key == editingKey }?.let { line ->
        EditLineDialog(
            line = line,
            onSave = { text, section ->
                editingKey = null
                vm.edit(line, text, section)
            },
            onDelete = {
                editingKey = null
                vm.delete(line)
            },
            onDismiss = { editingKey = null },
        )
    }
    if (renaming) {
        NameDialog(
            stringResource(R.string.grocery_rename_list),
            list?.name.orEmpty(),
            onSave = { renaming = false; vm.rename(it) },
            onDismiss = { renaming = false },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.grocery_delete_title)) },
            text = { Text(stringResource(R.string.grocery_delete_message, list?.name.orEmpty())) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deleteList(onBack) }) { Text(stringResource(R.string.grocery_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.grocery_cancel)) } },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun RecipeOnList(row: GroceryRecipeRow, onOpen: () -> Unit, onScale: (Double) -> Unit, onRemove: () -> Unit) {
    var scaleMenu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(row.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Box {
            AssistChip(onClick = { scaleMenu = true }, label = { Text(stringResource(R.string.grocery_scale, Fractions.format(row.scale))) })
            DropdownMenu(expanded = scaleMenu, onDismissRequest = { scaleMenu = false }) {
                groceryScales.forEach { scale ->
                    DropdownMenuItem(text = { Text(stringResource(R.string.grocery_scale, Fractions.format(scale))) }, onClick = {
                        scaleMenu = false
                        onScale(scale)
                    })
                }
            }
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.grocery_remove_recipe, row.title))
        }
    }
}

@Composable
private fun LineRow(line: GroceryLine, onToggle: () -> Unit, onEdit: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = line.checked, onCheckedChange = { onToggle() }, modifier = Modifier.padding(start = 4.dp))
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(
                line.text,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (line.checked) TextDecoration.LineThrough else null,
                color = if (line.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            if (line.sources.isNotEmpty()) {
                Text(
                    line.sources.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    HorizontalDivider(Modifier.padding(start = 56.dp))
}

@Composable
private fun EditLineDialog(
    line: GroceryLine,
    onSave: (String, StoreSection) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(line.text) }
    var section by rememberSaveable { mutableStateOf(line.section) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.grocery_edit_item_title)) },
        text = {
            Column {
                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text(stringResource(R.string.grocery_item_label)) })
                Text(stringResource(R.string.grocery_store_section), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                StoreSection.entries.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().clickable { section = option },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = section == option, onClick = { section = option })
                        Text(stringResource(option.labelRes))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text, section) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.grocery_save)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text(stringResource(R.string.grocery_delete), color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.grocery_cancel)) }
            }
        },
    )
}
