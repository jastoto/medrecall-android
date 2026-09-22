package com.asok.medrecall.ui.appointments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.asok.medrecall.data.local.Appointment
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import com.asok.medrecall.ui.components.MedRecallTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppointmentsScreen(
    onAddAppointment: () -> Unit,
    onEditAppointment: (Int) -> Unit,
    onViewPastAppointments: () -> Unit,
    onGoHome: () -> Unit,
    onGoBack: (() -> Unit)? = null,
    viewModel: AppointmentsViewModel = viewModel(factory = AppointmentsViewModel.factory(LocalContext.current))
) {
    val uiState by viewModel.uiState.collectAsState()
    val pendingGoogleDeletions by viewModel.pendingGoogleDeletions.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            MedRecallTopBar(
                title = "Appointments",
                onGoHome = onGoHome,
                onGoBack = onGoBack,
                actions = {
                    IconButton(onClick = onViewPastAppointments) {
                        Icon(Icons.Default.History, contentDescription = "Past appointments")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddAppointment) {
                Icon(Icons.Default.Add, contentDescription = "Add appointment")
            }
        }
    ) { innerPadding ->
        if (uiState.appointments.isEmpty() && !uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("No appointments yet. Tap + to add one.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.appointments, key = { it.id }) { appointment ->
                    val doctorName = uiState.doctors.firstOrNull { it.id == appointment.doctorId }?.name
                    AppointmentRow(
                        appointment = appointment,
                        doctorName = doctorName,
                        onClick = { onEditAppointment(appointment.id) }
                    )
                }
            }
        }
    }

    pendingGoogleDeletions.firstOrNull()?.let { appointment ->
        GooglePendingDeletionDialog(
            appointment = appointment,
            onRemove = { coroutineScope.launch { viewModel.confirmGoogleDeletion(appointment) } },
            onKeep = { coroutineScope.launch { viewModel.dismissGoogleDeletion(appointment) } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppointmentRow(appointment: Appointment, doctorName: String?, onClick: () -> Unit) {
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
        }
    }
}

private fun formatDateTime(epochMillis: Long): String {
    val formatter = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy 'at' h:mm a")
    return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(formatter)
}

/**
 * One-time "this was removed from Google Calendar" confirmation (see
 * calendar-two-way-sync-scope.md §5.1) -- shows one at a time even if
 * several came back flagged from the same reconciliation pass.
 */
@Composable
private fun GooglePendingDeletionDialog(
    appointment: Appointment,
    onRemove: () -> Unit,
    onKeep: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onKeep,
        title = { Text("Removed from Google Calendar") },
        text = {
            Text(
                "\"${appointment.reason}\" (${formatDateTime(appointment.dateTime)}) was deleted from Google Calendar. " +
                    "Remove it from MedRecall+ too?"
            )
        },
        confirmButton = {
            TextButton(onClick = onRemove) { Text("Remove") }
        },
        dismissButton = {
            TextButton(onClick = onKeep) { Text("Keep in MedRecall+") }
        }
    )
}
