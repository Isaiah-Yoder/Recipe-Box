package io.github.isaiahyoder.recipebox.ui.recipe

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import io.github.isaiahyoder.recipebox.appContainer
import io.github.isaiahyoder.recipebox.data.RecipeEntity
import io.github.isaiahyoder.recipebox.data.RecipeLine
import io.github.isaiahyoder.recipebox.ingredients.DisplayIngredient
import io.github.isaiahyoder.recipebox.ingredients.Fractions
import io.github.isaiahyoder.recipebox.ingredients.IngredientParser
import io.github.isaiahyoder.recipebox.ingredients.IngredientScaler
import io.github.isaiahyoder.recipebox.ui.formatMinutes
import kotlin.math.abs
import kotlin.math.roundToInt

/** Scale stops on the slider. */
private val scaleStops = listOf(0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 4.0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeScreen(recipeId: Long, onBack: () -> Unit) {
    val container = LocalContext.current.appContainer
    val viewModel = viewModel(key = "recipe-$recipeId") {
        RecipeViewModel(container.database.recipeDao(), container.photos, recipeId)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val cookMode by viewModel.cookMode.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    // Cook mode keeps the screen on only while this screen is showing.
    val view = LocalView.current
    DisposableEffect(cookMode) {
        view.keepScreenOn = cookMode
        onDispose { view.keepScreenOn = false }
    }

    val recipe = (state as? RecipeUiState.Loaded)?.recipe
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (recipe != null) {
                        IconButton(onClick = { viewModel.setCookMode(!cookMode) }) {
                            Icon(
                                if (cookMode) Icons.Filled.Lightbulb else Icons.Outlined.Lightbulb,
                                contentDescription = if (cookMode) "Turn off cook mode" else "Turn on cook mode",
                                tint = if (cookMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(onClick = { viewModel.setFavorite(!recipe.favorite) }) {
                            Icon(
                                if (recipe.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = if (recipe.favorite) "Remove from favorites" else "Add to favorites",
                            )
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                recipe.sourceUrl?.let { url ->
                                    DropdownMenuItem(
                                        text = { Text("Open original page") },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) },
                                        onClick = {
                                            menuOpen = false
                                            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Delete recipe") },
                                    leadingIcon = { Icon(Icons.Filled.Delete, null) },
                                    onClick = {
                                        menuOpen = false
                                        confirmDelete = true
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        when (state) {
            RecipeUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            RecipeUiState.Missing -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("This recipe was deleted.")
            }
            is RecipeUiState.Loaded -> RecipeContent(
                recipe = recipe!!,
                tags = tags,
                cookMode = cookMode,
                contentPadding = padding,
                onScale = viewModel::setScale,
                onShowUsUnits = viewModel::setShowUsUnits,
            )
        }
    }

    if (confirmDelete && recipe != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this recipe?") },
            text = { Text("\"${recipe.title}\" and its photo will be removed from Recipe Box.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(onDeleted = onBack)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecipeContent(
    recipe: RecipeEntity,
    tags: List<String>,
    cookMode: Boolean,
    contentPadding: PaddingValues,
    onScale: (Double) -> Unit,
    onShowUsUnits: (Boolean) -> Unit,
) {
    val container = LocalContext.current.appContainer
    // The slider moves freely while dragging and saves when released.
    var sliderIndex by remember(recipe.id) { mutableFloatStateOf(indexOfScale(recipe.lastScale).toFloat()) }
    val scale = scaleStops[sliderIndex.roundToInt().coerceIn(scaleStops.indices)]
    val hasMetric = remember(recipe.ingredients) {
        recipe.ingredients.any { !it.isHeader && IngredientParser.parse(it.text).unit?.isMetric == true }
    }
    val toUs = hasMetric && recipe.showUsUnits
    val bodyStyle = if (cookMode) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 32.dp,
        ),
    ) {
        recipe.imageFile?.let { name ->
            item {
                AsyncImage(
                    model = container.photos.file(name),
                    contentDescription = "Photo of ${recipe.title}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .clip(RoundedCornerShape(16.dp)),
                )
            }
        }
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(recipe.title, style = MaterialTheme.typography.headlineSmall)
                recipe.siteName?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val times = listOfNotNull(
                    formatMinutes(recipe.prepMinutes)?.let { "Prep $it" },
                    formatMinutes(recipe.cookMinutes)?.let { "Cook $it" },
                    formatMinutes(recipe.totalMinutes)?.let { "Total $it" },
                )
                if (times.isNotEmpty()) Text(times.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                if (tags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        tags.forEach { AssistChip(onClick = {}, label = { Text(it) }) }
                    }
                }
            }
        }
        item {
            ScaleCard(
                recipe = recipe,
                scale = scale,
                sliderIndex = sliderIndex,
                onSliderChange = { sliderIndex = it },
                // Read the slider position when the gesture ends; the composed value can lag one frame.
                onSliderDone = { onScale(scaleStops[sliderIndex.roundToInt().coerceIn(scaleStops.indices)]) },
                hasMetric = hasMetric,
                showUsUnits = toUs,
                onShowUsUnits = onShowUsUnits,
            )
        }
        sectionHeading("Ingredients")
        lines(recipe.ingredients) { line ->
            val shown = remember(line.text, scale, toUs) { IngredientScaler.display(line.text, scale, toUs) }
            IngredientRow(shown, bodyStyle)
        }
        sectionHeading("Steps")
        var stepNumber = 0
        val numbered = recipe.steps.map { line -> if (line.isHeader) line to 0 else line to ++stepNumber }
        itemsIndexed(numbered) { _, (line, number) ->
            if (line.isHeader) {
                LineHeader(line.text)
            } else {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        "$number",
                        style = bodyStyle,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(32.dp),
                    )
                    Text(line.text, style = bodyStyle)
                }
            }
        }
    }
}

@Composable
private fun ScaleCard(
    recipe: RecipeEntity,
    scale: Double,
    sliderIndex: Float,
    onSliderChange: (Float) -> Unit,
    onSliderDone: () -> Unit,
    hasMetric: Boolean,
    showUsUnits: Boolean,
    onShowUsUnits: (Boolean) -> Unit,
) {
    Card(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            val makes = recipe.servings?.let { "Makes ${Fractions.format(it * scale)} servings" }
            Text(
                listOfNotNull("${Fractions.format(scale)}× recipe", makes).joinToString(" · "),
                style = MaterialTheme.typography.titleMedium,
            )
            Slider(
                value = sliderIndex,
                onValueChange = { onSliderChange(it) },
                onValueChangeFinished = onSliderDone,
                valueRange = 0f..(scaleStops.size - 1).toFloat(),
                steps = scaleStops.size - 2,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                scaleStops.forEach {
                    Text(Fractions.format(it), style = MaterialTheme.typography.labelSmall)
                }
            }
            if (hasMetric) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Show metric amounts in US units", Modifier.weight(1f))
                    Switch(checked = showUsUnits, onCheckedChange = onShowUsUnits)
                }
            }
        }
    }
}

@Composable
private fun IngredientRow(shown: DisplayIngredient, style: androidx.compose.ui.text.TextStyle) {
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

private fun LazyListScope.sectionHeading(title: String) {
    item {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
        )
    }
}

private fun LazyListScope.lines(lines: List<RecipeLine>, content: @Composable (RecipeLine) -> Unit) {
    itemsIndexed(lines) { _, line ->
        if (line.isHeader) LineHeader(line.text) else content(line)
    }
}

@Composable
private fun LineHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

private fun indexOfScale(scale: Double): Int =
    scaleStops.indices.minBy { abs(scaleStops[it] - scale) }
