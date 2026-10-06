package io.github.isaiahyoder.recipebox.ui.cards

import io.github.isaiahyoder.recipebox.util.runCatchingCancellable
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.isaiahyoder.recipebox.cards.CardDraft
import io.github.isaiahyoder.recipebox.cards.CardDrafts
import io.github.isaiahyoder.recipebox.cards.CardReader
import io.github.isaiahyoder.recipebox.cards.CardReaderKind
import io.github.isaiahyoder.recipebox.cards.CardRecipe
import io.github.isaiahyoder.recipebox.photos.PhotoStore
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Collects the photos of one recipe card, front first, and reads them. The
 * photos are saved right away, so they survive the camera app or a screen
 * rotation, and become the recipe's card photos.
 */
class CardScanViewModel(
    private val photos: PhotoStore,
    private val reader: CardReader,
    private val state: SavedStateHandle,
) : ViewModel() {
    val photoNames: StateFlow<List<String>> = state.getStateFlow(KEY_PHOTOS, emptyList())

    var adding by mutableStateOf(false)
        private set

    /** The reader working now, or null when nothing is being read. */
    var reading by mutableStateOf<CardReaderKind?>(null)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    fun add(uris: List<Uri>) {
        if (uris.isEmpty()) return
        adding = true
        error = null
        viewModelScope.launch {
            val saved = uris.mapNotNull { photos.saveCardPhoto(it) }
            if (saved.size < uris.size) error = "A photo couldn't be opened. Try taking it again."
            state[KEY_PHOTOS] = photoNames.value + saved
            adding = false
        }
    }

    fun remove(name: String) {
        state[KEY_PHOTOS] = photoNames.value - name
        photos.delete(name)
    }

    fun moveEarlier(name: String) {
        val list = photoNames.value.toMutableList()
        val index = list.indexOf(name)
        if (index > 0) {
            list.removeAt(index)
            list.add(index - 1, name)
            state[KEY_PHOTOS] = list
        }
    }

    /** Reads the photos and calls [onRead] with the draft's id for the editor. */
    fun read(onRead: (Long) -> Unit) {
        val names = photoNames.value
        if (names.isEmpty() || reading != null) return
        error = null
        reading = reader.firstKind()
        viewModelScope.launch {
            runCatchingCancellable { reader.read(names.map(photos::file)) { reading = it } }
                .onSuccess { result ->
                    reading = null
                    onRead(CardDrafts.put(CardDraft(result.recipe, names, result.kind, result.problems)))
                }
                .onFailure {
                    reading = null
                    error = it.message ?: "The card couldn't be read."
                }
        }
    }

    /** Opens the editor with the photos attached and the fields empty. */
    fun typeInstead(onDraft: (Long) -> Unit) {
        onDraft(CardDrafts.put(CardDraft(CardRecipe(), photoNames.value, kind = null, problems = emptyList())))
    }

    private companion object {
        const val KEY_PHOTOS = "photos"
    }
}
