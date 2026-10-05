package io.github.isaiahyoder.recipebox.ui.importing

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.isaiahyoder.recipebox.appContainer
import io.github.isaiahyoder.recipebox.importer.ImportOutcome
import io.github.isaiahyoder.recipebox.importer.ImportStage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(text: String, onOpenRecipe: (Long) -> Unit, onBack: () -> Unit) {
    val container = LocalContext.current.appContainer
    val viewModel = viewModel { ImportViewModel(container.importer, text) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        when (val outcome = (state as? ImportUiState.Finished)?.outcome) {
            is ImportOutcome.Saved -> onOpenRecipe(outcome.recipeId)
            is ImportOutcome.AlreadySaved -> onOpenRecipe(outcome.recipeId)
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add recipe") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (val current = state) {
                is ImportUiState.Working -> {
                    CircularProgressIndicator()
                    Text(stageText(current.stage), textAlign = TextAlign.Center)
                }
                is ImportUiState.Finished -> when (val outcome = current.outcome) {
                    is ImportOutcome.NotFound -> NotFound(outcome, onRetry = viewModel::retry, onBack = onBack)
                    ImportOutcome.NoLink -> Problem(
                        "The shared text doesn't contain a web link.",
                        detail = null,
                        onBack = onBack,
                    )
                    else -> CircularProgressIndicator()
                }
            }
        }
    }
}

private fun stageText(stage: ImportStage?): String = when (stage) {
    null, ImportStage.DOWNLOADING -> "Reading the recipe page…"
    ImportStage.TRYING_BROWSER -> "The site is slow to answer. Trying again in a browser view…"
    ImportStage.SAVING -> "Saving the recipe and its photo…"
}

@Composable
private fun NotFound(outcome: ImportOutcome.NotFound, onRetry: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    Problem(
        "Recipe Box couldn't find a recipe on this page.",
        detail = outcome.reason,
        onBack = onBack,
    ) {
        // Sites sometimes refuse for a minute and then answer normally.
        Button(onClick = onRetry) { Text("Try again") }
        OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, outcome.url.toUri())) }) {
            Text("Open the page")
        }
    }
}

@Composable
private fun Problem(
    message: String,
    detail: String?,
    onBack: () -> Unit,
    extraAction: (@Composable () -> Unit)? = null,
) {
    Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
    Text(message, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    if (detail != null) {
        Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
    extraAction?.invoke()
    OutlinedButton(onClick = onBack) { Text("Back to recipes") }
}
