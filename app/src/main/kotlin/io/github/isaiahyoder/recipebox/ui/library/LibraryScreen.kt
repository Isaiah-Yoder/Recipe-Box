package io.github.isaiahyoder.recipebox.ui.library

import android.content.ClipboardManager
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.semantics.Role
import io.github.isaiahyoder.recipebox.ui.categories.FeederDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.InputChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import io.github.isaiahyoder.recipebox.appContainer
import io.github.isaiahyoder.recipebox.data.RecipeSummary
import io.github.isaiahyoder.recipebox.importer.Links
import io.github.isaiahyoder.recipebox.ui.queue.QueueBanner
import io.github.isaiahyoder.recipebox.ui.update.UpdateBanner
import kotlinx.coroutines.launch
import io.github.isaiahyoder.recipebox.ui.formatMinutes
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenRecipe: (Long) -> Unit,
    onOpenShelf: (Long) -> Unit,
    onOpenQueue: () -> Unit,
    onOpenMenu: () -> Unit,
    onNewRecipe: () -> Unit,
    onScanCard: () -> Unit,
) {
    val container = LocalContext.current.appContainer
    val viewModel = viewModel {
        val database = container.database
        HomeViewModel(database.recipeDao(), database.tagDao(), database.categoryDao(), container.settings, container.library)
    }
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val offers by viewModel.feederOffers.collectAsStateWithLifecycle()
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    // Owned by the screen, not the dialog: closing the dialog must not cancel adding the links.
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recipe Box") },
                navigationIcon = {
                    IconButton(onClick = onOpenMenu) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menu")
                    }
                },
            )
        },
        bottomBar = {
            Column(Modifier.navigationBarsPadding()) {
                UpdateBanner()
                QueueBanner(onOpenQueue)
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add recipe") },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SearchField(query, viewModel::setQuery)
            when {
                query.isNotBlank() -> RecipeList(
                    recipes = results,
                    empty = "No recipes match \"${query.trim()}\".",
                    onOpenRecipe = onOpenRecipe,
                )
                rows == null -> Unit
                rows!!.isEmpty() -> EmptyLibrary()
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (offers.isNotEmpty()) {
                        item(key = "offer") {
                            FeederOfferCard(offers, onAccept = viewModel::acceptFeeders, onDismiss = viewModel::dismissFeederOffer)
                        }
                    }
                    items(rows!!, key = { it.key }) { row -> ShelfRow(row, onClick = { onOpenShelf(row.key) }) }
                }
            }
        }
    }

    if (showAddDialog) {
        AddRecipeDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { links ->
                showAddDialog = false
                scope.launch { container.importQueue.enqueue(links) }
            },
            onTypeIn = {
                showAddDialog = false
                onNewRecipe()
            },
            onScanCard = {
                showAddDialog = false
                onScanCard()
            },
        )
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text("Search recipes, ingredients, or tags") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
    )
}

@Composable
private fun RecipeList(recipes: List<RecipeSummary>?, empty: String, onOpenRecipe: (Long) -> Unit) {
    when {
        recipes == null -> Unit
        recipes.isEmpty() -> Text(empty, Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        else -> LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(recipes, key = { it.id }) { recipe -> RecipeRow(recipe, onClick = { onOpenRecipe(recipe.id) }) }
        }
    }
}

