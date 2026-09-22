package com.asok.medrecall.ui.health

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.asok.medrecall.data.local.CUSTOM_VITAL_TYPE
import com.asok.medrecall.data.local.VitalReading
import com.asok.medrecall.ui.components.BackIconButton
import com.asok.medrecall.ui.components.SaveIconButton
import com.asok.medrecall.ui.vitals.VitalsViewModel
import com.asok.medrecall.ui.vitals.formatNumber
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Entry form for one-off Custom readings on the Health page (punch item
 * #13) -- unlike the fixed VitalType forms, every field (label, value,
 * unit) is typed fresh each time; there's no shared "type" to remember
 * between entries, per Asok's call. Reuses VitalsViewModel scoped to the
 * synthetic CUSTOM_VITAL_TYPE string -- same generic save/delete/get
 * machinery the real vital types use, since VitalsViewModel only ever
 * needed a type string, not the VitalType enum itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomReadingFormScreen(
    readingId: Int?,
    onDone: () -> Unit,
    onGoBack: () -> Unit,
    viewModel: VitalsViewModel = viewModel(factory = VitalsViewModel.factory(LocalContext.current, CUSTOM_VITAL_TYPE))
) {
    val coroutineScope = rememberCoroutineScope()

    var loadedExisting by remember { mutableStateOf(readingId == null) }
    var editingId by remember { mutableStateOf(0) }
    var dateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var label by remember { mutableStateOf("") }
    var valueText by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    LaunchedEffect(readingId) {
        if (readingId != null) {
            viewModel.getReading(readingId)?.let { existing ->
                editingId = existing.id
                dateMillis = existing.recordedAt
                label = existing.customLabel.orEmpty()
                valueText = formatNumber(existing.primaryValue)
                unit = existing.customUnit.orEmpty()
                notes = existing.notes.orEmpty()
            }
            loadedExisting = true
        }
    }

    if (!loadedExisting) return

    val isValid = label.isNotBlank() && valueText.toDoubleOrNull() != null

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (readingId == null) "Log Custom Reading" else "Edit Custom Reading", fontWeight = FontWeight.Bold) },
                navigationIcon = { BackIconButton(onClick = onGoBack) },
                actions = {
                    SaveIconButton(
                        enabled = isValid,
                        onClick = {
                            coroutineScope.launch {
                                viewModel.saveReading(
                                    VitalReading(
                                        id = editingId,
                                        type = CUSTOM_VITAL_TYPE,
                                        recordedAt = dateMillis,
                                        primaryValue = valueText.toDouble(),
                                        customLabel = label.trim(),
                                        customUnit = unit.trim().ifBlank { null },
                                        notes = notes.trim().ifBlank { null }
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

            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("What are you tracking?") },
                placeholder = { Text("e.g. Peak Flow") },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )

            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                OutlinedTextField(
                    value = valueText,
                    onValueChange = { valueText = it },
                    label = { Text("Value") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text("Unit, optional") },
                    placeholder = { Text("e.g. L/min") },
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                )
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
            )

            if (readingId != null) {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            viewModel.getReading(editingId)?.let { viewModel.deleteReading(it) }
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
