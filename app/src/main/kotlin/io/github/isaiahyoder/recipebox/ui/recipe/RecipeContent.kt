package io.github.isaiahyoder.recipebox.ui.recipe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import io.github.isaiahyoder.recipebox.R
import io.github.isaiahyoder.recipebox.appContainer
import io.github.isaiahyoder.recipebox.data.RecipeEntity
import io.github.isaiahyoder.recipebox.ingredients.IngredientParser
import io.github.isaiahyoder.recipebox.ingredients.IngredientScaler
import io.github.isaiahyoder.recipebox.ingredients.Measure
import io.github.isaiahyoder.recipebox.ingredients.StepText
import io.github.isaiahyoder.recipebox.ui.cards.CardPhotoRow
import io.github.isaiahyoder.recipebox.ui.formatMinutes
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RecipeContent(
    recipe: RecipeEntity,
    tags: List<String>,
    viewModel: RecipeViewModel,
    cookMode: Boolean,
    contentPadding: PaddingValues,
    onScale: (Double) -> Unit,
) {
    val container = LocalContext.current.appContainer
    // The slider moves freely while dragging and saves when released.
    var sliderIndex by remember(recipe.id) { mutableFloatStateOf(indexOfScale(recipe.lastScale).toFloat()) }
    // A typed serving count gives a scale between slider stops, such as 8 of 6 servings.
    var customScale by remember(recipe.id) {
        mutableStateOf(recipe.lastScale.takeIf { saved -> scaleStops.none { abs(it - saved) < 1e-6 } })
    }
    val scale = customScale ?: scaleStops[sliderIndex.roundToInt().coerceIn(scaleStops.indices)]
    var editingServings by rememberSaveable { mutableStateOf(false) }
    // Amounts she can switch between US and metric; counts such as "2 eggs" can't.
    val hasMeasures = remember(recipe.ingredients) {
        recipe.ingredients.any { line ->
            !line.isHeader && IngredientParser.parse(line.text).unit?.measure.let { it != null && it != Measure.COUNT }
        }
    }
    // Starts in her setting; switching here lasts only while she's on this recipe.
    val defaultUnits by container.settings.unitSystem.collectAsStateWithLifecycle()
    var units by remember(recipe.id, defaultUnits) { mutableStateOf(defaultUnits) }
    val bodyStyle = if (cookMode) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 32.dp,
        ),
    ) {
        recipe.imageFile?.let { name ->
            item(key = "photo") {
                // A missing file hides the photo; checking the disk first would block drawing.
                var missing by remember(name) { mutableStateOf(false) }
                if (!missing) {
                    AsyncImage(
                        model = container.photos.file(name),
                        contentDescription = stringResource(R.string.recipe_photo_of, recipe.title),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .aspectRatio(4f / 3f)
                            .clip(RoundedCornerShape(16.dp)),
                        onError = { missing = true },
                    )
                }
            }
        }
        if (recipe.cardPhotos.isNotEmpty() && !cookMode) {
            item {
                // The original card, for checking what was read or seeing her handwriting.
                CardPhotoRow(recipe.cardPhotos, modifier = Modifier.padding(top = 8.dp))
            }
        }
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(recipe.title, style = MaterialTheme.typography.headlineSmall)
                recipe.siteName?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val times = listOfNotNull(
                    formatMinutes(recipe.prepMinutes)?.let { stringResource(R.string.recipe_time_prep, it) },
                    formatMinutes(recipe.cookMinutes)?.let { stringResource(R.string.recipe_time_cook, it) },
                    formatMinutes(recipe.totalMinutes)?.let { stringResource(R.string.recipe_time_total, it) },
                )
                if (times.isNotEmpty()) Text(times.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
            }
        }
        item {
            ScaleCard(
                recipe = recipe,
                scale = scale,
                sliderIndex = sliderIndex,
                onSliderChange = {
                    sliderIndex = it
                    customScale = null
                },
                onEditServings = { editingServings = true },
                // Read the slider position when the gesture ends; the composed value can lag one frame.
                onSliderDone = { if (customScale == null) onScale(scaleStops[sliderIndex.roundToInt().coerceIn(scaleStops.indices)]) },
                hasMeasures = hasMeasures,
                units = units,
                onUnits = { units = it },
            )
        }
        if (recipe.ingredients.isNotEmpty()) sectionHeading(R.string.recipe_ingredients)
        lines(recipe.ingredients) { line ->
            val shown = remember(line.text, scale, units) { IngredientScaler.display(line.text, scale, units) }
            IngredientRow(shown, bodyStyle)
        }
        if (recipe.steps.isNotEmpty()) sectionHeading(R.string.recipe_steps)
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
                    val pieces = remember(line.text, scale, units) { StepText.display(line.text, scale, units) }
                    val highlight = MaterialTheme.colorScheme.primary
                    Text(
                        buildAnnotatedString {
                            pieces.forEach { piece ->
                                if (piece.changed) {
                                    withStyle(SpanStyle(color = highlight, fontWeight = FontWeight.SemiBold)) { append(piece.text) }
                                } else {
                                    append(piece.text)
                                }
                            }
                        },
                        style = bodyStyle,
                    )
                }
            }
        }
        if (recipe.notes.isNotBlank()) {
            sectionHeading(R.string.recipe_notes)
            item {
                Text(
                    recipe.notes,
                    style = bodyStyle,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        // Tags and categories come last, so the recipe itself is what she sees first.
        sectionHeading(R.string.recipe_tags_and_categories)
        item { RecipeTagsSection(tags, viewModel) }
    }

    val servings = recipe.servings
    if (editingServings && servings != null) {
        ServingsDialog(
            current = servings * scale,
            onSet = { count ->
                editingServings = false
                val exact = count / servings
                customScale = exact.takeIf { s -> scaleStops.none { abs(it - s) < 1e-6 } }
                sliderIndex = indexOfScale(exact).toFloat()
                onScale(exact)
            },
            onDismiss = { editingServings = false },
        )
    }
}
