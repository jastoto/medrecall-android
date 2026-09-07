package com.asok.medrecall.ui.conditions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.asok.medrecall.data.local.Condition
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight

private val statusOptions = listOf("Active", "Monitoring", "Resolved")

/**
 * Cancel / centered-title / Save top bar (rather than this app's usual
 * Home-icon TopAppBar) matches how this screen is meant to feel like a
 * modal sheet for adding one condition, per the reference design.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConditionFormScreen(
    conditionId: Int?,
    onDone: () -> Unit,
    viewModel: ConditionsViewModel = viewModel(factory = ConditionsViewModel.factory(LocalContext.current))
) {
    val coroutineScope = rememberCoroutineScope()

    var loadedExisting by remember { mutableStateOf(conditionId == null) }
    var editingId by remember { mutableStateOf(0) }
    var name by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(statusOptions.first()) }
    var iconKey by remember { mutableStateOf(defaultConditionIconKey) }
    var notes by remember { mutableStateOf("") }
    var statusMenuExpanded by remember { mutableStateOf(false) }

    // Same fix as the doctor-picker dropdown on RecordVisitScreen: a plain
    // .clickable on a readOnly OutlinedTextField gets swallowed by the
    // field's own pointer-input handling, so open the menu off the field's
    // interactionSource instead.
    val statusFieldInteractionSource = remember { MutableInteractionSource() }
    LaunchedEffect(statusFieldInteractionSource) {
        statusFieldInteractionSource.interactions.collectLatest { interaction ->
            if (interaction is PressInteraction.Release) {
                statusMenuExpanded = true
            }
        }
    }

    LaunchedEffect(conditionId) {
        if (conditionId != null) {
            viewModel.getCondition(conditionId)?.let { existing ->
                editingId = existing.id
                name = existing.name
                status = existing.status
                iconKey = existing.iconKey
                notes = existing.notes.orEmpty()
            }
            loadedExisting = true
        }
    }

    if (!loadedExisting) return

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (conditionId == null) "Add Condition" else "Edit Condition", fontWeight = FontWeight.Bold, color = Color.Black) },
                navigationIcon = {
                    TextButton(onClick = onDone) { Text("Cancel") }
                },
                actions = {
                    TextButton(
                        enabled = name.isNotBlank(),
                        onClick = {
                            coroutineScope.launch {
                                viewModel.saveCondition(
                                    Condition(
                                        id = editingId,
                                        name = name.trim(),
                                        status = status,
                                        iconKey = iconKey,
                                        notes = notes.trim().ifBlank { null }
                                    )
                                )
                                onDone()
                            }
                        }
                    ) { Text("Save") }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Condition name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Box(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                OutlinedTextField(
                    value = status,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Status") },
                    trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) },
                    interactionSource = statusFieldInteractionSource,
                    modifier = Modifier.fillMaxWidth()
                )
                DropdownMenu(
                    expanded = statusMenuExpanded,
                    onDismissRequest = { statusMenuExpanded = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    statusOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                status = option
                                statusMenuExpanded = false
                            }
                        )
                    }
                }
            }

            Text(
                "Icon",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(conditionIconOptions) { option ->
                        IconChoiceTile(
                            option = option,
                            selected = option.key == iconKey,
                            onClick = { iconKey = option.key }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
            )

            if (conditionId != null) {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            viewModel.getCondition(editingId)?.let { viewModel.deleteCondition(it) }
                            onDone()
                        }
                    },
                    modifier = Modifier.padding(top = 20.dp)
                ) {
                    Text("Delete Condition")
                }
            }
        }
    }
}

@Composable
private fun IconChoiceTile(option: ConditionIconOption, selected: Boolean, onClick: () -> Unit) {
    val background = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(option.icon, contentDescription = option.key, tint = tint, modifier = Modifier.size(22.dp))
    }
}
