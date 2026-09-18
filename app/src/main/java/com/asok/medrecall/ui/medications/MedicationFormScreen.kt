package com.asok.medrecall.ui.medications

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
import com.asok.medrecall.ui.components.BackIconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.asok.medrecall.data.local.Condition
import com.asok.medrecall.data.local.Doctor
import com.asok.medrecall.data.local.Medication
import com.asok.medrecall.ui.components.AutocompleteTextField
import com.asok.medrecall.ui.components.SaveIconButton
import com.asok.medrecall.ui.conditions.defaultConditionIconKey
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationFormScreen(
    medicationId: Int?,
    onDone: () -> Unit,
    viewModel: MedicationsViewModel = viewModel(factory = MedicationsViewModel.factory(LocalContext.current))
) {
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var loadedExisting by remember { mutableStateOf(medicationId == null) }
    var editingId by remember { mutableStateOf(0) }
    var name by remember { mutableStateOf("") }
    var dosage by remember { mutableStateOf("") }
    var schedule by remember { mutableStateOf("") }
    var doctorId by remember { mutableStateOf<Int?>(null) }
    var doctorNameField by remember { mutableStateOf("") }
    var conditionId by remember { mutableStateOf<Int?>(null) }
    var conditionNameField by remember { mutableStateOf("") }
    var conditionMenuExpanded by remember { mutableStateOf(false) }
    var showAddConditionDialog by remember { mutableStateOf(false) }
    var newConditionName by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(true) }

    // Same readOnly-field/interactionSource fix as ConditionFormScreen's status
    // dropdown -- a plain .clickable on a readOnly OutlinedTextField gets
    // swallowed by the field's own pointer input.
    val conditionFieldInteractionSource = remember { MutableInteractionSource() }
    LaunchedEffect(conditionFieldInteractionSource) {
        conditionFieldInteractionSource.interactions.collectLatest { interaction ->
            if (interaction is PressInteraction.Release) {
                conditionMenuExpanded = true
            }
        }
    }

    LaunchedEffect(medicationId) {
        if (medicationId != null) {
            viewModel.getMedication(medicationId)?.let { existing ->
                editingId = existing.id
                name = existing.name
                dosage = existing.dosage.orEmpty()
                schedule = existing.schedule.orEmpty()
                doctorId = existing.prescribingDoctorId
                conditionId = existing.conditionId
                notes = existing.notes.orEmpty()
                active = existing.active
                doctorNameField = uiState.doctors.firstOrNull { it.id == existing.prescribingDoctorId }?.name.orEmpty()
                conditionNameField = uiState.conditions.firstOrNull { it.id == existing.conditionId }?.name.orEmpty()
            }
            loadedExisting = true
        }
    }

    if (!loadedExisting) return

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (medicationId == null) "New Medication" else "Edit Medication", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    // Orange back arrow, top left -- leaves without saving,
                    // matching the same Cancel/Save icon layout used by
                    // every other form screen in the app.
                    BackIconButton(onClick = onDone)
                },
                actions = {
                    SaveIconButton(
                        enabled = name.isNotBlank(),
                        onClick = {
                            coroutineScope.launch {
                                var resolvedDoctorId = doctorId
                                if (resolvedDoctorId == null && doctorNameField.isNotBlank()) {
                                    resolvedDoctorId = viewModel.addDoctor(Doctor(name = doctorNameField.trim()))
                                }
                                var resolvedConditionId = conditionId
                                if (resolvedConditionId == null && conditionNameField.isNotBlank()) {
                                    // A condition created inline from this field
                                    // inherits this medication's own prescribing doctor, since it's
                                    // often the same doctor managing both.
                                    resolvedConditionId = viewModel.addCondition(
                                        Condition(
                                            name = conditionNameField.trim(),
                                            iconKey = defaultConditionIconKey,
                                            doctorId = resolvedDoctorId
                                        )
                                    )
                                }
                                viewModel.saveMedication(
                                    Medication(
                                        id = editingId,
                                        name = name.trim(),
                                        dosage = dosage.trim().ifBlank { null },
                                        schedule = schedule.trim().ifBlank { null },
                                        prescribingDoctorId = resolvedDoctorId,
                                        active = active,
                                        notes = notes.trim().ifBlank { null },
                                        conditionId = resolvedConditionId
                                    )
                                )
                                onDone()
                            }
                        }
                    )
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
                label = { Text("Medication name") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = dosage,
                onValueChange = { dosage = it },
                label = { Text("Dosage (e.g. .1 mg)") },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )

            OutlinedTextField(
                value = schedule,
                onValueChange = { schedule = it },
                label = { Text("Frequency (e.g. 2x/day)") },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )

            DoctorDropdownField(
                doctors = uiState.doctors,
                selectedDoctorId = doctorId,
                onSelect = { id -> doctorId = id },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )

            Box(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                OutlinedTextField(
                    value = uiState.conditions.firstOrNull { it.id == conditionId }?.name ?: "None",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Condition this treats (optional)") },
                    trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) },
                    interactionSource = conditionFieldInteractionSource,
                    modifier = Modifier.fillMaxWidth()
                )
                DropdownMenu(
                    expanded = conditionMenuExpanded,
                    onDismissRequest = { conditionMenuExpanded = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    DropdownMenuItem(
                        text = { Text("None") },
                        onClick = {
                            conditionId = null
                            conditionMenuExpanded = false
                        }
                    )
                    uiState.conditions.forEach { condition ->
                        DropdownMenuItem(
                            text = { Text(condition.name) },
                            onClick = {
                                conditionId = condition.id
                                conditionMenuExpanded = false
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("+ Add new condition") },
                        onClick = {
                            conditionMenuExpanded = false
                            newConditionName = ""
                            showAddConditionDialog = true
                        }
                    )
                }
            }

            if (showAddConditionDialog) {
                AlertDialog(
                    onDismissRequest = { showAddConditionDialog = false },
                    title = { Text("New Condition") },
                    text = {
                        OutlinedTextField(
                            value = newConditionName,
                            onValueChange = { newConditionName = it },
                            label = { Text("Condition name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        TextButton(
                            enabled = newConditionName.isNotBlank(),
                            onClick = {
                                val trimmedName = newConditionName.trim()
                                coroutineScope.launch {
                                    conditionId = viewModel.addCondition(Condition(name = trimmedName))
                                    showAddConditionDialog = false
                                }
                            }
                        ) { Text("Add") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAddConditionDialog = false }) { Text("Cancel") }
                    }
                )
            }

            AutocompleteTextField(
                value = conditionNameField,
                onValueChange = { newValue ->
                    conditionNameField = newValue
                    conditionId = uiState.conditions.firstOrNull { it.name.equals(newValue, ignoreCase = true) }?.id
                },
                label = "Condition this treats (optional)",
                suggestions = uiState.conditions.map { it.name },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                minLines = 3
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = active, onCheckedChange = { active = it })
                Text("Active")
            }

            if (medicationId != null) {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            viewModel.getMedication(editingId)?.let { viewModel.deleteMedication(it) }
                            onDone()
                        }
                    },
                    modifier = Modifier.padding(top = 20.dp)
                ) {
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun DoctorDropdownField(
    doctors: List<Doctor>,
    selectedDoctorId: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    // Same readOnly-field/interactionSource fix as the Condition dropdown
    // above -- a plain .clickable on a readOnly OutlinedTextField gets
    // swallowed by the field's own pointer input.
    val interactionSource = remember { MutableInteractionSource() }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collectLatest { interaction ->
            if (interaction is PressInteraction.Release) {
                expanded = true
            }
        }
    }
    val selectedName = doctors.firstOrNull { it.id == selectedDoctorId }?.name ?: "None"

    Box(modifier = modifier) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Prescribing doctor (optional)") },
            trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) },
            interactionSource = interactionSource,
            modifier = Modifier.fillMaxWidth()
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth()
        ) {
            DropdownMenuItem(
                text = { Text("None") },
                onClick = {
                    onSelect(null)
                    expanded = false
                }
            )
            doctors.forEach { doctor ->
                DropdownMenuItem(
                    text = { Text(doctor.name) },
                    onClick = {
                        onSelect(doctor.id)
                        expanded = false
                    }
                )
            }
        }
    }
}
