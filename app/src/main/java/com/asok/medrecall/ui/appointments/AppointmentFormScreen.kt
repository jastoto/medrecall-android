package com.asok.medrecall.ui.appointments

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import com.asok.medrecall.data.local.Appointment
import com.asok.medrecall.data.local.Doctor
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

/**
 * Create/edit an appointment. [initialDateMillis], when given (tapping a
 * date on the Calendar screen), pre-fills a brand-new appointment's date --
 * ignored when editing an existing one, since that loads its own date.
 * Doctor selection is the type-to-filter dropdown below (pick an existing
 * doctor, or type a new name to create one on Save). Saving also
 * best-effort syncs to whichever device calendar is selected in
 * Settings > Calendar Sync (see AppointmentsViewModel.syncToDeviceCalendar).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppointmentFormScreen(
    appointmentId: Int?,
    initialDateMillis: Long? = null,
    onDone: () -> Unit,
    viewModel: AppointmentsViewModel = viewModel(factory = AppointmentsViewModel.factory(LocalContext.current))
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var loadedExisting by remember { mutableStateOf(appointmentId == null) }
    var editingId by remember { mutableStateOf(0) }
    var reason by remember { mutableStateOf("") }
    var dateMillis by remember { mutableStateOf(initialDateMillis ?: System.currentTimeMillis()) }
    var doctorId by remember { mutableStateOf<Int?>(null) }
    var doctorNameField by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var completed by remember { mutableStateOf(false) }
    var deviceCalendarEventId by remember { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    LaunchedEffect(appointmentId) {
        if (appointmentId != null) {
            viewModel.getAppointment(appointmentId)?.let { existing ->
                editingId = existing.id
                reason = existing.reason
                dateMillis = existing.dateTime
                doctorId = existing.doctorId
                location = existing.location.orEmpty()
                notes = existing.notes.orEmpty()
                completed = existing.completed
                deviceCalendarEventId = existing.deviceCalendarEventId
                doctorNameField = uiState.doctors.firstOrNull { it.id == existing.doctorId }?.name.orEmpty()
            }
            loadedExisting = true
        }
    }

    if (!loadedExisting) return

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text(if (appointmentId == null) "New Appointment" else "Edit Appointment", fontWeight = FontWeight.Bold, color = Color.Black) }) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                label = { Text("Reason") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
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

            DoctorDropdown(
                doctors = uiState.doctors,
                selectedName = doctorNameField,
                onNameChange = { name ->
                    doctorNameField = name
                    doctorId = uiState.doctors.firstOrNull { it.name.equals(name, ignoreCase = true) }?.id
                },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )

            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("Location") },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                minLines = 3
            )

            // Completed status is hidden from this form for now (still saved/used by
            // Reports -- see completed var above) -- Asok found it confusing next to Save.

            Row(modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            var resolvedDoctorId = doctorId
                            if (resolvedDoctorId == null && doctorNameField.isNotBlank()) {
                                resolvedDoctorId = viewModel.addDoctor(Doctor(name = doctorNameField.trim()))
                            }
                            val saved = viewModel.saveAppointment(
                                Appointment(
                                    id = editingId,
                                    dateTime = dateMillis,
                                    reason = reason.trim(),
                                    doctorId = resolvedDoctorId,
                                    location = location.trim().ifBlank { null },
                                    notes = notes.trim().ifBlank { null },
                                    completed = completed,
                                    deviceCalendarEventId = deviceCalendarEventId
                                )
                            )
                            val doctorName = uiState.doctors.firstOrNull { it.id == resolvedDoctorId }?.name
                            viewModel.syncToDeviceCalendar(context, saved, doctorName)
                            onDone()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = reason.isNotBlank()
                ) {
                    Text("Save")
                }
                if (appointmentId != null) {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                viewModel.getAppointment(editingId)?.let { viewModel.deleteAppointment(context, it) }
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

@Composable
private fun DoctorDropdown(
    doctors: List<Doctor>,
    selectedName: String,
    onNameChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val filtered = doctors.filter { it.name.contains(selectedName, ignoreCase = true) }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {
                onNameChange(it)
                expanded = it.isNotBlank()
            },
            label = { Text("Doctor (optional)") },
            modifier = Modifier.fillMaxWidth()
        )
        DropdownMenu(
            expanded = expanded && filtered.isNotEmpty(),
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth()
        ) {
            filtered.forEach { doctor ->
                DropdownMenuItem(
                    text = { Text(doctor.name) },
                    onClick = {
                        onNameChange(doctor.name)
                        expanded = false
                    }
                )
            }
        }
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
