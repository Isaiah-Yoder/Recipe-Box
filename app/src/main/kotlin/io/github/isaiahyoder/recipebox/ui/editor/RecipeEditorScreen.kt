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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import io.github.isaiahyoder.recipebox.R
import io.github.isaiahyoder.recipebox.appContainer
import io.github.isaiahyoder.recipebox.ui.cards.CardPhotoRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeEditorScreen(
    recipeId: Long,
    sourceUrl: String?,
    importJobId: Long,
    onSaved: (Long) -> Unit,
    onCancel: () -> Unit,
    cardDraftId: Long = 0,
) {
    val container = LocalContext.current.appContainer
    val vm = viewModel(key = "edit-$recipeId-$importJobId-$cardDraftId") {
        RecipeEditorViewModel(
            container.database.recipeDao(),
            container.recipes,
            container.photos,
            container.importQueue,
            recipeId,
            sourceUrl,
            importJobId,
            cardDraftId,
        )
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.choosePhoto(uri)
    }
    val pickCardPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.addCardPhoto(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val titleRes = when {
                        vm.isCardDraft -> R.string.editor_title_card
                        recipeId == 0L -> R.string.editor_title_new
                        else -> R.string.editor_title_edit
                    }
                    Text(stringResource(titleRes))
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.editor_cancel)) }
                },
                actions = {
                    if (vm.saving) {
                        CircularProgressIndicator(Modifier.padding(end = 16.dp).padding(4.dp))
                    } else {
                        TextButton(onClick = { vm.save(onSaved) }, enabled = vm.canSave) { Text(stringResource(R.string.editor_save)) }
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
            vm.readBy?.let { reader ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.editor_read_with, stringResource(reader.labelRes)), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.editor_read_check))
                        vm.readProblems.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }

            if (vm.cardPhotos.isNotEmpty() || vm.isCardDraft) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.editor_card_photos), style = MaterialTheme.typography.titleSmall)
                    // The row runs edge to edge, so it undoes the column's side padding.
                    CardPhotoRow(
                        vm.cardPhotos,
                        modifier = Modifier.layout { measurable, constraints ->
                            val extra = 32.dp.roundToPx()
                            val placeable = measurable.measure(constraints.copy(maxWidth = constraints.maxWidth + extra))
                            layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
                        },
                        onRemove = vm::removeCardPhoto,
                        onMoveEarlier = vm::moveCardPhotoEarlier,
                    )
                    OutlinedButton(
                        onClick = { pickCardPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        enabled = !vm.addingCardPhoto,
                    ) {
                        Icon(Icons.Filled.Image, contentDescription = null)
                        Text(stringResource(R.string.editor_add_card_photo), Modifier.padding(start = 8.dp))
                    }
                }
            }

            OutlinedTextField(
                value = vm.title,
                onValueChange = { vm.title = it },
                label = { Text(stringResource(R.string.editor_title_label)) },
                isError = vm.title.isBlank(),
                supportingText = { if (vm.title.isBlank()) Text(stringResource(R.string.editor_title_required)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )

            PhotoField(
                photo = vm.newPhoto ?: vm.photoFile?.takeIf { !vm.removePhoto }?.let { container.photos.file(it) },
                // Beside card photos, "photo" alone would be unclear.
                isCover = vm.cardPhotos.isNotEmpty(),
                onChoose = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onRemove = vm::clearPhoto,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(stringResource(R.string.editor_servings), vm.servings, { vm.servings = it }, Modifier.weight(1f))
                NumberField(stringResource(R.string.editor_prep_minutes), vm.prepMinutes, { vm.prepMinutes = it }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(stringResource(R.string.editor_cook_minutes), vm.cookMinutes, { vm.cookMinutes = it }, Modifier.weight(1f))
                NumberField(stringResource(R.string.editor_total_minutes), vm.totalMinutes, { vm.totalMinutes = it }, Modifier.weight(1f))
            }

            OutlinedTextField(
                value = vm.ingredients,
                onValueChange = { vm.ingredients = it },
                label = { Text(stringResource(R.string.editor_ingredients)) },
                supportingText = { Text(stringResource(R.string.editor_ingredients_help)) },
                minLines = 6,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = vm.steps,
                onValueChange = { vm.steps = it },
                label = { Text(stringResource(R.string.editor_steps)) },
                supportingText = { Text(stringResource(R.string.editor_steps_help)) },
                minLines = 6,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = vm.notes,
                onValueChange = { vm.notes = it },
                label = { Text(stringResource(R.string.editor_notes)) },
                supportingText = { Text(stringResource(R.string.editor_notes_help)) },
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = vm.sourceUrl,
                onValueChange = { vm.sourceUrl = it },
                label = { Text(stringResource(R.string.editor_source_url)) },
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
private fun PhotoField(photo: Any?, isCover: Boolean, onChoose: () -> Unit, onRemove: () -> Unit) {
    val addRes = if (isCover) R.string.editor_cover_photo_add else R.string.editor_photo_add
    val changeRes = if (isCover) R.string.editor_cover_photo_change else R.string.editor_photo_change
    val removeRes = if (isCover) R.string.editor_cover_photo_remove else R.string.editor_photo_remove
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (photo != null) {
            AsyncImage(
                model = photo,
                contentDescription = stringResource(R.string.editor_photo_description),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(12.dp)),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onChoose) {
                Icon(Icons.Filled.Image, contentDescription = null)
                Text(stringResource(if (photo == null) addRes else changeRes), Modifier.padding(start = 8.dp))
            }
            if (photo != null) {
                TextButton(onClick = onRemove) {
                    Text(stringResource(removeRes), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
