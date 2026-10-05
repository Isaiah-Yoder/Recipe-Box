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
import io.github.isaiahyoder.recipebox.data.CategoryEntity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(onBack: () -> Unit) {
    val container = LocalContext.current.appContainer
    val dao = container.database.recipeDao()
    val categories by dao.observeCategories().collectAsStateWithLifecycle(initialValue = emptyList())
    val tagsInUse by dao.observeTagNamesInUse().collectAsStateWithLifecycle(initialValue = emptyList())
    var editingFeeders by rememberSaveable { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()
    var creating by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleting by rememberSaveable { mutableStateOf<Long?>(null) }

    fun move(index: Int, delta: Int) {
        val ids = categories.map { it.id }.toMutableList()
        val target = index + delta
        if (target !in ids.indices) return
        ids.add(target, ids.removeAt(index))
        scope.launch { dao.reorderCategories(ids) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categories") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New category") },
            )
        },
    ) { padding ->
        if (categories.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Group recipes your own way, such as Weeknight dinners or Holidays. " +
                        "A recipe can be in several categories, and a category can fill itself from tags.",
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
                            if (category.feederTags.isEmpty()) "Filled by hand. Tap to fill from tags."
                            else "Fills from ${category.feederTags.joinToString(" or ")}"
                        )
                    },
                    modifier = Modifier.clickable { editingFeeders = category.id },
                    trailingContent = {
                        androidx.compose.foundation.layout.Row {
                            IconButton(onClick = { move(index, -1) }, enabled = index > 0) {
                                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
                            }
                            IconButton(onClick = { move(index, 1) }, enabled = index < categories.lastIndex) {
                                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
                            }
                            IconButton(onClick = { renaming = category.id }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Rename ${category.name}")
                            }
                            IconButton(onClick = { deleting = category.id }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete ${category.name}")
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
                scope.launch {
                    dao.setFeederTags(category.id, tags)
                    container.tagRefresher.applyCategories()
                }
            },
            onDismiss = { editingFeeders = null },
        )
    }

    if (creating) {
        NameDialog(
            title = "New category",
            initial = "",
            onSave = { name ->
                creating = false
                scope.launch { dao.createCategory(name) }
            },
            onDismiss = { creating = false },
        )
    }
    categories.firstOrNull { it.id == renaming }?.let { category ->
        NameDialog(
            title = "Rename category",
            initial = category.name,
            onSave = { name ->
                renaming = null
                scope.launch { dao.renameCategory(category.id, name.trim()) }
            },
            onDismiss = { renaming = null },
        )
    }
    categories.firstOrNull { it.id == deleting }?.let { category: CategoryEntity ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${category.name}\"?") },
            text = { Text("The category is removed. Its recipes stay in your library.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch { dao.deleteCategory(category.id) }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
fun NameDialog(title: String, initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
