package io.github.isaiahyoder.recipebox.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.isaiahyoder.recipebox.ui.importing.ImportScreen
import io.github.isaiahyoder.recipebox.ui.library.LibraryScreen
import io.github.isaiahyoder.recipebox.ui.recipe.RecipeScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable

@Serializable data object LibraryRoute
@Serializable data class RecipeRoute(val id: Long)
@Serializable data class ImportRoute(val text: String)

@Composable
fun RecipeBoxNavHost(sharedText: MutableStateFlow<String?>) {
    val navController = rememberNavController()
    val shared by sharedText.collectAsState()

    LaunchedEffect(shared) {
        val text = shared ?: return@LaunchedEffect
        sharedText.value = null
        navController.navigate(ImportRoute(text))
    }

    NavHost(navController, startDestination = LibraryRoute) {
        composable<LibraryRoute> {
            LibraryScreen(
                onOpenRecipe = { navController.navigate(RecipeRoute(it)) },
                onImport = { navController.navigate(ImportRoute(it)) },
            )
        }
        composable<ImportRoute> { entry ->
            ImportScreen(
                text = entry.toRoute<ImportRoute>().text,
                onOpenRecipe = { id ->
                    navController.navigate(RecipeRoute(id)) {
                        popUpTo<ImportRoute> { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable<RecipeRoute> { entry ->
            RecipeScreen(
                recipeId = entry.toRoute<RecipeRoute>().id,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
