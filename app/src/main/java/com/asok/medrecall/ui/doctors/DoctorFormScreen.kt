package com.asok.medrecall.ui.doctors

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.asok.medrecall.data.local.Doctor
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoctorFormScreen(
    doctorId: Int?,
    onDone: () -> Unit,
    viewModel: DoctorsViewModel = viewModel(factory = DoctorsViewModel.factory(LocalContext.current))
) {
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
        topBar = { CenterAlignedTopAppBar(title = { Text(if (doctorId == null) "New Doctor" else "Edit Doctor", fontWeight = FontWeight.Bold, color = Color.Black) }) }
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

            OutlinedTextField(
                value = specialty,
                onValueChange = { specialty = it },
                label = { Text("Specialty") },
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

            Row(modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
                Button(
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
                    },
                    modifier = Modifier.weight(1f),
                    enabled = name.isNotBlank()
                ) {
                    Text("Save")
                }
                if (doctorId != null) {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                viewModel.getDoctor(editingId)?.let { viewModel.deleteDoctor(it) }
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
}
