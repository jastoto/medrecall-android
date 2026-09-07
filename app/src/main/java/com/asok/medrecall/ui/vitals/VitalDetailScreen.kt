package com.asok.medrecall.ui.vitals

import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.asok.medrecall.ui.components.MedRecallTopBar

/**
 * Lists readings for one vital, merging manual Room entries with whatever
 * Health Connect supplies (see VitalsViewModel) into one time-sorted list.
 * Only manual rows (id != null) are editable -- MedRecall never writes
 * back to Health Connect, so a tap on a "Watch" row does nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VitalDetailScreen(
    vitalType: VitalType,
    onAddReading: () -> Unit,
    onEditReading: (Int) -> Unit,
    onGoHome: () -> Unit,
    viewModel: VitalsViewModel = viewModel(factory = VitalsViewModel.factory(LocalContext.current, vitalType.id))
) {
    val uiState by viewModel.uiState.collectAsState()
    val healthConnectStatus by viewModel.healthConnectStatus.collectAsState()
    val healthConnectReadings by viewModel.healthConnectReadings.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { viewModel.refreshHealthConnect(vitalType) }

    LaunchedEffect(vitalType) { viewModel.refreshHealthConnect(vitalType) }

    val combinedReadings = (uiState.readings.map { it.toDisplay() } + healthConnectReadings)
        .sortedByDescending { it.recordedAt }

    Scaffold(
        topBar = {
            MedRecallTopBar(
                title = vitalType.title,
                onGoHome = onGoHome,
                actions = {
                    IconButton(onClick = onAddReading) {
                        Icon(Icons.Default.Add, contentDescription = "Log a reading")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (healthConnectStatus == HealthConnectStatus.NEEDS_PERMISSION) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Connect Health Connect to see automatic readings here",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = { permissionLauncher.launch(setOf(vitalType.healthConnectReadPermission)) }) {
                        Text("Connect")
                    }
                }
            }

            if (combinedReadings.isEmpty() && !uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No ${vitalType.title.lowercase()} readings yet. Tap + to log one.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(combinedReadings, key = { it.id ?: -it.recordedAt }) { reading ->
                        if (reading.id != null) {
                            Card(onClick = { onEditReading(reading.id) }, modifier = Modifier.fillMaxWidth()) {
                                VitalReadingCardContent(vitalType, reading)
                            }
                        } else {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                VitalReadingCardContent(vitalType, reading)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VitalReadingCardContent(vitalType: VitalType, reading: VitalReadingDisplay) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(formatReadingValue(vitalType, reading), style = MaterialTheme.typography.titleMedium)
            SourceTag(reading.source)
        }
        Text(
            formatDateTime(reading.recordedAt),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        reading.context?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        }
        reading.notes?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun SourceTag(source: String) {
    val label = if (source == "HEALTH_CONNECT") "Watch" else "Manual"
    val tint = if (source == "HEALTH_CONNECT") Color(0xFF3E86B8) else MaterialTheme.colorScheme.onSurfaceVariant
    Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
}

private fun formatReadingValue(type: VitalType, reading: VitalReadingDisplay): String = when (type) {
    VitalType.BLOOD_PRESSURE -> {
        val systolic = reading.primaryValue.toInt()
        val diastolic = reading.secondaryValue?.toInt() ?: 0
        "$systolic/$diastolic mmHg"
    }
    VitalType.WEIGHT -> "${formatNumber(reading.primaryValue)} lb"
    VitalType.BLOOD_GLUCOSE -> "${reading.primaryValue.toInt()} mg/dL"
    VitalType.HEART_RATE -> {
        val avg = reading.primaryValue.toInt()
        val rangeSuffix = if (reading.secondaryValue != null && reading.tertiaryValue != null) {
            " (${reading.secondaryValue.toInt()}–${reading.tertiaryValue.toInt()})"
        } else ""
        "$avg bpm$rangeSuffix"
    }
    VitalType.RESTING_HEART_RATE -> "${reading.primaryValue.toInt()} bpm"
    VitalType.HEART_RATE_VARIABILITY -> "${formatNumber(reading.primaryValue)} ms"
    VitalType.BLOOD_OXYGEN -> "${reading.primaryValue.toInt()}%"
    VitalType.RESPIRATORY_RATE -> "${formatNumber(reading.primaryValue)} breaths/min"
    VitalType.BODY_TEMPERATURE -> "${formatNumber(reading.primaryValue)}°F"
}

internal fun formatNumber(value: Double): String =
    if (value == Math.floor(value)) value.toLong().toString() else String.format("%.1f", value)

private fun formatDateTime(epochMillis: Long): String {
    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a")
    return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(formatter)
}
