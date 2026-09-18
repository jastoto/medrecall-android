package com.asok.medrecall.ui.doctors

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
import com.asok.medrecall.ui.components.BackIconButton
import com.asok.medrecall.ui.components.SaveIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.asok.medrecall.data.local.Doctor
import com.asok.medrecall.ui.components.AutocompleteTextField
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight

private val commonSpecialties = listOf(
    "Primary Care", "Cardiologist", "Dermatologist", "Endocrinologist",
    "Gastroenterologist", "Neurologist", "OB/GYN", "Oncologist",
    "Ophthalmologist", "Optometrist", "Orthopedist", "Pediatrician",
    "Psychiatrist", "Pulmonologist", "Rheumatologist", "Urologist",
    "Dentist", "Physical Therapist", "ENT (Otolaryngologist)"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoctorFormScreen(
    doctorId: Int?,
    onDone: () -> Unit,
    viewModel: DoctorsViewModel = viewModel(factory = DoctorsViewModel.factory(LocalContext.current))
) {
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var loadedExisting by remember { mutableStateOf(doctorId == null) }
    var editingId by remember { mutableStateOf(0) }
    var name by remember { mutableStateOf("") }
    var specialty by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    LaunchedEffect(doctorId) {
        if (doctorId != null) {
            viewModel.getDoctor(doctorId)?.let { existing ->
                editingId = existing.id
                name = existing.name
                specialty = existing.specialty.orEmpty()
                phone = existing.phone.orEmpty()
                address = existing.address.orEmpty()
                notes = existing.notes.orEmpty()
            }
            loadedExisting = true
        }
    }

    if (!loadedExisting) return

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (doctorId == null) "New Doctor" else "Edit Doctor", fontWeight = FontWeight.Bold) },
                navigationIcon = { BackIconButton(onClick = onDone) },
                actions = {
                    SaveIconButton(
                        enabled = name.isNotBlank(),
                        onClick = {
                            coroutineScope.launch {
                                viewModel.saveDoctor(
                                    Doctor(
                                        id = editingId,
                                        name = name.trim(),
                                        specialty = specialty.trim().ifBlank { null },
                                        phone = phone.trim().ifBlank { null },
                                        address = address.trim().ifBlank { null },
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
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Doctor name") },
                modifier = Modifier.fillMaxWidth()
            )

            AutocompleteTextField(
                value = specialty,
                onValueChange = { specialty = it },
                label = "Specialty",
                suggestions = (commonSpecialties + uiState.doctors.mapNotNull { it.specialty }).distinct().sorted(),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Phone") },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )

            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("Address") },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                minLines = 3
            )

            if (doctorId != null) {
                // Read-only -- conditions are linked to a doctor from the
                // Condition edit screen, not from here. This is just a
                // summary so you can see at a glance what this doctor
                // manages (punch-list #7); can be more than one.
                val managedConditions = uiState.conditions
                    .filter { it.doctorId == editingId }
                    .sortedBy { it.name }

                Text(
                    "Conditions Managed",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
                )
                if (managedConditions.isEmpty()) {
                    Text(
                        "No conditions currently linked to this doctor.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            managedConditions.forEachIndexed { index, condition ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        condition.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        condition.status,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (index < managedConditions.lastIndex) {
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                }
                            }
                        }
                    }
                }
            }

            if (doctorId != null) {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            viewModel.getDoctor(editingId)?.let { viewModel.deleteDoctor(it) }
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
}
