package io.github.isaiahyoder.recipebox.ui.recipe

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.isaiahyoder.recipebox.model.RecipeLine
import io.github.isaiahyoder.recipebox.ingredients.DisplayIngredient

@Composable
internal fun IngredientRow(shown: DisplayIngredient, style: androidx.compose.ui.text.TextStyle) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Text("•", style = style, modifier = Modifier.width(20.dp))
        Text(
            shown.text,
            style = style,
            fontWeight = if (shown.changed) FontWeight.SemiBold else FontWeight.Normal,
            color = if (shown.changed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (shown.unscaled) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = "This amount isn't scaled",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp).size(18.dp),
            )
        }
    }
}

internal fun LazyListScope.sectionHeading(title: String) {
    item {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
        )
    }
}

internal fun LazyListScope.lines(lines: List<RecipeLine>, content: @Composable (RecipeLine) -> Unit) {
    itemsIndexed(lines) { _, line ->
        if (line.isHeader) LineHeader(line.text) else content(line)
    }
}

@Composable
internal fun LineHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}
