package com.asok.medrecall.ui.medicalid

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.asok.medrecall.data.local.Medication
import com.asok.medrecall.navigation.Destination
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.asok.medrecall.ui.components.MedRecallTopBar

/**
 * Styled as a physical "Medical ID" wallet card, matching the red gradient
 * card from Asok's iOS reference screenshot: MedicalId's own home-tile
 * gradient (see Destinations.kt) painted behind white text, grouped into
 * the same four sections (Personal Information / Conditions / Allergies /
 * Emergency Contact) plus a MEDRECALL+ footer wordmark. Uses the app's
 * default Material typography/fonts throughout -- same as every other
 * screen -- rather than a new font family.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicalIdScreen(
    onEdit: () -> Unit,
    onGoHome: () -> Unit,
    viewModel: MedicalIdViewModel = viewModel(factory = MedicalIdViewModel.factory(LocalContext.current))
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            MedRecallTopBar(
                title = "Medical ID",
                onGoHome = onGoHome,
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Medical ID")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            MedicalIdCard(uiState = uiState)
            Spacer(modifier = Modifier.height(20.dp))
            CurrentMedicationsCard(medications = uiState.medications)
        }
    }
}

@Composable
private fun CurrentMedicationsCard(medications: List<Medication>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Current Medications",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (medications.isEmpty()) {
                Text(
                    text = "No active medications on file",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                medications.forEachIndexed { index, medication ->
                    val details = listOfNotNull(medication.dosage, medication.schedule).joinToString(" \u00b7 ")
                    val line = buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(medication.name)
                        }
                        if (details.isNotBlank()) {
                            append("  \u2014 ")
                            append(details)
                        }
                    }
                    Text(
                        text = line,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                    if (index < medications.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MedicalIdCard(uiState: MedicalIdUiState) {
    val patient = uiState.patient

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = Color.Black.copy(alpha = 0.30f),
                spotColor = Color.Black.copy(alpha = 0.40f)
            )
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(Destination.MedicalId.tileColors))
            .padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.size(10.dp))
            Text(
                text = "MEDICAL ID",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                letterSpacing = 1.sp
            )
        }

        CardDivider()

        SectionLabel("Personal Information")
        Text(
            text = patient?.name?.ifBlank { "Add your name" } ?: "Add your name",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp
        )
        patient?.addressLine?.takeIf { it.isNotBlank() }?.let {
            Text(text = it, color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp))
        }
        val cityStateZip = listOfNotNull(
            patient?.city?.takeIf { it.isNotBlank() },
            listOfNotNull(patient?.state?.takeIf { it.isNotBlank() }, patient?.zip?.takeIf { it.isNotBlank() })
                .joinToString(" ")
                .ifBlank { null }
        ).joinToString(", ")
        if (cityStateZip.isNotBlank()) {
            Text(text = cityStateZip, color = Color.White, fontSize = 16.sp)
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "DOB: ${formatDob(patient?.dateOfBirth)}",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.padding(end = 24.dp)
            )
            Text(
                text = "Blood Type: ${patient?.bloodType?.takeIf { it.isNotBlank() } ?: "Not set"}",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }

        CardDivider()

        SectionLabel("Conditions")
        val medicalIdConditions = uiState.medicalIdConditions
        if (medicalIdConditions.isEmpty()) {
            Text(text = "None on file", color = Color.White, fontSize = 16.sp)
        } else {
            medicalIdConditions.forEach { condition ->
                Text(
                    text = condition.name,
                    color = Color.White,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        CardDivider()

        SectionLabel("Allergies")
        Text(
            text = patient?.allergies?.takeIf { it.isNotBlank() } ?: "None on file",
            color = Color.White,
            fontSize = 16.sp
        )

        CardDivider()

        SectionLabel("Emergency Contact")
        val contactName = patient?.emergencyContactName?.takeIf { it.isNotBlank() }
        if (contactName != null) {
            val relationship = patient?.emergencyContactRelationship?.takeIf { it.isNotBlank() }
            Text(
                text = if (relationship != null) "$contactName — $relationship" else contactName,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
            patient?.emergencyContactPhone?.takeIf { it.isNotBlank() }?.let {
                Text(text = it, color = Color.White, fontSize = 16.sp)
            }
        } else {
            Text(text = "Not set", color = Color.White, fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "MEDRECALL+",
            color = Color.White.copy(alpha = 0.65f),
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = Color.White.copy(alpha = 0.75f),
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun CardDivider() {
    HorizontalDivider(
        color = Color.White.copy(alpha = 0.25f),
        modifier = Modifier.padding(vertical = 16.dp)
    )
}

private fun formatDob(epochMillis: Long?): String {
    if (epochMillis == null) return "Not set"
    val formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy")
    return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(formatter)
}
