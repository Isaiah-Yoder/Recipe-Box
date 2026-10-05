package io.github.isaiahyoder.recipebox.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.isaiahyoder.recipebox.data.CategoryEntity
import io.github.isaiahyoder.recipebox.data.RecipeDao
import io.github.isaiahyoder.recipebox.data.RecipeSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** Search text plus filters. Filters combine: a category and a tag together narrow the list. */
data class LibraryFilter(
    val query: String = "",
    val categoryId: Long = 0,
    val tag: String = "",
    val favoritesOnly: Boolean = false,
) {
    val isFiltered: Boolean get() = query.isNotBlank() || categoryId != 0L || tag.isNotEmpty() || favoritesOnly
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class LibraryViewModel(private val dao: RecipeDao) : ViewModel() {
    private val _filter = MutableStateFlow(LibraryFilter())
    val filter: StateFlow<LibraryFilter> = _filter.asStateFlow()

    /** Null until the first load finishes, so the empty-library message doesn't flash. */
    val recipes: StateFlow<List<RecipeSummary>?> = _filter
        .debounce(150)
        .flatMapLatest { dao.observeSummaries(it.query.trim(), it.categoryId, it.tag, it.favoritesOnly) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val categories: StateFlow<List<CategoryEntity>> = dao.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tags: StateFlow<List<String>> = dao.observeTagNamesInUse()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(value: String) = _filter.update { it.copy(query = value) }

    /** Selecting the selected category again clears it. */
    fun toggleCategory(id: Long) = _filter.update { it.copy(categoryId = if (it.categoryId == id) 0 else id) }

    fun setTag(tag: String) = _filter.update { it.copy(tag = tag) }

    fun toggleFavorites() = _filter.update { it.copy(favoritesOnly = !it.favoritesOnly) }

    fun clearFilters() = _filter.update { LibraryFilter(query = it.query) }
}
