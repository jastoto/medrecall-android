package com.asok.medrecall.ui.doctors

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.asok.medrecall.data.local.Appointment
import com.asok.medrecall.data.local.Condition
import com.asok.medrecall.data.local.Doctor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.asok.medrecall.ui.components.MedRecallTopBar

private val tileColors = listOf(
    Color(0xFFE0435A), // red
    Color(0xFF6C63E8), // indigo/purple
    Color(0xFFFF9F43), // orange
    Color(0xFF2BB3A3), // teal
    Color(0xFF4CAF50), // green
    Color(0xFFAB47BC)  // purple
)

private fun colorForDoctor(doctorId: Int): Color =
    tileColors[Math.floorMod(doctorId, tileColors.size)]

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoctorsScreen(
    onAddDoctor: () -> Unit,
    onEditDoctor: (Int) -> Unit,
    onEditAppointment: (Int) -> Unit,
    onGoHome: () -> Unit,
    viewModel: DoctorsViewModel = viewModel(factory = DoctorsViewModel.factory(LocalContext.current))
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            MedRecallTopBar(
                title = "Doctors",
                onGoHome = onGoHome,
                actions = {
                    IconButton(onClick = onAddDoctor) {
                        Icon(Icons.Default.Add, contentDescription = "Add doctor")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (uiState.upcomingAppointments.isNotEmpty()) {
                item {
                    Text(
                        "Upcoming Appointments",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                item {
                    val doctorsById = uiState.doctors.associateBy { it.id }
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            uiState.upcomingAppointments.forEachIndexed { index, appointment ->
                                val doctorName = appointment.doctorId?.let { doctorsById[it]?.name }
                                UpcomingAppointmentRow(
                                    appointment = appointment,
                                    doctorName = doctorName,
                                    onClick = { onEditAppointment(appointment.id) }
                                )
                                if (index < uiState.upcomingAppointments.lastIndex) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "Doctors",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
            }

            if (uiState.doctors.isEmpty() && !uiState.isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
                        Text("No doctors yet. Tap + to add one.")
                    }
                }
            } else {
                items(uiState.doctors.chunked(4)) { rowDoctors ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowDoctors.forEach { doctor ->
                            DoctorTile(
                                doctor = doctor,
                                managedConditions = uiState.conditions.filter { it.doctorId == doctor.id },
                                onClick = { onEditDoctor(doctor.id) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(4 - rowDoctors.size) {
                            Box(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpcomingAppointmentRow(appointment: Appointment, doctorName: String?, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Text(doctorName ?: appointment.reason, style = MaterialTheme.typography.titleMedium)
        Text(formatDateTime(appointment.dateTime), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DoctorTile(
    doctor: Doctor,
    managedConditions: List<Condition>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .background(colorForDoctor(doctor.id), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.MedicalServices,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
        Text(
            doctor.name,
            style = MaterialTheme.typography.labelLarge,
            color = Color.Black,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
        if (!doctor.specialty.isNullOrBlank()) {
            Text(
                doctor.specialty,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (managedConditions.isNotEmpty()) {
            Text(
                managedConditions.joinToString(", ") { it.name },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

private fun formatDateTime(epochMillis: Long): String {
    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a")
    return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(formatter)
}
