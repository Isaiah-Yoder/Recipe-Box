package io.github.isaiahyoder.recipebox.ui.grocery

import io.github.isaiahyoder.recipebox.ui.recipe.UnitToggle
import android.content.Intent
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.isaiahyoder.recipebox.appContainer
import io.github.isaiahyoder.recipebox.data.GroceryRecipeRow
import io.github.isaiahyoder.recipebox.grocery.GroceryLine
import io.github.isaiahyoder.recipebox.grocery.StoreSection
import io.github.isaiahyoder.recipebox.ingredients.Fractions
import io.github.isaiahyoder.recipebox.ui.categories.NameDialog

/** Scales a recipe can have on a list. */
val groceryScales = listOf(0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 4.0)

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(list?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, vm.shareText())
                        context.startActivity(Intent.createChooser(send, "Share grocery list"))
                    }) { Icon(Icons.Filled.Share, contentDescription = "Share list") }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More options") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Hide pantry staples") },
                                trailingIcon = { Checkbox(checked = list?.hideStaples == true, onCheckedChange = null) },
                                onClick = {
                                    menuOpen = false
                                    vm.setHideStaples(list?.hideStaples != true)
                                },
                            )
                            DropdownMenuItem(text = { Text("Uncheck everything") }, onClick = {
                                menuOpen = false
                                vm.uncheckAll()
                            })
                            DropdownMenuItem(text = { Text("Rename list") }, onClick = {
                                menuOpen = false
                                renaming = true
                            })
                            DropdownMenuItem(text = { Text("Delete list") }, onClick = {
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
                        placeholder = { Text("Add an item, such as paper towels") },
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
                    }, enabled = newItem.isNotBlank()) { Icon(Icons.Filled.Add, contentDescription = "Add item") }
                }
            }
            if (recipes.isNotEmpty()) {
                item { SectionTitle("Recipes on this list") }
                items(recipes, key = { "recipe-${it.recipeId}" }) { row ->
                    RecipeOnList(row, onOpen = { onOpenRecipe(row.recipeId) }, onScale = { vm.setScale(row.recipeId, it) }, onRemove = { vm.removeRecipe(row.recipeId) })
                }
            }
            val current = groups
            if (current != null && current.isEmpty() && recipes.isEmpty()) {
                item {
                    Text(
                        "This list is empty. Open a recipe and choose Add to grocery list, or type an item above.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            current.orEmpty().forEach { group ->
                item(key = "section-${group.section}") { SectionTitle(group.section.label) }
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
        NameDialog("Rename list", list?.name.orEmpty(), onSave = { renaming = false; vm.rename(it) }, onDismiss = { renaming = false })
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this list?") },
            text = { Text("\"${list?.name.orEmpty()}\" is removed. Your recipes aren't affected.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteList(onBack) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
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
            AssistChip(onClick = { scaleMenu = true }, label = { Text("${Fractions.format(row.scale)}×") })
            DropdownMenu(expanded = scaleMenu, onDismissRequest = { scaleMenu = false }) {
                groceryScales.forEach { scale ->
                    DropdownMenuItem(text = { Text("${Fractions.format(scale)}×") }, onClick = {
                        scaleMenu = false
                        onScale(scale)
                    })
                }
            }
        }
        IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, contentDescription = "Remove ${row.title} from the list") }
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
        title = { Text("Edit item") },
        text = {
            Column {
                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Item") })
                Text("Store section", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                StoreSection.entries.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().clickable { section = option },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = section == option, onClick = { section = option })
                        Text(option.label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text, section) }, enabled = text.isNotBlank()) { Text("Save") } },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
