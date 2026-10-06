package io.github.isaiahyoder.recipebox.ui.library

import io.github.isaiahyoder.recipebox.R
import io.github.isaiahyoder.recipebox.ui.components.UndoReports
import io.github.isaiahyoder.recipebox.ui.components.Undoable
import kotlinx.coroutines.flow.flowOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.isaiahyoder.recipebox.data.CategoryDao
import io.github.isaiahyoder.recipebox.data.CategoryEntity
import io.github.isaiahyoder.recipebox.data.RecipeDao
import io.github.isaiahyoder.recipebox.data.RecipeSummary
import io.github.isaiahyoder.recipebox.data.TagDao
import io.github.isaiahyoder.recipebox.repository.LibraryRepository
import io.github.isaiahyoder.recipebox.repository.RecipeTag
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Favorites and one tag narrow a list; both combine. */
data class ListFilter(val tag: String = "", val favoritesOnly: Boolean = false) {
    val isFiltered: Boolean get() = tag.isNotEmpty() || favoritesOnly
}

/** A recipe suggested for the category being shown, through one of its feeder tags. */
data class SuggestedRecipe(val recipe: RecipeSummary, val tag: String)

/** One shelf: every recipe, favorites, recipes in no category, or one category. */
@OptIn(ExperimentalCoroutinesApi::class)
class RecipeListViewModel(
    private val dao: RecipeDao,
    tagDao: TagDao,
    categoryDao: CategoryDao,
    private val library: LibraryRepository,
    val key: Long,
) : ViewModel() {
    private val _filter = MutableStateFlow(ListFilter())
    val filter: StateFlow<ListFilter> = _filter.asStateFlow()

    val category: StateFlow<CategoryEntity?> = categoryDao.observeCategories()
        .map { list -> list.firstOrNull { it.id == key } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val recipes: StateFlow<List<RecipeSummary>?> = _filter
        .flatMapLatest {
            dao.observeSummaries(
                query = "",
                categoryId = if (key == Shelf.FAVORITES) 0 else key,
                tag = it.tag,
                favoritesOnly = it.favoritesOnly || key == Shelf.FAVORITES,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val tagsInUse: StateFlow<List<String>> = tagDao.observeTagNamesInUse()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Recipes a feeder tag was guessed for, which she hasn't answered. */
    val suggested: StateFlow<List<SuggestedRecipe>> = combine(
        category,
        tagDao.observeSuggestions(),
        categoryDao.observeCategoryLinks(),
    ) { category, suggestions, links ->
        if (category == null || category.feederTags.isEmpty()) return@combine emptyList()
        val feeders = category.feederTags.map { it.lowercase() }.toSet()
        val members = links.filter { it.categoryId == category.id }.map { it.recipeId }.toSet()
        suggestions
            .filter { it.tag.lowercase() in feeders && it.recipeId !in members }
            .distinctBy { it.recipeId }
    }.flatMapLatest { picks ->
        // Rows for just the suggested recipes, not the whole library.
        if (picks.isEmpty()) {
            flowOf(emptyList())
        } else {
            dao.observeSummariesIn(picks.map { it.recipeId }).map { rows ->
                val byId = rows.associateBy { it.id }
                picks.mapNotNull { pick -> byId[pick.recipeId]?.let { SuggestedRecipe(it, pick.tag) } }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setTag(tag: String) = _filter.update { it.copy(tag = tag) }

    fun toggleFavorites() = _filter.update { it.copy(favoritesOnly = !it.favoritesOnly) }

    fun clearFilters() = _filter.update { ListFilter() }

    /** Yes: the suggested tag becomes hers, which adds the recipe to this category. */
    fun accept(vararg items: SuggestedRecipe) = viewModelScope.launch {
        library.acceptSuggestions(items.map { RecipeTag(it.recipe.id, it.tag) })
    }

    /** No: the suggestion is dismissed for good. */
    fun dismiss(item: SuggestedRecipe) = viewModelScope.launch {
        val link = library.dismissSuggestion(item.recipe.id, item.tag) ?: return@launch
        undo.report(Undoable(R.string.library_dismissed_suggestion, listOf(item.recipe.title)) { library.restoreTag(link) })
    }

    /** Dismissals she can take back from a snackbar. */
    val undo = UndoReports(viewModelScope)

    fun setFeeders(tagNames: List<String>) = viewModelScope.launch {
        val current = category.value ?: return@launch
        library.setFeederTags(current.id, tagNames)
    }
}
