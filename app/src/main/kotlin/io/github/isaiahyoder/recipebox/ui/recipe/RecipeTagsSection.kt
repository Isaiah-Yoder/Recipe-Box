package io.github.isaiahyoder.recipebox.ui.recipe

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.isaiahyoder.recipebox.R

/**
 * A recipe's tags, suggestions, and categories, with the dialogs for
 * changing them. It sits at the end of the recipe.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RecipeTagsSection(tags: List<String>, viewModel: RecipeViewModel) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    var addingTag by rememberSaveable { mutableStateOf(false) }
    var editingCategories by rememberSaveable { mutableStateOf(false) }
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    // The tag whose options are open, and the suggestion she's being asked about.
    var tagMenuFor by rememberSaveable { mutableStateOf<String?>(null) }
    var askingAbout by rememberSaveable { mutableStateOf<String?>(null) }

    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tags.forEach { tag ->
                InputChip(
                    selected = false,
                    onClick = { tagMenuFor = tag },
                    label = { Text(tag) },
                    trailingIcon = {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.recipe_remove_tag_named, tag),
                            modifier = Modifier.size(18.dp).clickable { viewModel.removeTag(tag) },
                        )
                    },
                )
            }
            suggestions.forEach { tag ->
                SuggestionChip(
                    onClick = { askingAbout = tag },
                    label = { Text(stringResource(R.string.recipe_suggested_tag, tag)) },
                    icon = { Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null, Modifier.size(18.dp)) },
                )
            }
            AssistChip(
                onClick = { addingTag = true },
                label = { Text(stringResource(R.string.recipe_add_tag)) },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, Modifier.size(18.dp)) },
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.forEach { category ->
                SuggestionChip(onClick = { editingCategories = true }, label = { Text(category.name) })
            }
            AssistChip(
                onClick = { editingCategories = true },
                label = {
                    Text(stringResource(if (categories.isEmpty()) R.string.recipe_add_to_category else R.string.recipe_categories))
                },
                leadingIcon = { Icon(Icons.Filled.Folder, contentDescription = null, Modifier.size(18.dp)) },
            )
        }
    }

    tagMenuFor?.let { tag ->
        val allCategories by viewModel.allCategories.collectAsStateWithLifecycle()
        val feeding = allCategories.filter { category -> category.feederTags.any { it.equals(tag, ignoreCase = true) } }
        AlertDialog(
            onDismissRequest = { tagMenuFor = null },
            title = { Text(tag) },
            text = {
                Text(
                    if (feeding.isEmpty()) {
                        stringResource(R.string.recipe_make_category_body, tag)
                    } else {
                        stringResource(R.string.recipe_tag_feeds, tag, feeding.joinToString { it.name })
                    }
                )
            },
            confirmButton = {
                if (feeding.isEmpty()) {
                    TextButton(onClick = {
                        tagMenuFor = null
                        viewModel.makeCategory(tag)
                    }) { Text(stringResource(R.string.recipe_make_category)) }
                } else {
                    TextButton(onClick = { tagMenuFor = null }) { Text(stringResource(R.string.recipe_ok)) }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    tagMenuFor = null
                    viewModel.removeTag(tag)
                }) { Text(stringResource(R.string.recipe_remove_tag)) }
            },
        )
    }
    askingAbout?.let { tag ->
        AlertDialog(
            onDismissRequest = { askingAbout = null },
            title = { Text(stringResource(R.string.recipe_suggestion_title, tag)) },
            text = { Text(stringResource(R.string.recipe_suggestion_body, tag)) },
            confirmButton = {
                TextButton(onClick = {
                    askingAbout = null
                    viewModel.acceptSuggestion(tag)
                }) { Text(stringResource(R.string.recipe_yes)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    askingAbout = null
                    viewModel.dismissSuggestion(tag)
                }) { Text(stringResource(R.string.recipe_no)) }
            },
        )
    }

    if (addingTag) {
        val allTags by viewModel.allTags.collectAsStateWithLifecycle()
        AddTagDialog(
            suggestions = allTags.filter { it !in tags },
            onAdd = { name ->
                addingTag = false
                viewModel.addTag(name)
            },
            onDismiss = { addingTag = false },
        )
    }
    if (editingCategories) {
        val allCategories by viewModel.allCategories.collectAsStateWithLifecycle()
        CategoryPickerDialog(
            allCategories = allCategories,
            selected = categories.map { it.id }.toSet(),
            onCreate = viewModel::createCategory,
            onSave = { ids ->
                editingCategories = false
                viewModel.saveCategories(ids)
            },
            onDismiss = { editingCategories = false },
        )
    }
}