/** A category or built-in list on the home screen, with a photo from one of its recipes. */
@Composable
private fun ShelfRow(row: HomeRow, onClick: () -> Unit) {
    val container = LocalContext.current.appContainer
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val photo = row.photo
        RecipeThumbnail(
            photo?.let { container.photos.existing(it.imageFile) ?: container.photos.existing(it.cardPhotos.firstOrNull()) },
            size = 56.dp,
        )
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(row.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    "${row.count} ${if (row.count == 1) "recipe" else "recipes"}",
                    row.suggested.takeIf { it > 0 }?.let { "$it suggested" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = if (row.suggested > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Offers, once, to fill the categories she made by hand from tags that fit their names. */
@Composable
private fun FeederOfferCard(offers: List<FeederOffer>, onAccept: (List<FeederOffer>) -> Unit, onDismiss: () -> Unit) {
    val chosen = remember(offers) { mutableStateListOf(*offers.toTypedArray()) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Fill your categories automatically?", style = MaterialTheme.typography.titleSmall)
            Text(
                "Recipes with a matching tag can join your categories on their own, now and in the future. " +
                    "Recipes you already sorted stay where they are.",
                style = MaterialTheme.typography.bodyMedium,
            )
            for (offer in offers) {
                val checked = offer in chosen
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(checked, role = Role.Checkbox) { on -> if (on) chosen += offer else chosen -= offer },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = checked, onCheckedChange = null)
                    val effects = listOfNotNull(
                        offer.adds.takeIf { it > 0 }?.let { "adds $it" },
                        offer.suggested.takeIf { it > 0 }?.let { "$it suggested to review" },
                    )
                    Text(
                        "${offer.category.name} from ${offer.tags.joinToString(" or ")}" +
                            if (effects.isNotEmpty()) " (${effects.joinToString(", ")})" else "",
                        Modifier.padding(start = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Not now") }
                TextButton(onClick = { onAccept(chosen.toList()) }, enabled = chosen.isNotEmpty()) { Text("Fill these") }
            }
        }
    }
}

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
        Shelf.ALL -> "All recipes"
        Shelf.FAVORITES -> "Favorites"
        Shelf.UNCATEGORIZED_SHELF -> "Not in a category"
        else -> category?.name ?: ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (category != null) {
                        IconButton(onClick = { editingFeeders = true }) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = "Fill from tags")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            category?.let { current ->
                Text(
                    if (current.feederTags.isEmpty()) "You add recipes to this category yourself."
                    else "Fills automatically from ${current.feederTags.joinToString(" or ")}.",
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
                                "Suggested for $title",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { viewModel.accept(*suggested.toTypedArray()) }) { Text("Add all") }
                        }
                    }
                    items(suggested, key = { "s-${it.recipe.id}" }) { item ->
                        Column {
                            RecipeRow(item.recipe, onClick = { onOpenRecipe(item.recipe.id) })
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { viewModel.dismiss(item) }) { Text("No") }
                                TextButton(onClick = { viewModel.accept(item) }) { Text("Add") }
                            }
                        }
                    }
                    item(key = "members-heading") {
                        Text(
                            "In $title",
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
                                if (filter.isFiltered) "No recipes match these filters." else "No recipes here yet.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (filter.isFiltered) TextButton(onClick = viewModel::clearFilters) { Text("Clear filters") }
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
private fun FilterRow(
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
                    label = { Text("Favorites") },
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
                        trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Clear tag filter", Modifier.size(18.dp)) },
                    )
                } else {
                    FilterChip(
                        selected = false,
                        onClick = { tagMenuOpen = true },
                        label = { Text("Tag") },
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

@Composable
private fun RecipeRow(recipe: RecipeSummary, onClick: () -> Unit) {
    val container = LocalContext.current.appContainer
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RecipeThumbnail(container.photos.existing(recipe.imageFile) ?: container.photos.existing(recipe.cardPhotos.firstOrNull()))
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(
                recipe.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val details = listOfNotNull(recipe.siteName, formatMinutes(recipe.totalMinutes))
            if (details.isNotEmpty()) {
                Text(
                    details.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val tags = recipe.tagNames?.split('|').orEmpty().sorted()
            if (tags.isNotEmpty()) {
                Text(
                    tags.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (recipe.favorite) {
            Icon(
                Icons.Filled.Favorite,
                contentDescription = "Favorite",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp).size(20.dp),
            )
        }
    }
}

@Composable
private fun RecipeThumbnail(file: File?, size: androidx.compose.ui.unit.Dp = 72.dp) {
    val shape = RoundedCornerShape(10.dp)
    if (file != null) {
        AsyncImage(
            model = file,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(shape),
        )
    } else {
        Box(
            Modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Restaurant, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyLibrary() {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            "No recipes yet.\n\nIn your browser, open a recipe and tap Share, then Recipe Box. " +
                "Or tap Add recipe to paste a link or scan a recipe card.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Takes one or more pasted links and adds them all to the import queue. */
@Composable
private fun AddRecipeDialog(
    onDismiss: () -> Unit,
    onAdd: (List<String>) -> Unit,
    onTypeIn: () -> Unit,
    onScanCard: () -> Unit,
) {
    val context = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }
    val links = remember(text) { Links.findAllUrls(text) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add recipes") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Recipe links") },
                    placeholder = { Text("Paste one or more links") },
                    minLines = 3,
                    maxLines = 8,
                )
                TextButton(onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    val pasted = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                    if (!pasted.isNullOrBlank()) {
                        text = if (text.isBlank()) pasted else text.trimEnd() + System.lineSeparator() + pasted
                    }
                }) {
                    Icon(Icons.Filled.ContentPaste, contentDescription = null)
                    Text("Paste", Modifier.padding(start = 8.dp))
                }
                TextButton(onClick = onScanCard) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null)
                    Text("Scan a recipe card", Modifier.padding(start = 8.dp))
                }
                TextButton(onClick = onTypeIn) {
                    Icon(Icons.Filled.Edit, contentDescription = null)
                    Text("Type in a recipe instead", Modifier.padding(start = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(links) }, enabled = links.isNotEmpty()) {
                Text(
                    when (links.size) {
                        0, 1 -> "Add recipe"
                        else -> "Add ${links.size} recipes"
                    }
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
