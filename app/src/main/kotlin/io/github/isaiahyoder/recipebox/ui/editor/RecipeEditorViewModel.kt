package io.github.isaiahyoder.recipebox.ui.editor

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.isaiahyoder.recipebox.data.RecipeDao
import io.github.isaiahyoder.recipebox.data.RecipeEntity
import io.github.isaiahyoder.recipebox.data.RecipeText
import io.github.isaiahyoder.recipebox.importer.ImportQueue
import io.github.isaiahyoder.recipebox.photos.PhotoStore
import io.github.isaiahyoder.recipebox.tags.AutoTagger
import io.github.isaiahyoder.recipebox.tags.toTaggable
import kotlinx.coroutines.launch

/**
 * Edits an existing recipe, or creates one when [recipeId] is 0. A recipe
 * started from a failed import keeps its link, and saving it removes the
 * failed entry from the queue.
 */
class RecipeEditorViewModel(
    private val dao: RecipeDao,
    private val photos: PhotoStore,
    private val queue: ImportQueue,
    private val recipeId: Long,
    initialSourceUrl: String?,
    private val importJobId: Long,
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

    private var original: RecipeEntity? = null

    init {
        if (recipeId != 0L) {
            viewModelScope.launch {
                dao.getRecipe(recipeId)?.let { recipe ->
                    original = recipe
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
                }
                loaded = true
            }
        }
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
            val base = original ?: RecipeEntity(title = "", createdAt = now, updatedAt = now)
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
                updatedAt = now,
            )
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

            dao.getRecipe(id)?.let { dao.replaceAutoTags(id, AutoTagger.tags(it.toTaggable())) }
            if (importJobId != 0L) queue.remove(importJobId)
            saving = false
            onSaved(id)
        }
    }
}
