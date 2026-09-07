package com.asok.medrecall.ui.medications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.asok.medrecall.data.local.Doctor
import com.asok.medrecall.data.local.Medication

private val tileColors = listOf(
    Color(0xFFE0435A), // red
    Color(0xFF6C63E8), // indigo/purple
    Color(0xFFFF9F43), // orange
    Color(0xFF2BB3A3), // teal
    Color(0xFF4CAF50), // green
    Color(0xFFAB47BC)  // purple
)

private fun colorForMedication(medicationId: Int): Color =
    tileColors[Math.floorMod(medicationId, tileColors.size)]

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationsScreen(
    onAddMedication: () -> Unit,
    onEditMedication: (Int) -> Unit,
    viewModel: MedicationsViewModel = viewModel(factory = MedicationsViewModel.factory(LocalContext.current))
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Medications") },
                actions = {
                    IconButton(onClick = onAddMedication) {
                        Icon(Icons.Default.Add, contentDescription = "Add medication")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (uiState.medications.isEmpty() && !uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("No medications yet. Tap + to add one.")
            }
        } else {
            val doctorsById = uiState.doctors.associateBy { it.id }
            val grouped = uiState.medications
                .sortedWith(compareByDescending<Medication> { it.active }.thenBy { it.name })
                .groupBy { med -> med.prescribingDoctorId?.let { doctorsById[it] } }
                .toList()
                .sortedBy { (doctor, _) -> doctor?.name ?: "￿" } // unassigned group sorts last

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(grouped, key = { (doctor, _) -> doctor?.id ?: -1 }) { (doctor, meds) ->
                    DoctorMedicationGroup(
                        doctorName = doctor?.name ?: "No Doctor Assigned",
                        medications = meds,
                        onEditMedication = onEditMedication
                    )
                }
            }
        }
    }
}

@Composable
private fun DoctorMedicationGroup(
    doctorName: String,
    medications: List<Medication>,
    onEditMedication: (Int) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = doctorName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            medications.forEachIndexed { index, medication ->
                MedicationRow(
                    medication = medication,
                    onClick = { onEditMedication(medication.id) }
                )
                if (index < medications.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun MedicationRow(medication: Medication, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(colorForMedication(medication.id), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Medication, contentDescription = null, tint = Color.White)
        }
        Column(
            modifier = Modifier
                .padding(start = 12.dp)
        ) {
            Text(
                text = medication.name,
                style = MaterialTheme.typography.titleMedium,
                textDecoration = if (medication.active) TextDecoration.None else TextDecoration.LineThrough
            )
            val details = listOfNotNull(medication.dosage, medication.schedule).joinToString(" · ")
            if (details.isNotBlank()) {
                Text(details, style = MaterialTheme.typography.bodyMedium)
            }
            if (!medication.active) {
                Text("Inactive", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
