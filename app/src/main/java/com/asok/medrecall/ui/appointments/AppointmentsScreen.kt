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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.asok.medrecall.data.local.Appointment
import com.asok.medrecall.ui.components.MedRecallTopBar
import com.asok.medrecall.ui.components.MonthCalendarView
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The Calendar screen: a 12-month-back/12-month-forward month calendar
 * (see MonthCalendarView) with a dot on every date that has an appointment,
 * plus the list of appointments for whichever date is selected (today by
 * default). Tapping a date selects it; the FAB always schedules a new
 * appointment on the selected date. Doctor selection happens in
 * AppointmentFormScreen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppointmentsScreen(
    onAddAppointment: (Long) -> Unit,
    onEditAppointment: (Int) -> Unit,
    onGoHome: () -> Unit,
    viewModel: AppointmentsViewModel = viewModel(factory = AppointmentsViewModel.factory(LocalContext.current))
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }

    val markedDates = remember(uiState.appointments) {
        uiState.appointments.map { it.dateTime.toLocalDate() }.toSet()
    }
    val appointmentsOnSelectedDate = remember(uiState.appointments, selectedDate) {
        uiState.appointments
            .filter { it.dateTime.toLocalDate() == selectedDate }
            .sortedBy { it.dateTime }
    }

    Scaffold(
        topBar = {
            MedRecallTopBar(
                title = "Calendar",
                onGoHome = onGoHome
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                val defaultTime = if (selectedDate == LocalDate.now()) LocalTime.now() else LocalTime.of(9, 0)
                onAddAppointment(selectedDate.atTime(defaultTime).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add appointment")
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            MonthCalendarView(
                selectedDate = selectedDate,
                markedDates = markedDates,
                onDateSelected = { selectedDate = it },
                modifier = Modifier.padding(16.dp)
            )

            Text(
                text = "Appointments on ${formatSelectedDate(selectedDate)}",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (appointmentsOnSelectedDate.isEmpty() && !uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Text("No appointments on this date. Tap + to add one.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(appointmentsOnSelectedDate, key = { it.id }) { appointment ->
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

private fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

private fun formatDateTime(epochMillis: Long): String {
    val formatter = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy 'at' h:mm a")
    return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(formatter)
}

private fun formatSelectedDate(date: LocalDate): String {
    val formatter = DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy")
    return date.format(formatter)
}
