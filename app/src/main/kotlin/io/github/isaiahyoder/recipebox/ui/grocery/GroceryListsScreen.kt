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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.isaiahyoder.recipebox.appContainer
import io.github.isaiahyoder.recipebox.data.GroceryDao
import io.github.isaiahyoder.recipebox.data.GroceryListEntity
import io.github.isaiahyoder.recipebox.data.GroceryListRecipeEntity
import io.github.isaiahyoder.recipebox.ui.categories.NameDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroceryListsScreen(onOpenList: (Long) -> Unit, onBack: () -> Unit) {
    val dao = LocalContext.current.appContainer.database.groceryDao()
    val lists by dao.observeSummaries().collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    var creating by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Grocery lists") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New list") },
            )
        },
    ) { padding ->
        val current = lists ?: return@Scaffold
        if (current.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Make a list for each trip, then add recipes to it from a recipe's menu.",
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
                        Text(
                            listOfNotNull(
                                list.recipeCount.takeIf { it > 0 }?.let { "$it ${if (it == 1) "recipe" else "recipes"}" },
                                list.manualCount.takeIf { it > 0 }?.let { "$it ${if (it == 1) "item" else "items"}" },
                            ).joinToString(" · ").ifEmpty { "Empty" }
                        )
                    },
                    modifier = Modifier.clickable { onOpenList(list.id) },
                )
                HorizontalDivider()
            }
        }
    }

    if (creating) {
        NameDialog("New grocery list", "", onSave = { name ->
            creating = false
            scope.launch {
                val now = System.currentTimeMillis()
                onOpenList(dao.insertList(GroceryListEntity(name = name, createdAt = now, updatedAt = now)))
            }
        }, onDismiss = { creating = false })
    }
}

/**
 * Adds a recipe to a list at the scale she's viewing it at. Adding it to a
 * list it's already on updates its scale there.
 */
@Composable
fun AddToGroceryListDialog(
    dao: GroceryDao,
    recipeId: Long,
    scale: Double,
    onDone: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val lists by dao.observeSummaries().collectAsStateWithLifecycle(initialValue = emptyList())
    var chosen by rememberSaveable { mutableLongStateOf(0L) }
    var newName by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val selected = if (chosen == 0L) lists.firstOrNull()?.id ?: -1L else chosen
    val creatingNew = selected == -1L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to grocery list") },
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
                        Text("New list")
                    }
                }
                if (creatingNew) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("List name") },
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
                onClick = {
                    scope.launch {
                        val now = System.currentTimeMillis()
                        val listId = if (creatingNew) {
                            dao.insertList(GroceryListEntity(name = newName.trim(), createdAt = now, updatedAt = now))
                        } else {
                            selected
                        }
                        dao.upsertRecipe(GroceryListRecipeEntity(listId, recipeId, scale, now))
                        dao.touch(listId, now)
                        onDone(if (creatingNew) newName.trim() else lists.first { it.id == listId }.name)
                    }
                },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
