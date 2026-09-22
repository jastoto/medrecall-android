package com.asok.medrecall.ui.health

import com.asok.medrecall.ui.vitals.VitalType

/**
 * The reading types offered on the Health page (punch item #13) -- a
 * curated subset of VitalType, deliberately narrower than the Vitals
 * screen's full 9-type list (no Resting HR/HRV/Respiratory Rate here;
 * those stay Vitals-only, per Asok's call to leave Vitals as-is).
 */
val healthPageVitalTypes = listOf(
    VitalType.A1C,
    VitalType.BLOOD_GLUCOSE,
    VitalType.BLOOD_OXYGEN,
    VitalType.BLOOD_PRESSURE,
    VitalType.BODY_TEMPERATURE,
    VitalType.CHOLESTEROL,
    VitalType.HEART_RATE,
    VitalType.WEIGHT
)

/** The 4 types featured in the top trend summary box (arrow + sparkline), per Asok's call. */
val healthPageFeaturedTypes = listOf(
    VitalType.BLOOD_PRESSURE,
    VitalType.WEIGHT,
    VitalType.BLOOD_GLUCOSE,
    VitalType.A1C
)

/** Everything else -- shown as a plain latest-value row under "Other Readings". */
val healthPageOtherTypes = healthPageVitalTypes - healthPageFeaturedTypes.toSet()

/** One item in the "Log a Reading" type picker -- either a real VitalType or the one-off Custom option. */
sealed class HealthReadingPickerItem(val title: String) {
    data class Standard(val vitalType: VitalType) : HealthReadingPickerItem(vitalType.title)
    data object Custom : HealthReadingPickerItem("Custom")
}

/** Alphabetical, per Asok's request -- "A1C, Blood Glucose, ... Cholesterol, Custom, Heart Rate, Weight". */
val healthReadingPickerItems: List<HealthReadingPickerItem> =
    (healthPageVitalTypes.map { HealthReadingPickerItem.Standard(it) } + HealthReadingPickerItem.Custom)
        .sortedBy { it.title }
