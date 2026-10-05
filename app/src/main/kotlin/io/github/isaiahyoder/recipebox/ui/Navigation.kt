package io.github.isaiahyoder.recipebox.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
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
                )
            }
            composable<QueueRoute> {
                QueueScreen(
                    onOpenRecipe = { navController.navigate(RecipeRoute(it)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<SettingsRoute> {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
            composable<RecipeRoute> { entry ->
                RecipeScreen(
                    recipeId = entry.toRoute<RecipeRoute>().id,
                    onBack = { navController.popBackStack() },
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
