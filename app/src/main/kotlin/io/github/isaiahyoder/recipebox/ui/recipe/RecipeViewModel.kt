package io.github.isaiahyoder.recipebox.ui.recipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.isaiahyoder.recipebox.data.CategoryEntity
import io.github.isaiahyoder.recipebox.data.RecipeDao
import io.github.isaiahyoder.recipebox.data.RecipeEntity
import io.github.isaiahyoder.recipebox.photos.PhotoStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface RecipeUiState {
    data object Loading : RecipeUiState
    data object Missing : RecipeUiState
    data class Loaded(val recipe: RecipeEntity) : RecipeUiState
}

class RecipeViewModel(
    private val dao: RecipeDao,
    private val photos: PhotoStore,
    private val recipeId: Long,
) : ViewModel() {
    val state: StateFlow<RecipeUiState> = dao.observeRecipe(recipeId)
        .map { recipe -> if (recipe == null) RecipeUiState.Missing else RecipeUiState.Loaded(recipe) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipeUiState.Loading)

    val tags: StateFlow<List<String>> = dao.observeTagNames(recipeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> = dao.observeRecipeCategories(recipeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allCategories: StateFlow<List<CategoryEntity>> = dao.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allTags: StateFlow<List<String>> = dao.observeTagNamesInUse()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** A tag she adds is hers: updating automatic tags never removes it. */
    fun addTag(name: String) = viewModelScope.launch {
        if (name.isNotBlank()) dao.addManualTag(recipeId, name.trim())
    }

    /** Removed tags stay removed, even automatic ones the rules would add again. */
    fun removeTag(name: String) = viewModelScope.launch { dao.removeTag(recipeId, name) }

    fun saveCategories(ids: Set<Long>) = viewModelScope.launch { dao.setRecipeCategories(recipeId, ids) }

    suspend fun createCategory(name: String): Long = dao.createCategory(name)

    private val _cookMode = MutableStateFlow(false)
    /** Keeps the screen on while she cooks. It isn't saved; it ends when she leaves the recipe. */
    val cookMode: StateFlow<Boolean> = _cookMode.asStateFlow()

    fun setScale(scale: Double) = viewModelScope.launch { dao.setLastScale(recipeId, scale) }

    fun setShowUsUnits(show: Boolean) = viewModelScope.launch { dao.setShowUsUnits(recipeId, show) }

    fun setFavorite(favorite: Boolean) = viewModelScope.launch { dao.setFavorite(recipeId, favorite) }

    fun setCookMode(on: Boolean) {
        _cookMode.value = on
    }

    fun delete(onDeleted: () -> Unit) = viewModelScope.launch {
        val recipe = dao.getRecipe(recipeId)
        dao.delete(recipeId)
        photos.delete(recipe?.imageFile)
        onDeleted()
    }
}
