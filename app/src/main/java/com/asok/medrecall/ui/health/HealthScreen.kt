package com.asok.medrecall.ui.health

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.asok.medrecall.data.local.Condition
import com.asok.medrecall.data.local.VitalReading
import com.asok.medrecall.ui.components.MedRecallTopBar
import com.asok.medrecall.ui.conditions.iconForKey
import com.asok.medrecall.ui.vitals.VitalType
import com.asok.medrecall.ui.vitals.formatNumber
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The Health page (punch item #13) -- "Log a Reading, Active Conditions,
 * Other Readings, top summary box with trends", per Asok's spec.
 * Featured trend summary covers Blood Pressure/Weight/Blood Glucose/A1C
 * (his choice); everything else (Blood Oxygen, Body Temperature,
 * Cholesterol, Heart Rate, plus any one-off Custom readings) lives under
 * Other Readings. Readings are the same VitalReading data the Vitals
 * screen reads/writes -- Vitals itself is untouched (see VitalType.kt's
 * trackedVitals comment).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthScreen(
    onGoHome: () -> Unit,
    onLogReading: (VitalType) -> Unit,
    onLogCustomReading: () -> Unit,
    onOpenReadingHistory: (VitalType) -> Unit,
    onAddCondition: () -> Unit,
    onEditCondition: (Int) -> Unit,
    onEditCustomReading: (Int) -> Unit,
    viewModel: HealthViewModel = viewModel(factory = HealthViewModel.factory(LocalContext.current))
) {
    val uiState by viewModel.uiState.collectAsState()
    var showReadingPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            MedRecallTopBar(
                title = "Health",
                onGoHome = onGoHome,
                actions = {
                    IconButton(onClick = { showReadingPicker = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Log a reading")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                LogReadingButton(onClick = { showReadingPicker = true })
            }
            item {
                TrendSummarySection(uiState.featured, onLogReading, onOpenReadingHistory)
            }
            item {
                ActiveConditionsSection(uiState.activeConditions, onAddCondition, onEditCondition)
            }
            item {
                OtherReadingsSection(
                    otherTypes = uiState.otherTypes,
                    customReadings = uiState.customReadings,
                    onOpenReadingHistory = onOpenReadingHistory,
                    onEditCustomReading = onEditCustomReading
                )
            }
        }
    }

    if (showReadingPicker) {
        ReadingTypePickerDialog(
            onDismiss = { showReadingPicker = false },
            onSelect = { item ->
                showReadingPicker = false
                when (item) {
                    is HealthReadingPickerItem.Standard -> onLogReading(item.vitalType)
                    HealthReadingPickerItem.Custom -> onLogCustomReading()
                }
            }
        )
    }
}

@Composable
private fun ReadingTypePickerDialog(onDismiss: () -> Unit, onSelect: (HealthReadingPickerItem) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log a Reading") },
        text = {
            Column {
                healthReadingPickerItems.forEachIndexed { index, item ->
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(item) }
                            .padding(vertical = 12.dp)
                    )
                    if (index < healthReadingPickerItems.lastIndex) {
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun LogReadingButton(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Text(
                "Log a Reading",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 10.dp)
            )
        }
    }
}

/** Orange/red flag color for an out-of-range featured reading -- see isOutOfRange(). */
private val HealthWarningColor = Color(0xFFE0603D)

@Composable
private fun TrendSummarySection(
    featured: List<FeaturedTrend>,
    onLogReading: (VitalType) -> Unit,
    onOpenReadingHistory: (VitalType) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader("At a Glance")
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                featured.chunked(2).forEachIndexed { rowIndex, row ->
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                        row.forEach { trend ->
                            GlanceColumn(
                                trend = trend,
                                onClick = {
                                    if (trend.readings.isEmpty()) onLogReading(trend.vitalType) else onOpenReadingHistory(trend.vitalType)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // Odd item count (e.g. a 3rd/4th featured type added later) -- keep columns evenly
                        // spaced instead of the last one stretching to fill the row.
                        if (row.size < 2) Box(modifier = Modifier.weight(1f))
                    }
                    if (rowIndex < featured.chunked(2).lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun GlanceColumn(trend: FeaturedTrend, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.clickable(onClick = onClick)) {
        Text(
            glanceLabel(trend.vitalType),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (trend.readings.isEmpty()) {
            Text(
                "No data yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        } else {
            val latest = trend.readings.first()
            Text(
                formatHealthValue(trend.vitalType, latest),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (isOutOfRange(trend.vitalType, latest)) HealthWarningColor else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 2.dp)
            )
            trendLine(trend.vitalType, trend.readings)?.let { line ->
                Text(
                    line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

/** "Blood Glucose" reads as just "Glucose" in the compact glance strip -- everywhere else (picker, Other Readings, history) keeps the full VitalType.title. */
private fun glanceLabel(type: VitalType): String = if (type == VitalType.BLOOD_GLUCOSE) "Glucose" else type.title

/** No single delta reads cleanly for a two-number Blood Pressure reading, so it gets no trend line -- matches Asok's reference screenshot. */
private fun trendLine(type: VitalType, readings: List<VitalReading>): String? {
    if (type == VitalType.BLOOD_PRESSURE || readings.size < 2) return null
    val delta = readings[0].primaryValue - readings[1].primaryValue
    return when {
        delta > 0 -> "↗ +${formatNumber(delta)}"
        delta < 0 -> "↘ -${formatNumber(-delta)}"
        else -> "→ No change"
    }
}

/**
 * Lightweight, generic thresholds for flagging a featured reading orange
 * on the "At a Glance" strip (punch item #13, per Asok's call to flag
 * out-of-range values) -- commonly-cited general-population cutoffs (AHA
 * for blood pressure, ADA for glucose/A1C), NOT personalized or
 * doctor-verified. Weight has no single-value "abnormal" threshold
 * without a target weight the app doesn't collect, so it's never flagged.
 */
private fun isOutOfRange(type: VitalType, reading: VitalReading): Boolean = when (type) {
    VitalType.BLOOD_PRESSURE -> reading.primaryValue >= 130 || (reading.secondaryValue ?: 0.0) >= 80
    VitalType.BLOOD_GLUCOSE -> reading.primaryValue >= 100
    VitalType.A1C -> reading.primaryValue >= 5.7
    else -> false
}

@Composable
private fun ActiveConditionsSection(
    conditions: List<Condition>,
    onAddCondition: () -> Unit,
    onEditCondition: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader("Active Conditions")
        if (conditions.isEmpty()) {
            EmptyStateCard("No conditions yet.", "Add one", onAddCondition)
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    conditions.forEachIndexed { index, condition ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEditCondition(condition.id) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                iconForKey(condition.iconKey),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                condition.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                        if (index < conditions.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OtherReadingsSection(
    otherTypes: List<OtherTypeSummary>,
    customReadings: List<VitalReading>,
    onOpenReadingHistory: (VitalType) -> Unit,
    onEditCustomReading: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader("Other Readings")
        Card(modifier = Modifier.fillMaxWidth()) {
            Column {
                val rows = otherTypes.size + customReadings.size
                var rowIndex = 0
                otherTypes.forEach { summary ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenReadingHistory(summary.vitalType) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            summary.vitalType.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(summary.vitalType.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                summary.latest?.let { formatHealthValue(summary.vitalType, it) } ?: "No readings yet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    rowIndex++
                    if (rowIndex < rows) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
                customReadings.forEach { reading ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEditCustomReading(reading.id) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(reading.customLabel ?: "Custom", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${formatNumber(reading.primaryValue)}${reading.customUnit?.let { " $it" } ?: ""} · ${formatDate(reading.recordedAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    rowIndex++
                    if (rowIndex < rows) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
                if (rows == 0) {
                    Text(
                        "No other readings yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun EmptyStateCard(message: String, actionLabel: String, onAction: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

internal fun formatHealthValue(type: VitalType, reading: VitalReading): String = when (type) {
    VitalType.BLOOD_PRESSURE -> "${reading.primaryValue.toInt()}/${reading.secondaryValue?.toInt() ?: 0} mmHg"
    VitalType.WEIGHT -> "${formatNumber(reading.primaryValue)} lb"
    VitalType.BLOOD_GLUCOSE -> "${reading.primaryValue.toInt()} mg/dL"
    VitalType.A1C -> "${formatNumber(reading.primaryValue)}%"
    VitalType.CHOLESTEROL -> "${reading.primaryValue.toInt()} mg/dL"
    VitalType.HEART_RATE -> "${reading.primaryValue.toInt()} bpm"
    VitalType.BLOOD_OXYGEN -> "${reading.primaryValue.toInt()}%"
    VitalType.BODY_TEMPERATURE -> "${formatNumber(reading.primaryValue)}°F"
    else -> formatNumber(reading.primaryValue)
}

private fun formatDate(epochMillis: Long): String {
    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
    return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(formatter)
}
