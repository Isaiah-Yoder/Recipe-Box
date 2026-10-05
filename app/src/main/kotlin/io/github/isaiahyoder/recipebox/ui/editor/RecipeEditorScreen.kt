package io.github.isaiahyoder.recipebox.ui.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import io.github.isaiahyoder.recipebox.appContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeEditorScreen(
    recipeId: Long,
    sourceUrl: String?,
    importJobId: Long,
    onSaved: (Long) -> Unit,
    onCancel: () -> Unit,
) {
    val container = LocalContext.current.appContainer
    val vm = viewModel(key = "edit-$recipeId-$importJobId") {
        RecipeEditorViewModel(
            container.database.recipeDao(),
            container.photos,
            container.importQueue,
            recipeId,
            sourceUrl,
            importJobId,
        )
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.choosePhoto(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (recipeId == 0L) "New recipe" else "Edit recipe") },
                navigationIcon = {
                    IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = "Cancel") }
                },
                actions = {
                    if (vm.saving) {
                        CircularProgressIndicator(Modifier.padding(end = 16.dp).padding(4.dp))
                    } else {
                        TextButton(onClick = { vm.save(onSaved) }, enabled = vm.canSave) { Text("Save") }
                    }
                },
            )
        },
    ) { padding ->
        if (!vm.loaded) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = vm.title,
                onValueChange = { vm.title = it },
                label = { Text("Title") },
                isError = vm.title.isBlank(),
                supportingText = { if (vm.title.isBlank()) Text("A recipe needs a title.") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )

            PhotoField(
                photo = vm.newPhoto ?: vm.photoFile?.takeIf { !vm.removePhoto }?.let { container.photos.file(it) },
                onChoose = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onRemove = vm::clearPhoto,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Servings", vm.servings, { vm.servings = it }, Modifier.weight(1f))
                NumberField("Prep min", vm.prepMinutes, { vm.prepMinutes = it }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Cook min", vm.cookMinutes, { vm.cookMinutes = it }, Modifier.weight(1f))
                NumberField("Total min", vm.totalMinutes, { vm.totalMinutes = it }, Modifier.weight(1f))
            }

            OutlinedTextField(
                value = vm.ingredients,
                onValueChange = { vm.ingredients = it },
                label = { Text("Ingredients") },
                supportingText = { Text("One ingredient per line. End a line with a colon to start a group, such as \"For the sauce:\".") },
                minLines = 6,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = vm.steps,
                onValueChange = { vm.steps = it },
                label = { Text("Steps") },
                supportingText = { Text("One step per line.") },
                minLines = 6,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = vm.notes,
                onValueChange = { vm.notes = it },
                label = { Text("Notes") },
                supportingText = { Text("Your own notes, such as changes you make. They're never overwritten.") },
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = vm.sourceUrl,
                onValueChange = { vm.sourceUrl = it },
                label = { Text("Original page link") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onChange(text.filter { it.isDigit() }.take(4)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun PhotoField(photo: Any?, onChoose: () -> Unit, onRemove: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (photo != null) {
            AsyncImage(
                model = photo,
                contentDescription = "Recipe photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(12.dp)),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onChoose) {
                Icon(Icons.Filled.Image, contentDescription = null)
                Text(if (photo == null) "Add photo" else "Change photo", Modifier.padding(start = 8.dp))
            }
            if (photo != null) {
                TextButton(onClick = onRemove) {
                    Text("Remove photo", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
