package com.asok.medrecall.ui.vitals

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.asok.medrecall.data.local.VitalReading
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val glucoseContextOptions = listOf("Fasting", "Before Meal", "After Meal", "Other")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VitalEntryFormScreen(
    vitalType: VitalType,
    readingId: Int?,
    onDone: () -> Unit,
    viewModel: VitalsViewModel = viewModel(factory = VitalsViewModel.factory(LocalContext.current, vitalType.id))
) {
    val coroutineScope = rememberCoroutineScope()

    var loadedExisting by remember { mutableStateOf(readingId == null) }
    var editingId by remember { mutableStateOf(0) }
    var dateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var primaryText by remember { mutableStateOf("") }
    var secondaryText by remember { mutableStateOf("") }
    var tertiaryText by remember { mutableStateOf("") }
    var readingContext by remember { mutableStateOf(glucoseContextOptions.first()) }
    var notes by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var contextMenuExpanded by remember { mutableStateOf(false) }

    // Same fix as the doctor-picker / condition-status dropdowns elsewhere
    // in this app: a plain .clickable on a readOnly OutlinedTextField gets
    // swallowed by the field's own pointer-input handling, so open the menu
    // off the field's interactionSource instead.
    val contextFieldInteractionSource = remember { MutableInteractionSource() }
    LaunchedEffect(contextFieldInteractionSource) {
        contextFieldInteractionSource.interactions.collectLatest { interaction ->
            if (interaction is PressInteraction.Release) contextMenuExpanded = true
        }
    }

    LaunchedEffect(readingId) {
        if (readingId != null) {
            viewModel.getReading(readingId)?.let { existing ->
                editingId = existing.id
                dateMillis = existing.recordedAt
                primaryText = formatNumber(existing.primaryValue)
                secondaryText = existing.secondaryValue?.let { formatNumber(it) } ?: ""
                tertiaryText = existing.tertiaryValue?.let { formatNumber(it) } ?: ""
                readingContext = existing.context ?: glucoseContextOptions.first()
                notes = existing.notes.orEmpty()
            }
            loadedExisting = true
        }
    }

    if (!loadedExisting) return

    val isValid = primaryText.toDoubleOrNull() != null &&
        (vitalType != VitalType.BLOOD_PRESSURE || secondaryText.toDoubleOrNull() != null)

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(if (readingId == null) "Log ${vitalType.title}" else "Edit ${vitalType.title}") })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.weight(1f)) {
                    Text(formatDate(dateMillis))
                }
                OutlinedButton(
                    onClick = { showTimePicker = true },
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                ) {
                    Text(formatTime(dateMillis))
                }
            }

            when (vitalType) {
                VitalType.BLOOD_PRESSURE -> {
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        OutlinedTextField(
                            value = primaryText,
                            onValueChange = { primaryText = it },
                            label = { Text("Systolic") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = secondaryText,
                            onValueChange = { secondaryText = it },
                            label = { Text("Diastolic") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f).padding(start = 8.dp)
                        )
                    }
                    OutlinedTextField(
                        value = tertiaryText,
                        onValueChange = { tertiaryText = it },
                        label = { Text("Pulse, optional") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    )
                }
                VitalType.WEIGHT -> OutlinedTextField(
                    value = primaryText,
                    onValueChange = { primaryText = it },
                    label = { Text("Weight (lb)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
                VitalType.BLOOD_GLUCOSE -> {
                    OutlinedTextField(
                        value = primaryText,
                        onValueChange = { primaryText = it },
                        label = { Text("Reading (mg/dL)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    )
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        OutlinedTextField(
                            value = readingContext,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Context") },
                            trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) },
                            interactionSource = contextFieldInteractionSource,
                            modifier = Modifier.fillMaxWidth()
                        )
                        DropdownMenu(
                            expanded = contextMenuExpanded,
                            onDismissRequest = { contextMenuExpanded = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            glucoseContextOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        readingContext = option
                                        contextMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                VitalType.HEART_RATE -> OutlinedTextField(
                    value = primaryText,
                    onValueChange = { primaryText = it },
                    label = { Text("Heart Rate (bpm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
                VitalType.RESTING_HEART_RATE -> OutlinedTextField(
                    value = primaryText,
                    onValueChange = { primaryText = it },
                    label = { Text("Resting Heart Rate (bpm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
                VitalType.HEART_RATE_VARIABILITY -> OutlinedTextField(
                    value = primaryText,
                    onValueChange = { primaryText = it },
                    label = { Text("HRV (ms)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
                VitalType.BLOOD_OXYGEN -> OutlinedTextField(
                    value = primaryText,
                    onValueChange = { primaryText = it },
                    label = { Text("Blood Oxygen (%)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
                VitalType.RESPIRATORY_RATE -> OutlinedTextField(
                    value = primaryText,
                    onValueChange = { primaryText = it },
                    label = { Text("Respiratory Rate (breaths/min)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
                VitalType.BODY_TEMPERATURE -> OutlinedTextField(
                    value = primaryText,
                    onValueChange = { primaryText = it },
                    label = { Text("Body Temperature (°F)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
            )

            Row(modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            viewModel.saveReading(
                                VitalReading(
                                    id = editingId,
                                    type = vitalType.id,
                                    recordedAt = dateMillis,
                                    primaryValue = primaryText.toDouble(),
                                    secondaryValue = if (vitalType == VitalType.BLOOD_PRESSURE) secondaryText.toDoubleOrNull() else null,
                                    tertiaryValue = if (vitalType == VitalType.BLOOD_PRESSURE) tertiaryText.toDoubleOrNull() else null,
                                    context = if (vitalType == VitalType.BLOOD_GLUCOSE) readingContext else null,
                                    notes = notes.trim().ifBlank { null }
                                )
                            )
                            onDone()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = isValid
                ) {
                    Text("Save")
                }
                if (readingId != null) {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                viewModel.getReading(editingId)?.let { viewModel.deleteReading(it) }
                                onDone()
                            }
                        },
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text("Delete")
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val initialDate = Instant.ofEpochMilli(dateMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { selectedMillis ->
                        // Material3's DatePicker reports the picked date at UTC midnight.
                        val pickedDate = Instant.ofEpochMilli(selectedMillis).atZone(ZoneId.of("UTC")).toLocalDate()
                        val existingTime = Instant.ofEpochMilli(dateMillis).atZone(ZoneId.systemDefault()).toLocalTime()
                        dateMillis = pickedDate.atTime(existingTime).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val initialTime = Instant.ofEpochMilli(dateMillis).atZone(ZoneId.systemDefault()).toLocalTime()
        val timePickerState = rememberTimePickerState(
            initialHour = initialTime.hour,
            initialMinute = initialTime.minute,
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val existingDate = Instant.ofEpochMilli(dateMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                    val pickedTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                    dateMillis = existingDate.atTime(pickedTime).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } },
            text = { TimePicker(state = timePickerState) }
        )
    }
}

private fun formatDate(epochMillis: Long): String {
    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
    return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(formatter)
}

private fun formatTime(epochMillis: Long): String {
    val formatter = DateTimeFormatter.ofPattern("h:mm a")
    return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(formatter)
}
