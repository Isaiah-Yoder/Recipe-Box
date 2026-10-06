package io.github.isaiahyoder.recipebox.ui.categories

import androidx.annotation.StringRes
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.isaiahyoder.recipebox.R
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
        if (fitting.isNotEmpty()) add(stringResource(R.string.library_feeder_fits, category.name) to fitting)
        for (group in TagGroup.entries) {
            val tags = known.filter { AutoTagger.groupOf(it) == group && it !in fitting }.sorted()
            if (tags.isNotEmpty()) add(stringResource(group.labelRes()) to tags)
        }
        val own = known.filter { AutoTagger.groupOf(it) == null && it !in fitting }.sorted()
        if (own.isNotEmpty()) add(stringResource(R.string.library_feeder_your_tags) to own)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_feeder_title, category.name)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.library_feeder_body),
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
        confirmButton = { TextButton(onClick = { onSave(chosen.toList()) }) { Text(stringResource(R.string.library_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.library_cancel)) } },
    )
}

/** The heading for a group of tags. [TagGroup.label] in core stays English; this one can be translated. */
@StringRes
private fun TagGroup.labelRes(): Int = when (this) {
    TagGroup.COURSE -> R.string.library_tag_group_course
    TagGroup.KIND -> R.string.library_tag_group_kind
    TagGroup.OCCASION -> R.string.library_tag_group_occasion
    TagGroup.METHOD -> R.string.library_tag_group_method
    TagGroup.MAIN_INGREDIENT -> R.string.library_tag_group_main_ingredient
    TagGroup.DIET -> R.string.library_tag_group_diet
    TagGroup.CUISINE -> R.string.library_tag_group_cuisine
    TagGroup.TIME -> R.string.library_tag_group_time
    TagGroup.SOURCE -> R.string.library_tag_group_source
}
