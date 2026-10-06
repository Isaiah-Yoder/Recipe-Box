package io.github.isaiahyoder.recipebox.ui.grocery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.isaiahyoder.recipebox.data.GroceryListSummary
import io.github.isaiahyoder.recipebox.repository.GroceryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The grocery lists, for the lists screen and for adding a recipe to one. */
class GroceryListsViewModel(private val groceries: GroceryRepository) : ViewModel() {
    /** Null until the first load finishes. */
    val lists: StateFlow<List<GroceryListSummary>?> = groceries.observeLists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun create(name: String, onCreated: (Long) -> Unit) = viewModelScope.launch {
        onCreated(groceries.createList(name))
    }

    /** Adds the recipe, creating the list first when [newListName] is given, then reports the list's name. */
    fun addRecipe(recipeId: Long, scale: Double, listId: Long, newListName: String?, onAdded: (String) -> Unit) =
        viewModelScope.launch {
            val id = groceries.addRecipe(recipeId, scale, listId, newListName)
            onAdded(newListName?.trim() ?: lists.value.orEmpty().firstOrNull { it.id == id }?.name.orEmpty())
        }
}
