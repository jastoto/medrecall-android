package com.asok.medrecall.ui.medicalid

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
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.asok.medrecall.data.local.Patient
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val bloodTypeOptions = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-", "Unknown")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicalIdFormScreen(
    onDone: () -> Unit,
    viewModel: MedicalIdViewModel = viewModel(factory = MedicalIdViewModel.factory(LocalContext.current))
) {
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var loadedExisting by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var addressLine by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var state by remember { mutableStateOf("") }
    var zip by remember { mutableStateOf("") }
    var dobMillis by remember { mutableStateOf<Long?>(null) }
    var bloodType by remember { mutableStateOf("") }
    var allergies by remember { mutableStateOf("") }
    var emergencyName by remember { mutableStateOf("") }
    var emergencyRelationship by remember { mutableStateOf("") }
    var emergencyPhone by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var bloodTypeMenuExpanded by remember { mutableStateOf(false) }

    // Same fix already proven on RecordVisitScreen/ConditionFormScreen: a
    // plain .clickable on a readOnly OutlinedTextField gets swallowed by the
    // field's own pointer-input handling.
    val bloodTypeFieldInteractionSource = remember { MutableInteractionSource() }
    LaunchedEffect(bloodTypeFieldInteractionSource) {
        bloodTypeFieldInteractionSource.interactions.collectLatest { interaction ->
            if (interaction is PressInteraction.Release) {
                bloodTypeMenuExpanded = true
            }
        }
    }

    // Populate the fields once, the first time the patient row has loaded --
    // uiState keeps emitting on every DB change, so this must not re-run and
    // clobber in-progress edits.
    LaunchedEffect(uiState.isLoading) {
        if (!loadedExisting && !uiState.isLoading) {
            uiState.patient?.let { existing ->
                name = existing.name
                addressLine = existing.addressLine.orEmpty()
                city = existing.city.orEmpty()
                state = existing.state.orEmpty()
                zip = existing.zip.orEmpty()
                dobMillis = existing.dateOfBirth
                bloodType = existing.bloodType.orEmpty()
                allergies = existing.allergies.orEmpty()
                emergencyName = existing.emergencyContactName.orEmpty()
                emergencyRelationship = existing.emergencyContactRelationship.orEmpty()
                emergencyPhone = existing.emergencyContactPhone.orEmpty()
            }
            loadedExisting = true
        }
    }

    if (!loadedExisting) return

    Scaffold(
        topBar = { TopAppBar(title = { Text("Edit Medical ID") }) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            SectionHeader("Personal Information")

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Full name") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = addressLine,
                onValueChange = { addressLine = it },
                label = { Text("Street address") },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )

            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = { Text("City") },
                    modifier = Modifier.weight(1.4f)
                )
                OutlinedTextField(
                    value = state,
                    onValueChange = { state = it },
                    label = { Text("State") },
                    modifier = Modifier.weight(0.8f).padding(start = 8.dp)
                )
                OutlinedTextField(
                    value = zip,
                    onValueChange = { zip = it },
                    label = { Text("ZIP") },
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                )
            }

            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            ) {
                Text(if (dobMillis != null) "DOB: ${formatDate(dobMillis!!)}" else "Set date of birth")
            }

            Box(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                OutlinedTextField(
                    value = bloodType,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Blood type") },
                    trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) },
                    interactionSource = bloodTypeFieldInteractionSource,
                    modifier = Modifier.fillMaxWidth()
                )
                DropdownMenu(
                    expanded = bloodTypeMenuExpanded,
                    onDismissRequest = { bloodTypeMenuExpanded = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    bloodTypeOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                bloodType = option
                                bloodTypeMenuExpanded = false
                            }
                        )
                    }
                }
            }

            SectionHeader("Allergies")

            OutlinedTextField(
                value = allergies,
                onValueChange = { allergies = it },
                label = { Text("Allergies") },
                placeholder = { Text("e.g. Rituxan, Penicillin") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            SectionHeader("Emergency Contact")

            OutlinedTextField(
                value = emergencyName,
                onValueChange = { emergencyName = it },
                label = { Text("Contact name") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = emergencyRelationship,
                onValueChange = { emergencyRelationship = it },
                label = { Text("Relationship") },
                placeholder = { Text("e.g. Spouse") },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )

            OutlinedTextField(
                value = emergencyPhone,
                onValueChange = { emergencyPhone = it },
                label = { Text("Phone") },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )

            Row(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            viewModel.savePatient(
                                Patient(
                                    id = 1,
                                    name = name.trim(),
                                    addressLine = addressLine.trim().ifBlank { null },
                                    city = city.trim().ifBlank { null },
                                    state = state.trim().ifBlank { null },
                                    zip = zip.trim().ifBlank { null },
                                    dateOfBirth = dobMillis,
                                    bloodType = bloodType.trim().ifBlank { null },
                                    allergies = allergies.trim().ifBlank { null },
                                    emergencyContactName = emergencyName.trim().ifBlank { null },
                                    emergencyContactRelationship = emergencyRelationship.trim().ifBlank { null },
                                    emergencyContactPhone = emergencyPhone.trim().ifBlank { null },
                                    notes = uiState.patient?.notes
                                )
                            )
                            onDone()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = name.isNotBlank()
                ) {
                    Text("Save")
                }
                TextButton(onClick = onDone, modifier = Modifier.padding(start = 8.dp)) {
                    Text("Cancel")
                }
            }
        }
    }

    if (showDatePicker) {
        val initialMillis = dobMillis ?: System.currentTimeMillis()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { selectedMillis ->
                        // Material3's DatePicker reports the picked date at UTC midnight.
                        val pickedDate = Instant.ofEpochMilli(selectedMillis).atZone(ZoneId.of("UTC")).toLocalDate()
                        dobMillis = pickedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
    )
}

private fun formatDate(epochMillis: Long): String {
    val formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy")
    return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(formatter)
}
