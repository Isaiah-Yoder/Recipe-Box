package io.github.isaiahyoder.recipebox.ui.library

import android.content.ClipboardManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.isaiahyoder.recipebox.R
import io.github.isaiahyoder.recipebox.appContainer
import io.github.isaiahyoder.recipebox.importer.Links
import io.github.isaiahyoder.recipebox.ui.queue.QueueBanner
import kotlinx.coroutines.launch

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
        HomeViewModel(
            database.recipeDao(), database.tagDao(), database.categoryDao(), container.settings, container.library,
            container.strings,
        )
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
                title = { Text(stringResource(R.string.library_app_title)) },
                navigationIcon = {
                    IconButton(onClick = onOpenMenu) {
                        Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.library_menu))
                    }
                },
            )
        },
        bottomBar = {
            Column(Modifier.navigationBarsPadding()) {
                container.distribution.UpdateBanner()
                QueueBanner(onOpenQueue)
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.library_add_recipe)) },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SearchField(query, viewModel::setQuery)
            when {
                query.isNotBlank() -> RecipeList(
                    recipes = results,
                    empty = stringResource(R.string.library_search_no_match, query.trim()),
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
internal fun SearchField(query: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text(stringResource(R.string.library_search_hint)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.library_search_clear))
                }
            }
        },
        singleLine = true,
    )
}

/** A category or built-in list on the home screen, with a photo from one of its recipes. */
@Composable
internal fun ShelfRow(row: HomeRow, onClick: () -> Unit) {
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
            photo?.let { (it.imageFile ?: it.cardPhotos.firstOrNull())?.let(container.photos::file) },
            size = 56.dp,
        )
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(row.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    pluralStringResource(R.plurals.library_shelf_count, row.count, row.count),
                    row.suggested.takeIf { it > 0 }?.let { stringResource(R.string.library_shelf_suggested, it) },
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
internal fun FeederOfferCard(offers: List<FeederOffer>, onAccept: (List<FeederOffer>) -> Unit, onDismiss: () -> Unit) {
    val chosen = remember(offers) { mutableStateListOf(*offers.toTypedArray()) }
    val orSeparator = stringResource(R.string.library_or_separator)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.library_offer_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.library_offer_body), style = MaterialTheme.typography.bodyMedium)
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
                        offer.adds.takeIf { it > 0 }?.let { stringResource(R.string.library_offer_adds, it) },
                        offer.suggested.takeIf { it > 0 }?.let { stringResource(R.string.library_offer_suggested, it) },
                    )
                    val tags = offer.tags.joinToString(orSeparator)
                    Text(
                        if (effects.isEmpty()) {
                            stringResource(R.string.library_offer_line, offer.category.name, tags)
                        } else {
                            stringResource(R.string.library_offer_line_effects, offer.category.name, tags, effects.joinToString(", "))
                        },
                        Modifier.padding(start = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_offer_not_now)) }
                TextButton(onClick = { onAccept(chosen.toList()) }, enabled = chosen.isNotEmpty()) {
                    Text(stringResource(R.string.library_offer_fill))
                }
            }
        }
    }
}

@Composable
internal fun EmptyLibrary() {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            stringResource(R.string.library_empty),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Takes one or more pasted links and adds them all to the import queue. */
@Composable
internal fun AddRecipeDialog(
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
        title = { Text(stringResource(R.string.library_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.library_add_links_label)) },
                    placeholder = { Text(stringResource(R.string.library_add_links_hint)) },
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
                    Text(stringResource(R.string.library_add_paste), Modifier.padding(start = 8.dp))
                }
                TextButton(onClick = onScanCard) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null)
                    Text(stringResource(R.string.library_add_scan_card), Modifier.padding(start = 8.dp))
                }
                TextButton(onClick = onTypeIn) {
                    Icon(Icons.Filled.Edit, contentDescription = null)
                    Text(stringResource(R.string.library_add_type_in), Modifier.padding(start = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(links) }, enabled = links.isNotEmpty()) {
                Text(
                    when (links.size) {
                        0, 1 -> stringResource(R.string.library_add_recipe)
                        else -> pluralStringResource(R.plurals.library_add_recipes, links.size, links.size)
                    }
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_cancel)) } },
    )
}
