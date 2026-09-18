package com.asok.medrecall.ui.appointments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.runtime.Composable
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
import com.asok.medrecall.data.local.Appointment
import com.asok.medrecall.data.local.Doctor
import com.asok.medrecall.ui.components.BackIconButton
import com.asok.medrecall.ui.components.MedRecallTopBar
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Where completed / past-dated appointments go once they drop off the main
 * Appointments list (see AppointmentsViewModel.uiState vs. pastAppointments).
 * Filters are combined with AND: doctor + date range + keyword (matched
 * against reason, location, and notes) all narrow the same list further.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PastAppointmentsScreen(
    onEditAppointment: (Int) -> Unit,
    onBack: () -> Unit,
    onGoHome: () -> Unit,
    viewModel: AppointmentsViewModel = viewModel(factory = AppointmentsViewModel.factory(LocalContext.current))
) {
    val pastAppointments by viewModel.pastAppointments.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var keyword by remember { mutableStateOf("") }
    var doctorFilter by remember { mutableStateOf<Doctor?>(null) }
    var fromMillis by remember { mutableStateOf<Long?>(null) }
    var toMillis by remember { mutableStateOf<Long?>(null) }
    var showDoctorMenu by remember { mutableStateOf(false) }
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }

    val filtered = pastAppointments.filter { appointment ->
        val doctorName = uiState.doctors.firstOrNull { it.id == appointment.doctorId }?.name
        val matchesDoctor = doctorFilter == null || appointment.doctorId == doctorFilter?.id
        val matchesFrom = fromMillis == null || appointment.dateTime >= fromMillis!!
        val matchesTo = toMillis == null || appointment.dateTime <= toMillis!!
        val matchesKeyword = keyword.isBlank() || listOfNotNull(
            appointment.reason, appointment.location, appointment.notes, doctorName
        ).any { it.contains(keyword.trim(), ignoreCase = true) }
        matchesDoctor && matchesFrom && matchesTo && matchesKeyword
    }

    Scaffold(
        topBar = {
            MedRecallTopBar(
                title = "Past Appointments",
                onGoHome = onGoHome,
                actions = { BackIconButton(onClick = onBack) }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    label = { Text("Search reason, location, or notes") },
                    trailingIcon = {
                        if (keyword.isNotBlank()) {
                            IconButton(onClick = { keyword = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Box(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    OutlinedButton(onClick = { showDoctorMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(doctorFilter?.name ?: "All doctors")
                    }
                    DropdownMenu(expanded = showDoctorMenu, onDismissRequest = { showDoctorMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("All doctors") },
                            onClick = { doctorFilter = null; showDoctorMenu = false }
                        )
                        uiState.doctors.forEach { doctor ->
                            DropdownMenuItem(
                                text = { Text(doctor.name) },
                                onClick = { doctorFilter = doctor; showDoctorMenu = false }
                            )
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    OutlinedButton(onClick = { showFromPicker = true }, modifier = Modifier.weight(1f)) {
                        Text(fromMillis?.let { formatDate(it) } ?: "From date")
                    }
                    OutlinedButton(
                        onClick = { showToPicker = true },
                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                    ) {
                        Text(toMillis?.let { formatDate(it) } ?: "To date")
                    }
                    if (fromMillis != null || toMillis != null) {
                        IconButton(onClick = { fromMillis = null; toMillis = null }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear date range")
                        }
                    }
                }
            }

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (pastAppointments.isEmpty()) "No past appointments yet."
                        else "No past appointments match those filters."
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.id }) { appointment ->
                        val doctorName = uiState.doctors.firstOrNull { it.id == appointment.doctorId }?.name
                        PastAppointmentRow(
                            appointment = appointment,
                            doctorName = doctorName,
                            onClick = { onEditAppointment(appointment.id) }
                        )
                    }
                }
            }
        }
    }

    if (showFromPicker) {
        DatePickerField(
            initialMillis = fromMillis,
            onDismiss = { showFromPicker = false },
            onConfirm = { fromMillis = it; showFromPicker = false }
        )
    }

    if (showToPicker) {
        DatePickerField(
            initialMillis = toMillis,
            onDismiss = { showToPicker = false },
            onConfirm = { toMillis = it; showToPicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerField(
    initialMillis: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long?) -> Unit
) {
    val datePickerState = androidx.compose.material3.rememberDatePickerState(
        initialSelectedDateMillis = initialMillis ?: System.currentTimeMillis()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(datePickerState.selectedDateMillis) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DatePicker(state = datePickerState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PastAppointmentRow(appointment: Appointment, doctorName: String?, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(formatDateTime(appointment.dateTime), style = MaterialTheme.typography.labelLarge)
            Text(appointment.reason, style = MaterialTheme.typography.titleMedium)
            if (doctorName != null) {
                Text(doctorName, style = MaterialTheme.typography.bodyMedium)
            }
            if (!appointment.location.isNullOrBlank()) {
                Text(appointment.location, style = MaterialTheme.typography.bodySmall)
            }
            if (appointment.completed) {
                Text("Completed", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun formatDateTime(epochMillis: Long): String {
    val formatter = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy 'at' h:mm a")
    return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(formatter)
}

private fun formatDate(epochMillis: Long): String {
    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
    return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(formatter)
}
