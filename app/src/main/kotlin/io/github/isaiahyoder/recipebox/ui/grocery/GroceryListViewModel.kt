package io.github.isaiahyoder.recipebox.ui.grocery

import io.github.isaiahyoder.recipebox.R
import io.github.isaiahyoder.recipebox.ui.components.UndoReports
import io.github.isaiahyoder.recipebox.ui.components.Undoable
import io.github.isaiahyoder.recipebox.ingredients.UnitSystem
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.isaiahyoder.recipebox.data.GroceryDao
import io.github.isaiahyoder.recipebox.data.GroceryListEntity
import io.github.isaiahyoder.recipebox.data.GroceryManualItemEntity
import io.github.isaiahyoder.recipebox.data.GroceryRecipeRow
import io.github.isaiahyoder.recipebox.data.SectionOverrideEntity
import io.github.isaiahyoder.recipebox.grocery.GroceryBuilder
import io.github.isaiahyoder.recipebox.grocery.GroceryLine
import io.github.isaiahyoder.recipebox.grocery.GroceryLineStateInput
import io.github.isaiahyoder.recipebox.grocery.GroceryManualInput
import io.github.isaiahyoder.recipebox.grocery.GroceryRecipeInput
import io.github.isaiahyoder.recipebox.grocery.GrocerySectionGroup
import io.github.isaiahyoder.recipebox.grocery.StoreSection
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One grocery list, rebuilt whenever its recipes, items, or her changes change. */
class GroceryListViewModel(private val dao: GroceryDao, private val listId: Long) : ViewModel() {
    /** Removals she can take back from a snackbar. */
    val undo = UndoReports(viewModelScope)

    val list: StateFlow<GroceryListEntity?> = dao.observeList(listId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val recipes: StateFlow<List<GroceryRecipeRow>> = dao.observeRecipes(listId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Null until the first build, so the empty message doesn't flash. */
    val groups: StateFlow<List<GrocerySectionGroup>?> = combine(
        dao.observeList(listId),
        dao.observeRecipes(listId),
        dao.observeManualItems(listId),
        dao.observeLineStates(listId),
        dao.observeSectionOverrides(),
    ) { list, recipes, manual, states, overrides ->
        GroceryBuilder.build(
            recipes = recipes.map { row ->
                GroceryRecipeInput(row.title, row.scale, row.ingredients.filterNot { it.isHeader }.map { it.text })
            },
            manual = manual.map { GroceryManualInput(it.id, it.text, StoreSection.fromName(it.section), it.checked) },
            states = states.associate { it.lineKey to GroceryLineStateInput(it.checked, it.hidden, it.customText) },
            sectionOverrides = overrides.mapNotNull { o -> StoreSection.fromName(o.section)?.let { o.nameKey to it } }.toMap(),
            hideStaples = list?.hideStaples ?: false,
            units = list?.units ?: UnitSystem.US,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun toggle(line: GroceryLine) = viewModelScope.launch {
        val manualId = line.manualId
        if (manualId != null) {
            dao.setManualChecked(manualId, !line.checked)
        } else {
            dao.updateLineState(listId, line.key) { it.copy(checked = !line.checked) }
        }
    }

    /**
     * Saves her changes to a line. A changed section is remembered for that
     * ingredient on every list.
     */
    fun edit(line: GroceryLine, text: String, section: StoreSection) = viewModelScope.launch {
        val manualId = line.manualId
        if (manualId != null) {
            dao.updateManualItem(manualId, text.trim(), section.name)
        } else {
            if (text.trim() != line.text) {
                dao.updateLineState(listId, line.key) { it.copy(customText = text.trim().ifBlank { null }) }
            }
        }
        if (section != line.section) dao.setSectionOverride(SectionOverrideEntity(line.nameKey, section.name))
    }

    fun delete(line: GroceryLine) = viewModelScope.launch {
        val manualId = line.manualId
        if (manualId != null) {
            val item = dao.getManualItem(manualId) ?: return@launch
            dao.deleteManualItem(manualId)
            undo.report(Undoable(R.string.grocery_removed_item, listOf(line.text)) { dao.insertManualItem(item) })
        } else {
            dao.updateLineState(listId, line.key) { it.copy(hidden = true) }
            undo.report(Undoable(R.string.grocery_removed_item, listOf(line.text)) {
                dao.updateLineState(listId, line.key) { it.copy(hidden = false) }
            })
        }
    }

    fun addItem(text: String) = viewModelScope.launch {
        if (text.isBlank()) return@launch
        dao.insertManualItem(GroceryManualItemEntity(listId = listId, text = text.trim(), createdAt = System.currentTimeMillis()))
        dao.touch(listId, System.currentTimeMillis())
    }

    fun setScale(recipeId: Long, scale: Double) = viewModelScope.launch { dao.setRecipeScale(listId, recipeId, scale) }

    fun removeRecipe(recipeId: Long, title: String) = viewModelScope.launch {
        val link = dao.getRecipeLink(listId, recipeId) ?: return@launch
        dao.removeRecipe(listId, recipeId)
        undo.report(Undoable(R.string.grocery_removed_recipe, listOf(title)) { dao.upsertRecipe(link) })
    }

    fun rename(name: String) = viewModelScope.launch { dao.renameList(listId, name.trim(), System.currentTimeMillis()) }

    fun setHideStaples(hide: Boolean) = viewModelScope.launch { dao.setHideStaples(listId, hide) }

    fun setUnits(units: UnitSystem) = viewModelScope.launch { dao.setUnits(listId, units) }

    fun uncheckAll() = viewModelScope.launch {
        val checkedLines = dao.getLineStates(listId).filter { it.checked }.map { it.lineKey }
        val checkedItems = dao.getManualItems(listId).filter { it.checked }.map { it.id }
        if (checkedLines.isEmpty() && checkedItems.isEmpty()) return@launch
        dao.uncheckAll(listId)
        undo.report(Undoable(R.string.grocery_unchecked_all) { dao.checkAgain(listId, checkedLines, checkedItems) })
    }

    fun deleteList(onDeleted: () -> Unit) = viewModelScope.launch {
        dao.deleteList(listId)
        onDeleted()
    }

    /** The list as text to share, named [defaultName] if it has no name yet, with [sectionLabel] naming each section. */
    fun shareText(defaultName: String, sectionLabel: (StoreSection) -> String): String =
        GroceryBuilder.shareText(list.value?.name ?: defaultName, groups.value.orEmpty(), sectionLabel)
}
