package io.github.isaiahyoder.recipebox.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.isaiahyoder.recipebox.ingredients.UnitSystem

/** Switches amounts between US and metric units, on a recipe or a grocery list. */
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
