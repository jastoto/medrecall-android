package com.asok.medrecall.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per logged vital-sign reading. `type` stores VitalType's id
 * (e.g. "blood_pressure") as a plain string -- same flexible-string
 * pattern as Condition.status and Medication.dosage elsewhere in this app.
 *
 * The value fields are deliberately generic so one table covers every
 * vital type instead of five near-identical ones:
 *  - Blood Pressure: primaryValue = systolic, secondaryValue = diastolic,
 *    tertiaryValue = pulse (optional)
 *  - Weight: primaryValue = weight (lb)
 *  - Blood Glucose: primaryValue = reading (mg/dL), context = "Fasting" /
 *    "Before Meal" / "After Meal" / "Other"
 *  - Steps: primaryValue = step count
 *  - Sleep: primaryValue = hours slept
 *  - A1C: primaryValue = percent
 *  - Cholesterol: primaryValue = total cholesterol (mg/dL)
 *  - Custom (Health page, punch item #13): type = "custom", primaryValue =
 *    whatever number was typed, customLabel = the user-typed name for this
 *    one-off reading (e.g. "Peak Flow"), customUnit = the user-typed unit
 *    (e.g. "L/min"). Each custom entry stands alone -- there's no shared
 *    "Peak Flow" type to group future entries under, per Asok's call.
 *
 * source is reserved for a future Health Connect sync (Android's closest
 * equivalent to Apple Health/Apple Watch data) -- everything logged today
 * is "MANUAL"; an eventual sync would tag its rows "HEALTH_CONNECT"
 * instead of needing a schema change.
 */
@Entity(tableName = "vital_readings")
data class VitalReading(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String,
    val recordedAt: Long,
    val primaryValue: Double,
    val secondaryValue: Double? = null,
    val tertiaryValue: Double? = null,
    val context: String? = null,
    val notes: String? = null,
    val source: String = "MANUAL",
    val customLabel: String? = null,
    val customUnit: String? = null
)

/** type value for one-off custom readings logged from the Health page (punch item #13). */
const val CUSTOM_VITAL_TYPE = "custom"
