package io.github.isaiahyoder.recipebox.ui.importing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.isaiahyoder.recipebox.importer.ImportOutcome
import io.github.isaiahyoder.recipebox.importer.ImportStage
import io.github.isaiahyoder.recipebox.importer.RecipeImporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ImportUiState {
    data class Working(val stage: ImportStage?) : ImportUiState
    data class Finished(val outcome: ImportOutcome) : ImportUiState
}

/** Runs one import. The view model outlives rotation, so the import runs once. */
class ImportViewModel(private val importer: RecipeImporter, private val text: String) : ViewModel() {
    private val _state = MutableStateFlow<ImportUiState>(ImportUiState.Working(null))
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    init {
        start()
    }

    /** Runs the import again, for a site that refused the first time. */
    fun retry() {
        if (_state.value is ImportUiState.Finished) start()
    }

    private fun start() {
        _state.value = ImportUiState.Working(null)
        viewModelScope.launch {
            val outcome = importer.import(text) { stage -> _state.value = ImportUiState.Working(stage) }
            _state.value = ImportUiState.Finished(outcome)
        }
    }
}
