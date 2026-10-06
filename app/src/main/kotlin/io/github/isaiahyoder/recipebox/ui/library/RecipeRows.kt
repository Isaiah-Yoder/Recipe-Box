package io.github.isaiahyoder.recipebox.ui.library

import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.isaiahyoder.recipebox.R
import io.github.isaiahyoder.recipebox.appContainer
import io.github.isaiahyoder.recipebox.data.RecipeSummary
import io.github.isaiahyoder.recipebox.ui.formatMinutes
import java.io.File

@Composable
internal fun RecipeList(recipes: List<RecipeSummary>?, empty: String, onOpenRecipe: (Long) -> Unit) {
    when {
        recipes == null -> Unit
        recipes.isEmpty() -> Text(empty, Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        else -> LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(recipes, key = { it.id }) { recipe -> RecipeRow(recipe, onClick = { onOpenRecipe(recipe.id) }) }
        }
    }
}

@Composable
internal fun RecipeRow(recipe: RecipeSummary, onClick: () -> Unit) {
    val container = LocalContext.current.appContainer
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RecipeThumbnail((recipe.imageFile ?: recipe.cardPhotos.firstOrNull())?.let(container.photos::file))
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(
                recipe.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val details = listOfNotNull(recipe.siteName, formatMinutes(recipe.totalMinutes))
            if (details.isNotEmpty()) {
                Text(
                    details.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val tags = recipe.tagNames?.split('|').orEmpty().sorted()
            if (tags.isNotEmpty()) {
                Text(
                    tags.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (recipe.favorite) {
            Icon(
                Icons.Filled.Favorite,
                contentDescription = stringResource(R.string.library_list_favorite),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp).size(20.dp),
            )
        }
    }
}

@Composable
internal fun RecipeThumbnail(file: File?, size: androidx.compose.ui.unit.Dp = 72.dp) {
    val shape = RoundedCornerShape(10.dp)
    // A missing file shows the placeholder; checking the disk first would block drawing.
    var failed by remember(file) { mutableStateOf(false) }
    if (file != null && !failed) {
        AsyncImage(
            model = file,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(shape),
            onError = { failed = true },
        )
    } else {
        Box(
            Modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Restaurant, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
