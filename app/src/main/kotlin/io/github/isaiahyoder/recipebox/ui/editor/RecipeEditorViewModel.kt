package io.github.isaiahyoder.recipebox.ui.editor

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.isaiahyoder.recipebox.cards.CardDrafts
import io.github.isaiahyoder.recipebox.cards.CardReaderKind
import io.github.isaiahyoder.recipebox.data.RecipeDao
import io.github.isaiahyoder.recipebox.data.RecipeEntity
import io.github.isaiahyoder.recipebox.data.RecipeText
import io.github.isaiahyoder.recipebox.importer.ImportQueue
import io.github.isaiahyoder.recipebox.photos.PhotoStore
import io.github.isaiahyoder.recipebox.data.EditedField
import io.github.isaiahyoder.recipebox.tags.TagRefresher
import kotlinx.coroutines.launch

/**
 * Edits an existing recipe, or creates one when [recipeId] is 0. A recipe
 * started from a failed import keeps its link, and saving it removes the
 * failed entry from the queue. A recipe read from card photos starts from
 * the draft [cardDraftId] names.
 */
class RecipeEditorViewModel(
    private val dao: RecipeDao,
    private val photos: PhotoStore,
    private val queue: ImportQueue,
    private val tags: TagRefresher,
    private val recipeId: Long,
    initialSourceUrl: String?,
    private val importJobId: Long,
    private val cardDraftId: Long = 0,
) : ViewModel() {
    var loaded by mutableStateOf(recipeId == 0L)
        private set
    var title by mutableStateOf("")
    var servings by mutableStateOf("")
    var prepMinutes by mutableStateOf("")
    var cookMinutes by mutableStateOf("")
    var totalMinutes by mutableStateOf("")
    var ingredients by mutableStateOf("")
    var steps by mutableStateOf("")
    var notes by mutableStateOf("")
    var sourceUrl by mutableStateOf(initialSourceUrl.orEmpty())

    /** The saved photo's file name, if any. */
    var photoFile by mutableStateOf<String?>(null)
        private set

    /** A photo she just chose, saved when she taps Save. */
    var newPhoto by mutableStateOf<Uri?>(null)
        private set
    var removePhoto by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
        private set

    /** Card photos, front first. Removed photos are deleted when she saves. */
    var cardPhotos by mutableStateOf<List<String>>(emptyList())
        private set
    var addingCardPhoto by mutableStateOf(false)
        private set

    /** Which reader filled in a card draft, and what didn't work first; null otherwise. */
    var readBy by mutableStateOf<CardReaderKind?>(null)
        private set
    var readProblems by mutableStateOf<List<String>>(emptyList())
        private set
    val isCardDraft: Boolean = cardDraftId != 0L

    private var original: RecipeEntity? = null

    /** A new recipe's starting values, such as a card draft's yield text. */
    private var draftBase: RecipeEntity? = null

    init {
        CardDrafts.get(cardDraftId)?.let { draft ->
            val now = System.currentTimeMillis()
            val entity = draft.recipe.toEntity(draft.photos, now)
            draftBase = entity
            fill(entity)
            readBy = draft.kind
            readProblems = draft.problems
        }
        if (recipeId != 0L) {
            viewModelScope.launch {
                dao.getRecipe(recipeId)?.let { recipe ->
                    original = recipe
                    fill(recipe)
                }
                loaded = true
            }
        }
    }

    private fun fill(recipe: RecipeEntity) {
        title = recipe.title
        servings = recipe.servings?.toString().orEmpty()
        prepMinutes = recipe.prepMinutes?.toString().orEmpty()
        cookMinutes = recipe.cookMinutes?.toString().orEmpty()
        totalMinutes = recipe.totalMinutes?.toString().orEmpty()
        ingredients = RecipeText.toText(recipe.ingredients)
        steps = RecipeText.toText(recipe.steps)
        notes = recipe.notes
        sourceUrl = recipe.sourceUrl.orEmpty()
        photoFile = recipe.imageFile
        cardPhotos = recipe.cardPhotos
    }

    /**
     * The parts she changed, added to any she changed before. A recipe she
     * typed in is entirely hers, so a refresh never replaces any part of it.
     */
    private fun editedFields(base: RecipeEntity, edited: RecipeEntity): List<String> {
        if (original == null && draftBase == null) {
            return listOf(EditedField.TITLE, EditedField.SERVINGS, EditedField.TIMES, EditedField.INGREDIENTS, EditedField.STEPS)
        }
        // Lines go through the same text conversion she edits, so an untouched list doesn't count as changed.
        fun roundTrip(lines: List<io.github.isaiahyoder.recipebox.data.RecipeLine>) = RecipeText.fromText(RecipeText.toText(lines))
        val changed = buildList {
            if (edited.title != base.title) add(EditedField.TITLE)
            if (edited.servings != base.servings) add(EditedField.SERVINGS)
            if (edited.prepMinutes != base.prepMinutes || edited.cookMinutes != base.cookMinutes ||
                edited.totalMinutes != base.totalMinutes
            ) {
                add(EditedField.TIMES)
            }
            if (edited.ingredients != roundTrip(base.ingredients)) add(EditedField.INGREDIENTS)
            if (edited.steps != roundTrip(base.steps)) add(EditedField.STEPS)
        }
        return (base.editedFields + changed).distinct()
    }

    fun addCardPhoto(uri: Uri) {
        addingCardPhoto = true
        viewModelScope.launch {
            photos.saveCardPhoto(uri)?.let { cardPhotos = cardPhotos + it }
            addingCardPhoto = false
        }
    }

    fun removeCardPhoto(name: String) {
        cardPhotos = cardPhotos - name
    }

    fun moveCardPhotoEarlier(name: String) {
        val index = cardPhotos.indexOf(name)
        if (index > 0) cardPhotos = cardPhotos.toMutableList().apply { add(index - 1, removeAt(index)) }
    }

    val canSave: Boolean get() = title.isNotBlank() && !saving

    fun choosePhoto(uri: Uri) {
        newPhoto = uri
        removePhoto = false
    }

    fun clearPhoto() {
        newPhoto = null
        removePhoto = true
    }

    /** Saves and calls [onSaved] with the recipe's id. */
    fun save(onSaved: (Long) -> Unit) {
        if (!canSave) return
        saving = true
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val base = original ?: draftBase ?: RecipeEntity(title = "", createdAt = now, updatedAt = now)
            val edited = base.copy(
                title = title.trim(),
                servings = servings.trim().toIntOrNull()?.takeIf { it > 0 },
                prepMinutes = prepMinutes.trim().toIntOrNull()?.takeIf { it > 0 },
                cookMinutes = cookMinutes.trim().toIntOrNull()?.takeIf { it > 0 },
                totalMinutes = totalMinutes.trim().toIntOrNull()?.takeIf { it > 0 },
                ingredients = RecipeText.fromText(ingredients),
                steps = RecipeText.fromText(steps),
                notes = notes.trim(),
                sourceUrl = sourceUrl.trim().ifBlank { null },
                cardPhotos = cardPhotos,
                updatedAt = now,
            ).let { it.copy(editedFields = editedFields(base, it)) }
            val id = if (original == null) dao.insert(edited) else edited.id.also { dao.update(edited) }

            val chosen = newPhoto
            when {
                chosen != null -> photos.saveOwnPhoto(chosen, id)?.let { name ->
                    photos.delete(edited.imageFile)
                    // Her own photo replaces the downloaded one for good.
                    dao.update(edited.copy(id = id, imageFile = name, imageIsOwn = true, imageUrl = null))
                }
                removePhoto -> {
                    photos.delete(edited.imageFile)
                    dao.update(edited.copy(id = id, imageFile = null, imageIsOwn = false, imageUrl = null))
                }
            }

            // Card photos she removed are deleted only once the recipe no longer uses them.
            (base.cardPhotos + (CardDrafts.get(cardDraftId)?.photos ?: emptyList()))
                .filter { it !in cardPhotos }
                .forEach(photos::delete)
            CardDrafts.remove(cardDraftId)

            dao.getRecipe(id)?.let { tags.refreshRecipe(it) }
            if (importJobId != 0L) queue.remove(importJobId)
            saving = false
            onSaved(id)
        }
    }
}
