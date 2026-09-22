package com.asok.medrecall.ui.vitals

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Science
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
 * see VitalReading.kt) and, where a Health Connect data type actually
 * exists for it, Health Connect too (Android's closest equivalent to
 * Apple Health/Apple Watch -- Samsung Health and most watches sync into
 * it): healthConnectReadPermission is the exact permission string
 * HealthConnectManager requests/checks for this vital, used to pull
 * automatic readings on top of whatever's logged by hand. MedRecall only
 * ever reads from Health Connect, never writes to it.
 *
 * healthConnectReadPermission is nullable: A1C and Cholesterol (added for
 * the Health page, punch item #13) have no corresponding Health Connect
 * record type as of this writing, so they're manual-only -- see
 * VitalsViewModel.refreshHealthConnect and HealthConnectManager for how a
 * null permission is handled (no banner shown, no crash).
 *
 * Steps, Distance, and Sleep are deliberately NOT here -- those belong to
 * the separate Activity & Fitness and Sleep categories Asok defined,
 * planned for later sessions; this pass covers just his "Vitals" list
 * plus the two manual-only additions the Health page needed.
 */
enum class VitalType(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val tileColors: List<Color>,
    val healthConnectReadPermission: String?
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
    ),
    A1C(
        "a1c", "A1C", Icons.Filled.Science,
        listOf(Color(0xFFD08CE8), Color(0xFFA354BF)),
        null
    ),
    CHOLESTEROL(
        "cholesterol", "Cholesterol", Icons.Filled.Science,
        listOf(Color(0xFFE0C24D), Color(0xFFB89A2A)),
        null
    );

    companion object {
        fun fromId(id: String): VitalType = entries.firstOrNull { it.id == id } ?: BLOOD_PRESSURE
    }
}

/**
 * The 9 vitals shown on the existing Vitals grid screen (VitalsScreen.kt)
 * -- deliberately an explicit list, NOT VitalType.entries, so that adding
 * A1C/Cholesterol for the new Health page (punch item #13) doesn't also
 * silently add two more tiles to Vitals. Per Asok's call: Vitals stays
 * exactly as it is; Health is a separate screen that happens to read/write
 * the same underlying VitalReading data for the types they share.
 */
val trackedVitals = listOf(
    VitalType.BLOOD_PRESSURE,
    VitalType.WEIGHT,
    VitalType.BLOOD_GLUCOSE,
    VitalType.HEART_RATE,
    VitalType.RESTING_HEART_RATE,
    VitalType.HEART_RATE_VARIABILITY,
    VitalType.BLOOD_OXYGEN,
    VitalType.RESPIRATORY_RATE,
    VitalType.BODY_TEMPERATURE
)

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
