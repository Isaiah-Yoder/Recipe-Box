package io.github.isaiahyoder.recipebox.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.isaiahyoder.recipebox.AppStrings
import io.github.isaiahyoder.recipebox.R
import io.github.isaiahyoder.recipebox.data.CategoryDao
import io.github.isaiahyoder.recipebox.data.CategoryEntity
import io.github.isaiahyoder.recipebox.data.TagDao
import io.github.isaiahyoder.recipebox.repository.LibraryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Her categories: creating, renaming, ordering, deleting, and choosing the tags that fill them. */
class CategoriesViewModel(
    categoryDao: CategoryDao,
    tagDao: TagDao,
    private val library: LibraryRepository,
) : ViewModel() {
    /** Null until the first load finishes, so the empty-list message doesn't flash. */
    val categories: StateFlow<List<CategoryEntity>?> = categoryDao.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val tagsInUse: StateFlow<List<String>> = tagDao.observeTagNamesInUse()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun create(name: String) = viewModelScope.launch { library.createCategory(name) }

    /** A name another category has is refused; the dialog already says so. */
    fun rename(id: Long, name: String) = viewModelScope.launch { library.renameCategory(id, name) }

    fun delete(id: Long) = viewModelScope.launch { library.deleteCategory(id) }

    /** Moves a category up ([delta] -1) or down (+1) in her list. */
    fun move(index: Int, delta: Int) {
        val ids = categories.value.orEmpty().map { it.id }.toMutableList()
        val target = index + delta
        if (index !in ids.indices || target !in ids.indices) return
        ids.add(target, ids.removeAt(index))
        viewModelScope.launch { library.reorderCategories(ids) }
    }

    fun setFeeders(id: Long, tagNames: List<String>) = viewModelScope.launch { library.setFeederTags(id, tagNames) }

    companion object {
        /** Category names must be unique, ignoring case. */
        fun takenMessage(categories: List<CategoryEntity>, name: String, exceptId: Long?, strings: AppStrings): String? =
            if (categories.any { it.id != exceptId && it.name.equals(name.trim(), ignoreCase = true) }) {
                strings.get(R.string.library_category_taken, name.trim())
            } else {
                null
            }
    }
}
