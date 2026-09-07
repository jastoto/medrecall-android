package com.asok.medrecall.ui.vitals

import com.asok.medrecall.data.local.VitalReading

/**
 * Shared shape for rendering a reading regardless of where it came from --
 * a manual Room row (id set, source == "MANUAL", editable) or a live
 * Health Connect record (id null, source == "HEALTH_CONNECT", read-only --
 * MedRecall never writes back to Health Connect). VitalDetailScreen merges
 * both lists into one for display.
 */
data class VitalReadingDisplay(
    val id: Int? = null,
    val recordedAt: Long,
    val primaryValue: Double,
    val secondaryValue: Double? = null,
    val tertiaryValue: Double? = null,
    val context: String? = null,
    val notes: String? = null,
    val source: String
)

fun VitalReading.toDisplay(): VitalReadingDisplay = VitalReadingDisplay(
    id = id,
    recordedAt = recordedAt,
    primaryValue = primaryValue,
    secondaryValue = secondaryValue,
    tertiaryValue = tertiaryValue,
    context = context,
    notes = notes,
    source = source
)
