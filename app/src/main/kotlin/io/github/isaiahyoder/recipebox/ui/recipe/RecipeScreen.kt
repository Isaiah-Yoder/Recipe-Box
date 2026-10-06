package io.github.isaiahyoder.recipebox.ui.recipe

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.isaiahyoder.recipebox.appContainer
import io.github.isaiahyoder.recipebox.ui.grocery.AddToGroceryListDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeScreen(recipeId: Long, onEdit: () -> Unit, onBack: () -> Unit) {
    val container = LocalContext.current.appContainer
    val viewModel = viewModel(key = "recipe-$recipeId") {
        val database = container.database
        RecipeViewModel(
            database.recipeDao(), database.tagDao(), database.categoryDao(),
            container.library, container.recipes, container.recipeRefresher, recipeId,
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val cookMode by viewModel.cookMode.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmWebsiteVersion by rememberSaveable { mutableStateOf(false) }
    var addingToList by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    // Cook mode keeps the screen on only while this screen is showing.
    val view = LocalView.current
    DisposableEffect(cookMode) {
        view.keepScreenOn = cookMode
        onDispose { view.keepScreenOn = false }
    }

    val recipe = (state as? RecipeUiState.Loaded)?.recipe
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (recipe != null) {
                        IconButton(onClick = { viewModel.setCookMode(!cookMode) }) {
                            Icon(
                                if (cookMode) Icons.Filled.Lightbulb else Icons.Outlined.Lightbulb,
                                contentDescription = if (cookMode) "Turn off cook mode" else "Turn on cook mode",
                                tint = if (cookMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(onClick = { viewModel.setFavorite(!recipe.favorite) }) {
                            Icon(
                                if (recipe.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = if (recipe.favorite) "Remove from favorites" else "Add to favorites",
                            )
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Add to grocery list") },
                                    leadingIcon = { Icon(Icons.Filled.ShoppingCart, null) },
                                    onClick = {
                                        menuOpen = false
                                        addingToList = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Edit recipe") },
                                    leadingIcon = { Icon(Icons.Filled.Edit, null) },
                                    onClick = {
                                        menuOpen = false
                                        onEdit()
                                    },
                                )
                                if (recipe.sourceUrl != null && recipe.editedFields.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("Use the website's version") },
                                        leadingIcon = { Icon(Icons.Filled.Restore, null) },
                                        onClick = {
                                            menuOpen = false
                                            confirmWebsiteVersion = true
                                        },
                                    )
                                }
                                recipe.sourceUrl?.let { url ->
                                    DropdownMenuItem(
                                        text = { Text("Open original page") },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                                        onClick = {
                                            menuOpen = false
                                            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Delete recipe") },
                                    leadingIcon = { Icon(Icons.Filled.Delete, null) },
                                    onClick = {
                                        menuOpen = false
                                        confirmDelete = true
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        when (state) {
            RecipeUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            RecipeUiState.Missing -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("This recipe was deleted.")
            }
            is RecipeUiState.Loaded -> RecipeContent(
                recipe = recipe!!,
                tags = tags,
                viewModel = viewModel,
                cookMode = cookMode,
                contentPadding = padding,
                onScale = viewModel::setScale,
            )
        }
    }

    if (addingToList && recipe != null) {
        AddToGroceryListDialog(
            recipeId = recipe.id,
            scale = recipe.lastScale,
            onDone = { listName ->
                addingToList = false
                Toast.makeText(context, "Added to $listName", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { addingToList = false },
        )
    }

    if (confirmWebsiteVersion && recipe != null) {
        AlertDialog(
            onDismissRequest = { confirmWebsiteVersion = false },
            title = { Text("Use the website's version?") },
            text = {
                Text(
                    "Your changes to this recipe's title, servings, times, ingredients, and steps are replaced " +
                        "with what its web page says. Your notes, tags, categories, and photos stay."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmWebsiteVersion = false
                    viewModel.useWebsiteVersion { worked ->
                        if (!worked) Toast.makeText(context, "The web page couldn't be read. Try again later.", Toast.LENGTH_LONG).show()
                    }
                }) { Text("Use website's version") }
            },
            dismissButton = { TextButton(onClick = { confirmWebsiteVersion = false }) { Text("Cancel") } },
        )
    }

    if (confirmDelete && recipe != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this recipe?") },
            text = { Text("\"${recipe.title}\" and its photo will be removed from Recipe Box.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(onDeleted = onBack)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
