package io.github.isaiahyoder.recipebox.ui.recipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.isaiahyoder.recipebox.data.CategoryEntity
import io.github.isaiahyoder.recipebox.data.RecipeDao
import io.github.isaiahyoder.recipebox.data.RecipeEntity
import io.github.isaiahyoder.recipebox.importer.RecipeRefresher
import io.github.isaiahyoder.recipebox.photos.PhotoStore
import io.github.isaiahyoder.recipebox.tags.TagRefresher
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
    private val tagRefresher: TagRefresher,
    private val refresher: RecipeRefresher,
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

    /** Guessed tags, such as an occasion, waiting for her yes or no. */
    val suggestions: StateFlow<List<String>> = dao.observeSuggestedTags(recipeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** A tag she adds is hers: updating automatic tags never removes it. It can fill categories. */
    fun addTag(name: String) = viewModelScope.launch {
        if (name.isNotBlank()) {
            dao.addManualTag(recipeId, name.trim())
            tagRefresher.applyCategories()
        }
    }

    /** Removed tags stay removed, even automatic ones the rules would add again. */
    fun removeTag(name: String) = viewModelScope.launch {
        dao.removeTag(recipeId, name)
        tagRefresher.applyCategories()
    }

    fun acceptSuggestion(name: String) = viewModelScope.launch {
        dao.acceptSuggestion(recipeId, name)
        tagRefresher.applyCategories()
    }

    /** A dismissed suggestion stays dismissed, like a removed tag. */
    fun dismissSuggestion(name: String) = viewModelScope.launch { dao.removeTag(recipeId, name) }

    /**
     * Creates a category filled by [tag], or adds [tag] as a feeder to the
     * category with that name, so recipes with the tag join it now and later.
     */
    fun makeCategory(tag: String) = viewModelScope.launch {
        val id = dao.createCategory(tag)
        val category = dao.getCategories().first { it.id == id }
        dao.setFeederTags(id, (category.feederTags + tag).distinct())
        tagRefresher.applyCategories()
    }

    /** Drops her edits and uses what the recipe's web page says. */
    fun useWebsiteVersion(onDone: (Boolean) -> Unit) = viewModelScope.launch {
        onDone(refresher.useWebsiteVersion(recipeId))
    }

    fun saveCategories(ids: Set<Long>) = viewModelScope.launch { dao.setRecipeCategories(recipeId, ids) }

    suspend fun createCategory(name: String): Long = dao.createCategory(name)

    private val _cookMode = MutableStateFlow(false)
    /** Keeps the screen on while she cooks. It isn't saved; it ends when she leaves the recipe. */
    val cookMode: StateFlow<Boolean> = _cookMode.asStateFlow()

    fun setScale(scale: Double) = viewModelScope.launch { dao.setLastScale(recipeId, scale) }

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
