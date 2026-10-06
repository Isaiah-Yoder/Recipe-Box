package io.github.isaiahyoder.recipebox.ui.library

import io.github.isaiahyoder.recipebox.data.ShelfRecipe
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.isaiahyoder.recipebox.AppStrings
import io.github.isaiahyoder.recipebox.R
import io.github.isaiahyoder.recipebox.data.CategoryDao
import io.github.isaiahyoder.recipebox.data.CategoryEntity
import io.github.isaiahyoder.recipebox.data.RecipeDao
import io.github.isaiahyoder.recipebox.data.RecipeSummary
import io.github.isaiahyoder.recipebox.data.TagDao
import io.github.isaiahyoder.recipebox.data.UNCATEGORIZED
import io.github.isaiahyoder.recipebox.repository.LibraryRepository
import io.github.isaiahyoder.recipebox.settings.AppSettings
import io.github.isaiahyoder.recipebox.tags.FeederSuggestions
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
    val photo: ShelfRecipe?,
    /** Recipes suggested for this category that she hasn't answered yet. */
    val suggested: Int = 0,
)

/** A category with no feeder tags and the tags that fit its name, offered once after the update. */
data class FeederOffer(val category: CategoryEntity, val tags: List<String>, val adds: Int, val suggested: Int)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val dao: RecipeDao,
    tagDao: TagDao,
    categoryDao: CategoryDao,
    private val settings: AppSettings,
    private val library: LibraryRepository,
    private val strings: AppStrings,
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
        // Only ids, favorites, and photos: the home screen needs counts, not recipe text.
        dao.observeShelfRecipes(),
        categoryDao.observeCategories(),
        categoryDao.observeCategoryLinks(),
        tagDao.observeSuggestions(),
    ) { all, categories, links, suggestions ->
        if (all.isEmpty()) return@combine emptyList()
        val byId = all.associateBy { it.id }
        fun photoOf(ids: Collection<Long>) = all.firstOrNull { it.id in ids && (it.imageFile != null || it.cardPhotos.isNotEmpty()) }
        val members = links.groupBy({ it.categoryId }, { it.recipeId }).mapValues { it.value.toSet() }
        val categorized = links.map { it.recipeId }.toSet()
        buildList {
            add(HomeRow(Shelf.ALL, strings.get(R.string.library_all_recipes), all.size, photoOf(byId.keys)))
            val favorites = all.filter { it.favorite }.map { it.id }
            if (favorites.isNotEmpty()) {
                add(HomeRow(Shelf.FAVORITES, strings.get(R.string.library_favorites), favorites.size, photoOf(favorites)))
            }
            for (category in categories) {
                val ids = members[category.id].orEmpty()
                val feeders = category.feederTags.map { it.lowercase() }.toSet()
                val suggested = suggestions
                    .filter { it.tag.lowercase() in feeders && it.recipeId !in ids }
                    .map { it.recipeId }.distinct().size
                add(HomeRow(category.id, category.name, ids.size, photoOf(ids), suggested))
            }
            val loose = byId.keys - categorized
            if (loose.isNotEmpty()) {
                add(HomeRow(Shelf.UNCATEGORIZED_SHELF, strings.get(R.string.library_uncategorized), loose.size, photoOf(loose)))
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val offerSeen = MutableStateFlow(settings.feederOfferSeen)

    /** Categories she made before feeder tags existed, with tags that fit their names. */
    val feederOffers: StateFlow<List<FeederOffer>> = combine(
        categoryDao.observeCategories(),
        tagDao.observeConfirmedTags(),
        categoryDao.observeCategoryLinks(),
        tagDao.observeSuggestions(),
        offerSeen,
    ) { categories, confirmed, links, suggestions, seen ->
        if (seen) return@combine emptyList()
        val tagsInUse = confirmed.map { it.tag }.toSet()
        val members = links.groupBy({ it.categoryId }, { it.recipeId }).mapValues { it.value.toSet() }
        val suggestedTags = suggestions.map { it.tag }.toSet()
        categories.filter { it.feederTags.isEmpty() }.mapNotNull { category ->
            // A tag counts when a recipe has it or it's waiting as a suggestion, such as Thanksgiving.
            val fitting = FeederSuggestions.forCategory(category.name, tagsInUse)
                .filter { it in tagsInUse || it in suggestedTags }
            if (fitting.isEmpty()) return@mapNotNull null
            val inCategory = members[category.id].orEmpty()
            val adds = confirmed.filter { it.tag in fitting && it.recipeId !in inCategory }.map { it.recipeId }.distinct().size
            val suggested = suggestions.filter { it.tag in fitting && it.recipeId !in inCategory }.map { it.recipeId }.distinct().size
            FeederOffer(category, fitting, adds, suggested)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun acceptFeeders(chosen: List<FeederOffer>) = viewModelScope.launch {
        library.setFeederTags(chosen.associate { it.category.id to it.tags })
        dismissFeederOffer()
    }

    fun dismissFeederOffer() {
        settings.feederOfferSeen = true
        offerSeen.value = true
    }
}
