package io.github.isaiahyoder.recipebox.ui.recipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.isaiahyoder.recipebox.data.CategoryEntity
import io.github.isaiahyoder.recipebox.data.CategoryDao
import io.github.isaiahyoder.recipebox.data.RecipeDao
import io.github.isaiahyoder.recipebox.data.RecipeEntity
import io.github.isaiahyoder.recipebox.data.TagDao
import io.github.isaiahyoder.recipebox.importer.RecipeRefresher
import io.github.isaiahyoder.recipebox.repository.LibraryRepository
import io.github.isaiahyoder.recipebox.repository.RecipeRepository
import io.github.isaiahyoder.recipebox.repository.RecipeTag
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
    tagDao: TagDao,
    categoryDao: CategoryDao,
    private val library: LibraryRepository,
    private val recipes: RecipeRepository,
    private val refresher: RecipeRefresher,
    private val recipeId: Long,
) : ViewModel() {
    val state: StateFlow<RecipeUiState> = dao.observeRecipe(recipeId)
        .map { recipe -> if (recipe == null) RecipeUiState.Missing else RecipeUiState.Loaded(recipe) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipeUiState.Loading)

    val tags: StateFlow<List<String>> = tagDao.observeTagNames(recipeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> = categoryDao.observeRecipeCategories(recipeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allCategories: StateFlow<List<CategoryEntity>> = categoryDao.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allTags: StateFlow<List<String>> = tagDao.observeTagNamesInUse()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Guessed tags, such as an occasion, waiting for her yes or no. */
    val suggestions: StateFlow<List<String>> = tagDao.observeSuggestedTags(recipeId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addTag(name: String) = viewModelScope.launch { library.addTag(recipeId, name) }

    fun removeTag(name: String) = viewModelScope.launch { library.removeTag(recipeId, name) }

    fun acceptSuggestion(name: String) = viewModelScope.launch { library.acceptSuggestions(listOf(RecipeTag(recipeId, name))) }

    fun dismissSuggestion(name: String) = viewModelScope.launch { library.dismissSuggestion(recipeId, name) }

    fun makeCategory(tag: String) = viewModelScope.launch { library.makeCategory(tag) }

    /** Drops her edits and uses what the recipe's web page says. */
    fun useWebsiteVersion(onDone: (Boolean) -> Unit) = viewModelScope.launch {
        onDone(refresher.useWebsiteVersion(recipeId))
    }

    fun saveCategories(ids: Set<Long>) = viewModelScope.launch { library.setRecipeCategories(recipeId, ids) }

    suspend fun createCategory(name: String): Long = library.createCategory(name)

    private val _cookMode = MutableStateFlow(false)
    /** Keeps the screen on while she cooks. It isn't saved; it ends when she leaves the recipe. */
    val cookMode: StateFlow<Boolean> = _cookMode.asStateFlow()

    fun setScale(scale: Double) = viewModelScope.launch { dao.setLastScale(recipeId, scale) }

    fun setFavorite(favorite: Boolean) = viewModelScope.launch { dao.setFavorite(recipeId, favorite) }

    fun setCookMode(on: Boolean) {
        _cookMode.value = on
    }

    fun delete(onDeleted: () -> Unit) = viewModelScope.launch {
        recipes.delete(recipeId)
        onDeleted()
    }
}
