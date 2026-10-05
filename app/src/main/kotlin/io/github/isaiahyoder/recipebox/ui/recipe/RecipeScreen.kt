package io.github.isaiahyoder.recipebox.ui.recipe

import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import io.github.isaiahyoder.recipebox.ingredients.Measure
import io.github.isaiahyoder.recipebox.ingredients.UnitSystem
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Restore
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.InputChip
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ShoppingCart
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
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
import io.github.isaiahyoder.recipebox.ingredients.StepText
import io.github.isaiahyoder.recipebox.ui.cards.CardPhotoRow
import io.github.isaiahyoder.recipebox.ui.grocery.AddToGroceryListDialog
import io.github.isaiahyoder.recipebox.ui.formatMinutes
import kotlin.math.abs
import kotlin.math.roundToInt

/** Scale stops on the slider. */
private val scaleStops = listOf(0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 4.0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeScreen(recipeId: Long, onEdit: () -> Unit, onBack: () -> Unit) {
    val container = LocalContext.current.appContainer
    val viewModel = viewModel(key = "recipe-$recipeId") {
        val database = container.database
        RecipeViewModel(
            database.recipeDao(), database.tagDao(), database.categoryDao(),
            container.library, container.recipes, container.recipeRefresher, recipeId,
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val cookMode by viewModel.cookMode.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmWebsiteVersion by rememberSaveable { mutableStateOf(false) }
    var addingToList by rememberSaveable { mutableStateOf(false) }
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
                                DropdownMenuItem(
                                    text = { Text("Add to grocery list") },
                                    leadingIcon = { Icon(Icons.Filled.ShoppingCart, null) },
                                    onClick = {
                                        menuOpen = false
                                        addingToList = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Edit recipe") },
                                    leadingIcon = { Icon(Icons.Filled.Edit, null) },
                                    onClick = {
                                        menuOpen = false
                                        onEdit()
                                    },
                                )
                                if (recipe.sourceUrl != null && recipe.editedFields.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("Use the website's version") },
                                        leadingIcon = { Icon(Icons.Filled.Restore, null) },
                                        onClick = {
                                            menuOpen = false
                                            confirmWebsiteVersion = true
                                        },
                                    )
                                }
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
                viewModel = viewModel,
                cookMode = cookMode,
                contentPadding = padding,
                onScale = viewModel::setScale,
            )
        }
    }

    if (addingToList && recipe != null) {
        AddToGroceryListDialog(
            dao = container.database.groceryDao(),
            recipeId = recipe.id,
            scale = recipe.lastScale,
            onDone = { listName ->
                addingToList = false
                Toast.makeText(context, "Added to $listName", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { addingToList = false },
        )
    }

    if (confirmWebsiteVersion && recipe != null) {
        AlertDialog(
            onDismissRequest = { confirmWebsiteVersion = false },
            title = { Text("Use the website's version?") },
            text = {
                Text(
                    "Your changes to this recipe's title, servings, times, ingredients, and steps are replaced " +
                        "with what its web page says. Your notes, tags, categories, and photos stay."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmWebsiteVersion = false
                    viewModel.useWebsiteVersion { worked ->
                        if (!worked) Toast.makeText(context, "The web page couldn't be read. Try again later.", Toast.LENGTH_LONG).show()
                    }
                }) { Text("Use website's version") }
            },
            dismissButton = { TextButton(onClick = { confirmWebsiteVersion = false }) { Text("Cancel") } },
        )
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
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    var addingTag by rememberSaveable { mutableStateOf(false) }
    var editingCategories by rememberSaveable { mutableStateOf(false) }
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    // The tag whose options are open, and the suggestion she's being asked about.
    var tagMenuFor by rememberSaveable { mutableStateOf<String?>(null) }
    var askingAbout by rememberSaveable { mutableStateOf<String?>(null) }
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
        container.photos.existing(recipe.imageFile)?.let { photo ->
            item {
                AsyncImage(
                    model = photo,
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
                    formatMinutes(recipe.prepMinutes)?.let { "Prep $it" },
                    formatMinutes(recipe.cookMinutes)?.let { "Cook $it" },
                    formatMinutes(recipe.totalMinutes)?.let { "Total $it" },
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
        if (recipe.ingredients.isNotEmpty()) sectionHeading("Ingredients")
        lines(recipe.ingredients) { line ->
            val shown = remember(line.text, scale, units) { IngredientScaler.display(line.text, scale, units) }
            IngredientRow(shown, bodyStyle)
        }
        if (recipe.steps.isNotEmpty()) sectionHeading("Steps")
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
            sectionHeading("Notes")
            item {
                Text(
                    recipe.notes,
                    style = bodyStyle,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        // Tags and categories come last, so the recipe itself is what she sees first.
        sectionHeading("Tags and categories")
        item {
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
                                    contentDescription = "Remove tag $tag",
                                    modifier = Modifier.size(18.dp).clickable { viewModel.removeTag(tag) },
                                )
                            },
                        )
                    }
                    suggestions.forEach { tag ->
                        SuggestionChip(
                            onClick = { askingAbout = tag },
                            label = { Text("$tag?") },
                            icon = { Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null, Modifier.size(18.dp)) },
                        )
                    }
                    AssistChip(
                        onClick = { addingTag = true },
                        label = { Text("Add tag") },
                        leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, Modifier.size(18.dp)) },
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { category ->
                        SuggestionChip(onClick = { editingCategories = true }, label = { Text(category.name) })
                    }
                    AssistChip(
                        onClick = { editingCategories = true },
                        label = { Text(if (categories.isEmpty()) "Add to category" else "Categories") },
                        leadingIcon = { Icon(Icons.Filled.Folder, contentDescription = null, Modifier.size(18.dp)) },
                    )
                }
            }
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
                        "Make a category from this tag? Every recipe tagged $tag joins it, now and in the future. " +
                            "You can still add or remove recipes yourself."
                    } else {
                        "Recipes tagged $tag join ${feeding.joinToString { it.name }} automatically."
                    }
                )
            },
            confirmButton = {
                if (feeding.isEmpty()) {
                    TextButton(onClick = {
                        tagMenuFor = null
                        viewModel.makeCategory(tag)
                    }) { Text("Make a category") }
                } else {
                    TextButton(onClick = { tagMenuFor = null }) { Text("OK") }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    tagMenuFor = null
                    viewModel.removeTag(tag)
                }) { Text("Remove tag") }
            },
        )
    }
    askingAbout?.let { tag ->
        AlertDialog(
            onDismissRequest = { askingAbout = null },
            title = { Text("Is this a $tag recipe?") },
            text = { Text("Recipe Box guessed $tag from the recipe's name. Your answer is kept, even when recipes refresh.") },
            confirmButton = {
                TextButton(onClick = {
                    askingAbout = null
                    viewModel.acceptSuggestion(tag)
                }) { Text("Yes") }
            },
            dismissButton = {
                TextButton(onClick = {
                    askingAbout = null
                    viewModel.dismissSuggestion(tag)
                }) { Text("No") }
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

@Composable
private fun ScaleCard(
    recipe: RecipeEntity,
    scale: Double,
    sliderIndex: Float,
    onSliderChange: (Float) -> Unit,
    onSliderDone: () -> Unit,
    onEditServings: () -> Unit,
    hasMeasures: Boolean,
    units: UnitSystem,
    onUnits: (UnitSystem) -> Unit,
) {
    Card(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            val makes = recipe.servings?.let {
                val count = it * scale
                "Makes ${Fractions.format(count)} ${if (Fractions.isPlural(count)) "servings" else "serving"}"
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = if (recipe.servings != null) Modifier.clickable(onClick = onEditServings) else Modifier,
            ) {
                Text(
                    listOfNotNull("${Fractions.format(scale)}× recipe", makes).joinToString(" · "),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (recipe.servings != null) {
                    Icon(Icons.Filled.Edit, contentDescription = "Type a number of servings", Modifier.size(20.dp))
                }
            }
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
            if (hasMeasures) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                UnitToggle(units, onUnits)
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

/** Switches amounts between US and metric units. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitToggle(units: UnitSystem, onUnits: (UnitSystem) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        listOf(UnitSystem.US to "US", UnitSystem.METRIC to "Metric").forEachIndexed { index, (option, label) ->
            SegmentedButton(
                selected = units == option,
                onClick = { onUnits(option) },
                shape = SegmentedButtonDefaults.itemShape(index, 2),
            ) { Text(label) }
        }
    }
}
