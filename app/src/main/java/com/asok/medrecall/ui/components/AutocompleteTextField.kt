package com.asok.medrecall.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.collectLatest

/**
 * A free-text field that also offers a filtered dropdown of [suggestions]
 * as the user types. Picking a suggestion fills the field with it; typing
 * something that isn't in [suggestions] is also allowed -- it's up to the
 * caller what "new" means (MedicationFormScreen's own doctor field, for
 * example, creates a brand-new Doctor row on save if the typed name has no
 * match, while a plain string like a specialty just gets saved as typed).
 *
 * Pulled out as a shared component so Condition's doctor field and
 * Doctor's specialty field don't each hand-roll the same filter-as-you-type
 * dropdown that MedicationFormScreen already implemented once inline.
 *
 * Tapping into the field (even before typing anything) opens the dropdown
 * showing every suggestion -- listening for the field's own press release
 * rather than relying on a plain .clickable, which OutlinedTextField's own
 * pointer input would otherwise swallow (same fix already used for the
 * Condition status field and the doctor picker on RecordVisitScreen).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutocompleteTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    suggestions: List<String>,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collectLatest { interaction ->
            if (interaction is PressInteraction.Release) {
                expanded = true
            }
        }
    }

    val filtered = remember(value, suggestions) {
        if (value.isBlank()) suggestions
        else suggestions.filter { it.contains(value, ignoreCase = true) }
    }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text(label) },
            interactionSource = interactionSource,
            modifier = Modifier.fillMaxWidth()
        )
        DropdownMenu(
            expanded = expanded && filtered.isNotEmpty(),
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth()
        ) {
            filtered.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion) },
                    onClick = {
                        onValueChange(suggestion)
                        expanded = false
                    }
                )
            }
        }
    }
}
