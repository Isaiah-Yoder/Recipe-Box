package io.github.isaiahyoder.recipebox.ui.categories

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.isaiahyoder.recipebox.data.CategoryEntity
import io.github.isaiahyoder.recipebox.tags.AutoTagger
import io.github.isaiahyoder.recipebox.tags.FeederSuggestions
import io.github.isaiahyoder.recipebox.tags.TagGroup

/**
 * Chooses the tags that fill [category] automatically. Tags that fit its
 * name come first, then every tag by group, then tags she made herself.
 */
@Composable
fun FeederDialog(
    category: CategoryEntity,
    tagsInUse: List<String>,
    onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val chosen = remember(category.id) { mutableStateListOf(*category.feederTags.toTypedArray()) }
    val fitting = FeederSuggestions.forCategory(category.name, tagsInUse)
    val known = (AutoTagger.VOCABULARY.keys + tagsInUse + category.feederTags).distinct()
    val sections = buildList {
        if (fitting.isNotEmpty()) add("Fits \"${category.name}\"" to fitting)
        for (group in TagGroup.entries) {
            val tags = known.filter { AutoTagger.groupOf(it) == group && it !in fitting }.sorted()
            if (tags.isNotEmpty()) add(group.label to tags)
        }
        val own = known.filter { AutoTagger.groupOf(it) == null && it !in fitting }.sorted()
        if (own.isNotEmpty()) add("Your tags" to own)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fill \"${category.name}\" from tags") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "Recipes with any checked tag join this category, now and in the future. Recipes you add " +
                        "or take out yourself stay that way.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                for ((title, tags) in sections) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                    for (tag in tags) {
                        val checked = chosen.any { it.equals(tag, ignoreCase = true) }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .toggleable(checked, role = Role.Checkbox) { on ->
                                    if (on) chosen += tag else chosen.removeAll { it.equals(tag, ignoreCase = true) }
                                }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Text(tag, Modifier.padding(start = 12.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(chosen.toList()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
