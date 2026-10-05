package io.github.isaiahyoder.recipebox.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.isaiahyoder.recipebox.data.CategoryEntity
import io.github.isaiahyoder.recipebox.data.RecipeDao
import io.github.isaiahyoder.recipebox.data.RecipeSummary
import io.github.isaiahyoder.recipebox.data.UNCATEGORIZED
import io.github.isaiahyoder.recipebox.settings.AppSettings
import io.github.isaiahyoder.recipebox.tags.CategoryRules
import io.github.isaiahyoder.recipebox.tags.TagRefresher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Home rows that aren't categories. Category rows use the category's id. */
object Shelf {
    const val ALL = 0L
    const val UNCATEGORIZED_SHELF = UNCATEGORIZED
    const val FAVORITES = -2L
}

/** One row on the home screen: a category or a built-in list, with a photo from one of its recipes. */
data class HomeRow(
    val key: Long,
    val title: String,
    val count: Int,
    val photo: RecipeSummary?,
    /** Recipes suggested for this category that she hasn't answered yet. */
    val suggested: Int = 0,
)

/** A category with no feeder tags and the tags that fit its name, offered once after the update. */
data class FeederOffer(val category: CategoryEntity, val tags: List<String>, val adds: Int, val suggested: Int)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val dao: RecipeDao,
    private val settings: AppSettings,
    private val tags: TagRefresher,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    fun setQuery(value: String) {
        _query.value = value
    }

    /** Search results across every recipe; empty while the search box is empty. */
    val results: StateFlow<List<RecipeSummary>> = _query
        .debounce(150)
        .flatMapLatest { text -> if (text.isBlank()) flowOf(emptyList()) else dao.observeSummaries(text.trim()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Null until the first load finishes, so the empty-library message doesn't flash. */
    val rows: StateFlow<List<HomeRow>?> = combine(
        dao.observeSummaries(""),
        dao.observeCategories(),
        dao.observeCategoryLinks(),
        dao.observeSuggestions(),
    ) { all, categories, links, suggestions ->
        if (all.isEmpty()) return@combine emptyList()
        val byId = all.associateBy { it.id }
        fun photoOf(ids: Collection<Long>) = all.firstOrNull { it.id in ids && (it.imageFile != null || it.cardPhotos.isNotEmpty()) }
        val members = links.groupBy({ it.categoryId }, { it.recipeId }).mapValues { it.value.toSet() }
        val categorized = links.map { it.recipeId }.toSet()
        buildList {
            add(HomeRow(Shelf.ALL, "All recipes", all.size, photoOf(byId.keys)))
            val favorites = all.filter { it.favorite }.map { it.id }
            if (favorites.isNotEmpty()) add(HomeRow(Shelf.FAVORITES, "Favorites", favorites.size, photoOf(favorites)))
            for (category in categories) {
                val ids = members[category.id].orEmpty()
                val feeders = category.feederTags.map { it.lowercase() }.toSet()
                val suggested = suggestions
                    .filter { it.tag.lowercase() in feeders && it.recipeId !in ids }
                    .map { it.recipeId }.distinct().size
                add(HomeRow(category.id, category.name, ids.size, photoOf(ids), suggested))
            }
            val loose = byId.keys - categorized
            if (loose.isNotEmpty()) add(HomeRow(Shelf.UNCATEGORIZED_SHELF, "Not in a category", loose.size, photoOf(loose)))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val offerSeen = MutableStateFlow(settings.feederOfferSeen)

    /** Categories she made before feeder tags existed, with tags that fit their names. */
    val feederOffers: StateFlow<List<FeederOffer>> = combine(
        dao.observeCategories(),
        dao.observeConfirmedTags(),
        dao.observeCategoryLinks(),
        dao.observeSuggestions(),
        offerSeen,
    ) { categories, confirmed, links, suggestions, seen ->
        if (seen) return@combine emptyList()
        val tagsInUse = confirmed.map { it.tag }.toSet()
        val members = links.groupBy({ it.categoryId }, { it.recipeId }).mapValues { it.value.toSet() }
        val suggestedTags = suggestions.map { it.tag }.toSet()
        categories.filter { it.feederTags.isEmpty() }.mapNotNull { category ->
            // A tag counts when a recipe has it or it's waiting as a suggestion, such as Thanksgiving.
            val fitting = CategoryRules.suggestedFeeders(category.name, tagsInUse)
                .filter { it in tagsInUse || it in suggestedTags }
            if (fitting.isEmpty()) return@mapNotNull null
            val inCategory = members[category.id].orEmpty()
            val adds = confirmed.filter { it.tag in fitting && it.recipeId !in inCategory }.map { it.recipeId }.distinct().size
            val suggested = suggestions.filter { it.tag in fitting && it.recipeId !in inCategory }.map { it.recipeId }.distinct().size
            FeederOffer(category, fitting, adds, suggested)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun acceptFeeders(chosen: List<FeederOffer>) = viewModelScope.launch {
        chosen.forEach { dao.setFeederTags(it.category.id, it.tags) }
        tags.applyCategories()
        dismissFeederOffer()
    }

    fun dismissFeederOffer() {
        settings.feederOfferSeen = true
        offerSeen.value = true
    }
}

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
    private val tags: TagRefresher,
    val key: Long,
) : ViewModel() {
    private val _filter = MutableStateFlow(ListFilter())
    val filter: StateFlow<ListFilter> = _filter.asStateFlow()

    val category: StateFlow<CategoryEntity?> = dao.observeCategories()
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

    val tagsInUse: StateFlow<List<String>> = dao.observeTagNamesInUse()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Recipes a feeder tag was guessed for, which she hasn't answered. */
    val suggested: StateFlow<List<SuggestedRecipe>> = combine(
        category,
        dao.observeSuggestions(),
        dao.observeCategoryLinks(),
        dao.observeSummaries(""),
    ) { category, suggestions, links, all ->
        if (category == null || category.feederTags.isEmpty()) return@combine emptyList()
        val feeders = category.feederTags.map { it.lowercase() }.toSet()
        val members = links.filter { it.categoryId == category.id }.map { it.recipeId }.toSet()
        val byId = all.associateBy { it.id }
        suggestions
            .filter { it.tag.lowercase() in feeders && it.recipeId !in members }
            .distinctBy { it.recipeId }
            .mapNotNull { suggestion -> byId[suggestion.recipeId]?.let { SuggestedRecipe(it, suggestion.tag) } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setTag(tag: String) = _filter.update { it.copy(tag = tag) }

    fun toggleFavorites() = _filter.update { it.copy(favoritesOnly = !it.favoritesOnly) }

    fun clearFilters() = _filter.update { ListFilter() }

    /** Yes: the suggested tag becomes hers, which adds the recipe to this category. */
    fun accept(vararg items: SuggestedRecipe) = viewModelScope.launch {
        items.forEach { dao.acceptSuggestion(it.recipe.id, it.tag) }
        tags.applyCategories()
    }

    /** No: the suggestion is dismissed for good. */
    fun dismiss(item: SuggestedRecipe) = viewModelScope.launch { dao.removeTag(item.recipe.id, item.tag) }

    fun setFeeders(tagNames: List<String>) = viewModelScope.launch {
        val current = category.value ?: return@launch
        dao.setFeederTags(current.id, tagNames)
        tags.applyCategories()
    }
}
