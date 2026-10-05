package io.github.isaiahyoder.recipebox.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.isaiahyoder.recipebox.appContainer
import io.github.isaiahyoder.recipebox.data.ImportStatus
import io.github.isaiahyoder.recipebox.ui.cards.CardScanScreen
import io.github.isaiahyoder.recipebox.ui.categories.CategoriesScreen
import io.github.isaiahyoder.recipebox.ui.editor.RecipeEditorScreen
import io.github.isaiahyoder.recipebox.ui.grocery.GroceryListScreen
import io.github.isaiahyoder.recipebox.ui.grocery.GroceryListsScreen
import io.github.isaiahyoder.recipebox.ui.library.LibraryScreen
import io.github.isaiahyoder.recipebox.ui.queue.QueueScreen
import io.github.isaiahyoder.recipebox.ui.recipe.RecipeScreen
import io.github.isaiahyoder.recipebox.ui.settings.SettingsScreen
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable data object LibraryRoute
@Serializable data class RecipeRoute(val id: Long)
@Serializable data object QueueRoute
@Serializable data object SettingsRoute
@Serializable data object CategoriesRoute
@Serializable data object GroceryListsRoute
@Serializable data class GroceryListRoute(val id: Long)
@Serializable data class RecipeEditRoute(
    val recipeId: Long = 0,
    val sourceUrl: String? = null,
    val importJobId: Long = 0,
    val cardDraftId: Long = 0,
)
@Serializable data object CardScanRoute

@Composable
fun RecipeBoxNavHost() {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStack by navController.currentBackStackEntryAsState()
    val onLibrary = backStack?.destination?.hasRoute<LibraryRoute>() != false

    ModalNavigationDrawer(
        drawerState = drawerState,
        // The menu opens from the library; other screens use their back arrow.
        gesturesEnabled = onLibrary || drawerState.isOpen,
        drawerContent = {
            AppDrawer(
                navController = navController,
                onClose = { scope.launch { drawerState.close() } },
            )
        },
    ) {
        NavHost(navController, startDestination = LibraryRoute) {
            composable<LibraryRoute> {
                LibraryScreen(
                    onOpenRecipe = { navController.navigate(RecipeRoute(it)) },
                    onOpenQueue = { navController.navigate(QueueRoute) },
                    onOpenMenu = { scope.launch { drawerState.open() } },
                    onNewRecipe = { navController.navigate(RecipeEditRoute()) },
                    onScanCard = { navController.navigate(CardScanRoute) },
                )
            }
            composable<CardScanRoute> {
                CardScanScreen(
                    onDraft = { draftId ->
                        navController.navigate(RecipeEditRoute(cardDraftId = draftId)) { popUpTo<CardScanRoute> { inclusive = true } }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<QueueRoute> {
                QueueScreen(
                    onOpenRecipe = { navController.navigate(RecipeRoute(it)) },
                    onEnterManually = { url, jobId -> navController.navigate(RecipeEditRoute(sourceUrl = url, importJobId = jobId)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<CategoriesRoute> {
                CategoriesScreen(onBack = { navController.popBackStack() })
            }
            composable<GroceryListsRoute> {
                GroceryListsScreen(
                    onOpenList = { navController.navigate(GroceryListRoute(it)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<GroceryListRoute> { entry ->
                GroceryListScreen(
                    listId = entry.toRoute<GroceryListRoute>().id,
                    onOpenRecipe = { navController.navigate(RecipeRoute(it)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<SettingsRoute> {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
            composable<RecipeRoute> { entry ->
                val id = entry.toRoute<RecipeRoute>().id
                RecipeScreen(
                    recipeId = id,
                    onEdit = { navController.navigate(RecipeEditRoute(recipeId = id)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<RecipeEditRoute> { entry ->
                val route = entry.toRoute<RecipeEditRoute>()
                RecipeEditorScreen(
                    recipeId = route.recipeId,
                    sourceUrl = route.sourceUrl,
                    importJobId = route.importJobId,
                    cardDraftId = route.cardDraftId,
                    onSaved = { id ->
                        if (route.recipeId == 0L) {
                            // A new recipe opens after saving; Back returns to where she started.
                            navController.navigate(RecipeRoute(id)) { popUpTo<RecipeEditRoute> { inclusive = true } }
                        } else {
                            navController.popBackStack()
                        }
                    },
                    onCancel = { navController.popBackStack() },
                )
            }
        }
    }
}

@Composable
private fun AppDrawer(navController: NavHostController, onClose: () -> Unit) {
    val jobs by LocalContext.current.appContainer.database.importJobDao().observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val failed = jobs.count { it.status == ImportStatus.FAILED }
    val active = jobs.count { it.status == ImportStatus.PENDING || it.status == ImportStatus.RUNNING }

    fun go(route: Any) {
        onClose()
        navController.navigate(route) {
            popUpTo<LibraryRoute>()
            launchSingleTop = true
        }
    }

    ModalDrawerSheet {
        Text(
            "Recipe Box",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
        )
        NavigationDrawerItem(
            label = { Text("Recipes") },
            icon = { Icon(Icons.Filled.MenuBook, contentDescription = null) },
            selected = false,
            onClick = { go(LibraryRoute) },
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        NavigationDrawerItem(
            label = { Text("Grocery lists") },
            icon = { Icon(Icons.Filled.ShoppingCart, contentDescription = null) },
            selected = false,
            onClick = { go(GroceryListsRoute) },
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        NavigationDrawerItem(
            label = { Text("Categories") },
            icon = { Icon(Icons.Filled.Folder, contentDescription = null) },
            selected = false,
            onClick = { go(CategoriesRoute) },
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        NavigationDrawerItem(
            label = { Text("Import queue") },
            icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
            badge = {
                when {
                    failed > 0 -> Badge(containerColor = MaterialTheme.colorScheme.error) { Text("$failed") }
                    active > 0 -> Badge { Text("$active") }
                }
            },
            selected = false,
            onClick = { go(QueueRoute) },
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        NavigationDrawerItem(
            label = { Text("Settings") },
            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            selected = false,
            onClick = { go(SettingsRoute) },
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}
