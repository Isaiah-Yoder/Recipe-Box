package io.github.isaiahyoder.recipebox.ui.grocery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.isaiahyoder.recipebox.R
import io.github.isaiahyoder.recipebox.appContainer
import io.github.isaiahyoder.recipebox.ui.categories.NameDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroceryListsScreen(onOpenList: (Long) -> Unit, onBack: () -> Unit) {
    val container = LocalContext.current.appContainer
    val vm = viewModel { GroceryListsViewModel(container.groceries) }
    val lists by vm.lists.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.grocery_lists_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.grocery_back))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.grocery_new_list)) },
            )
        },
    ) { padding ->
        val current = lists ?: return@Scaffold
        if (current.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.grocery_lists_empty),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 96.dp)) {
            items(current, key = { it.id }) { list ->
                ListItem(
                    headlineContent = { Text(list.name) },
                    supportingContent = {
                        val recipes = list.recipeCount.takeIf { it > 0 }
                            ?.let { pluralStringResource(R.plurals.grocery_lists_recipe_count, it, it) }
                        val items = list.manualCount.takeIf { it > 0 }
                            ?.let { pluralStringResource(R.plurals.grocery_lists_item_count, it, it) }
                        Text(
                            when {
                                recipes != null && items != null -> stringResource(R.string.grocery_lists_summary_both, recipes, items)
                                else -> recipes ?: items ?: stringResource(R.string.grocery_lists_summary_empty)
                            }
                        )
                    },
                    modifier = Modifier.clickable { onOpenList(list.id) },
                )
                HorizontalDivider()
            }
        }
    }

    if (creating) {
        NameDialog(stringResource(R.string.grocery_new_list_title), "", onSave = { name ->
            creating = false
            vm.create(name, onOpenList)
        }, onDismiss = { creating = false })
    }
}

/**
 * Adds a recipe to a list at the scale she's viewing it at. Adding it to a
 * list it's already on updates its scale there.
 */
@Composable
fun AddToGroceryListDialog(
    recipeId: Long,
    scale: Double,
    onDone: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val container = LocalContext.current.appContainer
    val vm = viewModel(key = "add-to-grocery-list") { GroceryListsViewModel(container.groceries) }
    val lists = vm.lists.collectAsStateWithLifecycle().value.orEmpty()
    var chosen by rememberSaveable { mutableLongStateOf(0L) }
    var newName by rememberSaveable { mutableStateOf("") }
    val selected = if (chosen == 0L) lists.firstOrNull()?.id ?: -1L else chosen
    val creatingNew = selected == -1L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.grocery_add_to_list_title)) },
        text = {
            Column {
                Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                    lists.forEach { list ->
                        Row(
                            Modifier.fillMaxWidth().clickable { chosen = list.id },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selected == list.id, onClick = { chosen = list.id })
                            Text(list.name)
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().clickable { chosen = -1L },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = creatingNew, onClick = { chosen = -1L })
                        Text(stringResource(R.string.grocery_new_list))
                    }
                }
                if (creatingNew) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text(stringResource(R.string.grocery_list_name)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !creatingNew || newName.isNotBlank(),
                onClick = { vm.addRecipe(recipeId, scale, selected, newName.takeIf { creatingNew }, onDone) },
            ) { Text(stringResource(R.string.grocery_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.grocery_cancel)) } },
    )
}
