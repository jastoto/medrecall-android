package com.asok.medrecall.ui.vitals

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Waves
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.WeightRecord

/**
 * One entry per vital MedRecall tracks. `id` doubles as the nav-route
 * argument (vital_detail/{id}, vital_form/{id}[/{readingId}]), so keep
 * these stable once shipped.
 *
 * Every vital here supports BOTH manual entry (this app's own Room table,
 * see VitalReading.kt) and Health Connect (Android's closest equivalent
 * to Apple Health/Apple Watch -- Samsung Health and most watches sync
 * into it): healthConnectReadPermission is the exact permission string
 * HealthConnectManager requests/checks for this vital, used to pull
 * automatic readings on top of whatever's logged by hand. MedRecall only
 * ever reads from Health Connect, never writes to it.
 *
 * Steps, Distance, and Sleep are deliberately NOT here -- those belong to
 * the separate Activity & Fitness and Sleep categories Asok defined,
 * planned for later sessions; this pass covers just his "Vitals" list.
 */
enum class VitalType(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val tileColors: List<Color>,
    val healthConnectReadPermission: String
) {
    BLOOD_PRESSURE(
        "blood_pressure", "Blood Pressure", Icons.Filled.MonitorHeart,
        listOf(Color(0xFFE8756B), Color(0xFFC94A3F)),
        HealthPermission.getReadPermission(BloodPressureRecord::class)
    ),
    WEIGHT(
        "weight", "Weight", Icons.Filled.MonitorWeight,
        listOf(Color(0xFF6FB8E0), Color(0xFF3E86B8)),
        HealthPermission.getReadPermission(WeightRecord::class)
    ),
    BLOOD_GLUCOSE(
        "blood_glucose", "Blood Glucose", Icons.Filled.Bloodtype,
        listOf(Color(0xFFF0A85A), Color(0xFFD87F2E)),
        HealthPermission.getReadPermission(BloodGlucoseRecord::class)
    ),
    HEART_RATE(
        "heart_rate", "Heart Rate", Icons.Filled.Favorite,
        listOf(Color(0xFFEF6C5C), Color(0xFFC0392B)),
        HealthPermission.getReadPermission(HeartRateRecord::class)
    ),
    RESTING_HEART_RATE(
        "resting_heart_rate", "Resting Heart Rate", Icons.Filled.FavoriteBorder,
        listOf(Color(0xFFF29AC2), Color(0xFFD35F97)),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class)
    ),
    HEART_RATE_VARIABILITY(
        "heart_rate_variability", "Heart Rate Variability", Icons.Filled.Timeline,
        listOf(Color(0xFF9B8CF0), Color(0xFF6C58C9)),
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class)
    ),
    BLOOD_OXYGEN(
        "blood_oxygen", "Blood Oxygen", Icons.Filled.Air,
        listOf(Color(0xFF5BC8E0), Color(0xFF2E93AA)),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class)
    ),
    RESPIRATORY_RATE(
        "respiratory_rate", "Respiratory Rate", Icons.Filled.Waves,
        listOf(Color(0xFF7FA8E8), Color(0xFF4A78C4)),
        HealthPermission.getReadPermission(RespiratoryRateRecord::class)
    ),
    BODY_TEMPERATURE(
        "body_temperature", "Body Temperature", Icons.Filled.Thermostat,
        listOf(Color(0xFFF2B84B), Color(0xFFD3922A)),
        HealthPermission.getReadPermission(BodyTemperatureRecord::class)
    );

    companion object {
        fun fromId(id: String): VitalType = entries.firstOrNull { it.id == id } ?: BLOOD_PRESSURE
    }
}

val trackedVitals = VitalType.entries.toList()

/**
 * What's left of iOS's "FOR USE WITH A WATCH" row after Blood Oxygen, HRV,
 * Resting Heart Rate, and Respiratory Rate graduated into real
 * Health-Connect-backed vitals above. ECG and Falls stay a grayed-out
 * preview -- neither is a standard Health Connect data type MedRecall can
 * read, so there's nothing to wire up yet. Icon substitution: Warning
 * stands in for a fall alert (Material Icons Extended has no dedicated
 * fall-detection glyph), same substitution pattern used elsewhere in
 * this app.
 */
data class WatchVital(val title: String, val icon: ImageVector)

val watchOnlyVitals = listOf(
    WatchVital("Electrocardiogram", Icons.Filled.MonitorHeart),
    WatchVital("Falls", Icons.Filled.Warning)
)
