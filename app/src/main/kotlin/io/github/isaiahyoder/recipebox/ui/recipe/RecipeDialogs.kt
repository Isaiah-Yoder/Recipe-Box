package io.github.isaiahyoder.recipebox.ui.recipe

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.isaiahyoder.recipebox.R
import io.github.isaiahyoder.recipebox.data.CategoryEntity
import io.github.isaiahyoder.recipebox.ingredients.Fractions
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddTagDialog(suggestions: List<String>, onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val matches = suggestions.filter { it.contains(text.trim(), ignoreCase = true) }.take(12)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.recipe_add_tag_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.recipe_tag)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                if (matches.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        matches.forEach { tag -> SuggestionChip(onClick = { onAdd(tag) }, label = { Text(tag) }) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(text) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.recipe_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.recipe_cancel)) } },
    )
}

/** Puts a recipe in any number of categories, and can create a category on the spot. */
@Composable
fun CategoryPickerDialog(
    allCategories: List<CategoryEntity>,
    selected: Set<Long>,
    onCreate: suspend (String) -> Long,
    onSave: (Set<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    var chosen by remember { mutableStateOf(selected) }
    var newName by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.recipe_categories)) },
        text = {
            Column {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    if (allCategories.isEmpty()) {
                        Text(stringResource(R.string.recipe_no_categories))
                    }
                    allCategories.forEach { category ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { chosen = chosen.toggle(category.id) },
                        ) {
                            Checkbox(checked = category.id in chosen, onCheckedChange = { chosen = chosen.toggle(category.id) })
                            Text(category.name)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text(stringResource(R.string.recipe_new_category)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            val name = newName.trim()
                            newName = ""
                            scope.launch { chosen = chosen + onCreate(name) }
                        },
                        enabled = newName.isNotBlank(),
                    ) { Text(stringResource(R.string.recipe_create)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(chosen) }) { Text(stringResource(R.string.recipe_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.recipe_cancel)) } },
    )
}

private fun Set<Long>.toggle(id: Long) = if (id in this) this - id else this + id

@Composable
fun ServingsDialog(current: Double, onSet: (Double) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(Fractions.format(current).filter { it.isDigit() }.ifEmpty { "1" }) }
    val count = text.toIntOrNull()?.takeIf { it in 1..999 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.recipe_servings_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { value -> text = value.filter { it.isDigit() }.take(3) },
                label = { Text(stringResource(R.string.recipe_servings)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(onClick = { count?.let { onSet(it.toDouble()) } }, enabled = count != null) {
                Text(stringResource(R.string.recipe_set))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.recipe_cancel)) } },
    )
}
