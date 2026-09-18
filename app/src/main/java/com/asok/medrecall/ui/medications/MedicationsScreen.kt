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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.asok.medrecall.data.local.Medication
import com.asok.medrecall.ui.components.MedRecallTopBar
import com.asok.medrecall.ui.components.SwipeToDeleteRow
import kotlinx.coroutines.launch

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
    onGoHome: () -> Unit,
    viewModel: MedicationsViewModel = viewModel(factory = MedicationsViewModel.factory(LocalContext.current))
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Ids swiped-away but not yet actually deleted -- see ConditionsScreen
    // for the same pattern and why it's done this way (instant visual
    // removal + Undo snackbar, commit-on-timeout).
    var pendingDeleteIds by remember { mutableStateOf(setOf<Int>()) }

    fun deleteWithUndo(medication: Medication) {
        pendingDeleteIds = pendingDeleteIds + medication.id
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Deleted ${medication.name}",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                pendingDeleteIds = pendingDeleteIds - medication.id
            } else {
                viewModel.deleteMedication(medication)
                pendingDeleteIds = pendingDeleteIds - medication.id
            }
        }
    }

    Scaffold(
        topBar = {
            MedRecallTopBar(
                title = "Medications",
                onGoHome = onGoHome,
                actions = {
                    IconButton(onClick = onAddMedication) {
                        Icon(Icons.Default.Add, contentDescription = "Add medication")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        val visibleMedications = uiState.medications.filter { it.id !in pendingDeleteIds }

        if (visibleMedications.isEmpty() && !uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("No medications yet. Tap + to add one.")
            }
        } else {
            val doctorsById = uiState.doctors.associateBy { it.id }
            val grouped = visibleMedications
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
                        onEditMedication = onEditMedication,
                        onDeleteMedication = ::deleteWithUndo
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
    onEditMedication: (Int) -> Unit,
    onDeleteMedication: (Medication) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Doctor name is a plain section heading above the card -- not
        // part of the bordered/elevated box -- so it reads as a group
        // label for the medications underneath rather than looking like
        // it belongs inside the box with them.
        Text(
            text = doctorName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            val rowBackgroundColor = CardDefaults.cardColors().containerColor
            Column {
                medications.forEachIndexed { index, medication ->
                    SwipeToDeleteRow(contentBackgroundColor = rowBackgroundColor,
                        onDelete = { onDeleteMedication(medication) }) {
                        MedicationRow(
                            medication = medication,
                            onClick = { onEditMedication(medication.id) }
                        )
                    }
                    if (index < medications.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
                color = MaterialTheme.colorScheme.onSurface,
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
