package io.github.isaiahyoder.recipebox.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.isaiahyoder.recipebox.R
import io.github.isaiahyoder.recipebox.appContainer
import io.github.isaiahyoder.recipebox.ui.categories.FeederDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeListScreen(key: Long, onOpenRecipe: (Long) -> Unit, onBack: () -> Unit) {
    val container = LocalContext.current.appContainer
    val viewModel = viewModel(key = "shelf-$key") {
        RecipeListViewModel(
            container.database.recipeDao(), container.database.tagDao(), container.database.categoryDao(),
            container.library, key,
        )
    }
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val recipes by viewModel.recipes.collectAsStateWithLifecycle()
    val category by viewModel.category.collectAsStateWithLifecycle()
    val tagsInUse by viewModel.tagsInUse.collectAsStateWithLifecycle()
    val suggested by viewModel.suggested.collectAsStateWithLifecycle()
    var editingFeeders by rememberSaveable { mutableStateOf(false) }

    val title = when (key) {
        Shelf.ALL -> stringResource(R.string.library_all_recipes)
        Shelf.FAVORITES -> stringResource(R.string.library_favorites)
        Shelf.UNCATEGORIZED_SHELF -> stringResource(R.string.library_uncategorized)
        else -> category?.name ?: ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.library_back))
                    }
                },
                actions = {
                    if (category != null) {
                        IconButton(onClick = { editingFeeders = true }) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = stringResource(R.string.library_list_fill_from_tags))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            category?.let { current ->
                Text(
                    if (current.feederTags.isEmpty()) {
                        stringResource(R.string.library_list_filled_by_hand)
                    } else {
                        stringResource(
                            R.string.library_list_fills_from,
                            current.feederTags.joinToString(stringResource(R.string.library_or_separator)),
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            FilterRow(
                filter = filter,
                tags = tagsInUse,
                showFavorites = key != Shelf.FAVORITES,
                onToggleFavorites = viewModel::toggleFavorites,
                onSetTag = viewModel::setTag,
            )
            val list = recipes
            if (list == null) return@Column
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (suggested.isNotEmpty()) {
                    item(key = "suggested-heading") {
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.library_list_suggested_for, title),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { viewModel.accept(*suggested.toTypedArray()) }) {
                                Text(stringResource(R.string.library_list_add_all))
                            }
                        }
                    }
                    items(suggested, key = { "s-${it.recipe.id}" }) { item ->
                        Column {
                            RecipeRow(item.recipe, onClick = { onOpenRecipe(item.recipe.id) })
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { viewModel.dismiss(item) }) { Text(stringResource(R.string.library_no)) }
                                TextButton(onClick = { viewModel.accept(item) }) { Text(stringResource(R.string.library_add)) }
                            }
                        }
                    }
                    item(key = "members-heading") {
                        Text(
                            stringResource(R.string.library_list_in, title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                }
                if (list.isEmpty()) {
                    item(key = "empty") {
                        Column(Modifier.padding(vertical = 16.dp)) {
                            Text(
                                stringResource(if (filter.isFiltered) R.string.library_list_no_match else R.string.library_list_empty),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (filter.isFiltered) {
                                TextButton(onClick = viewModel::clearFilters) { Text(stringResource(R.string.library_list_clear_filters)) }
                            }
                        }
                    }
                }
                items(list, key = { it.id }) { recipe -> RecipeRow(recipe, onClick = { onOpenRecipe(recipe.id) }) }
            }
        }
    }

    val current = category
    if (editingFeeders && current != null) {
        FeederDialog(
            category = current,
            tagsInUse = tagsInUse,
            onSave = { tags ->
                editingFeeders = false
                viewModel.setFeeders(tags)
            },
            onDismiss = { editingFeeders = false },
        )
    }
}

/** Favorites and one tag; both narrow the list. */
@Composable
internal fun FilterRow(
    filter: ListFilter,
    tags: List<String>,
    showFavorites: Boolean,
    onToggleFavorites: () -> Unit,
    onSetTag: (String) -> Unit,
) {
    var tagMenuOpen by remember { mutableStateOf(false) }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showFavorites) {
            item {
                FilterChip(
                    selected = filter.favoritesOnly,
                    onClick = onToggleFavorites,
                    label = { Text(stringResource(R.string.library_favorites)) },
                    leadingIcon = { Icon(Icons.Filled.Favorite, contentDescription = null, Modifier.size(18.dp)) },
                )
            }
        }
        item {
            Box {
                if (filter.tag.isNotEmpty()) {
                    InputChip(
                        selected = true,
                        onClick = { onSetTag("") },
                        label = { Text(filter.tag) },
                        trailingIcon = {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.library_list_clear_tag),
                                Modifier.size(18.dp),
                            )
                        },
                    )
                } else {
                    FilterChip(
                        selected = false,
                        onClick = { tagMenuOpen = true },
                        label = { Text(stringResource(R.string.library_list_tag)) },
                        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null, Modifier.size(18.dp)) },
                        enabled = tags.isNotEmpty(),
                    )
                }
                DropdownMenu(expanded = tagMenuOpen, onDismissRequest = { tagMenuOpen = false }) {
                    tags.forEach { tag ->
                        DropdownMenuItem(text = { Text(tag) }, onClick = {
                            tagMenuOpen = false
                            onSetTag(tag)
                        })
                    }
                }
            }
        }
    }
}
