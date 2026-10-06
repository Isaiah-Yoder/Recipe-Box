package io.github.isaiahyoder.recipebox.ui.recipe

import io.github.isaiahyoder.recipebox.ui.components.UnitToggle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.isaiahyoder.recipebox.data.RecipeEntity
import io.github.isaiahyoder.recipebox.ingredients.Fractions
import io.github.isaiahyoder.recipebox.ingredients.UnitSystem
import kotlin.math.abs

/** Scale stops on the slider. */
internal val scaleStops = listOf(0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 4.0)

@Composable
internal fun ScaleCard(
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

internal fun indexOfScale(scale: Double): Int =
    scaleStops.indices.minBy { abs(scaleStops[it] - scale) }
