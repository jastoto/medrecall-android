package com.asok.medrecall.ui.conditions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The 13 icon choices offered when adding/editing a Condition. Material
 * Icons Extended has no exact stethoscope or lungs glyph, so those two
 * slots use HealthAndSafety and Air as stand-ins -- same kind of
 * substitution already used for Doctors/Medications elsewhere in this app.
 */
data class ConditionIconOption(val key: String, val icon: ImageVector)

val conditionIconOptions = listOf(
    ConditionIconOption("assignment", Icons.Default.Assignment),
    ConditionIconOption("stethoscope", Icons.Default.HealthAndSafety),
    ConditionIconOption("heart", Icons.Default.Favorite),
    ConditionIconOption("brain", Icons.Default.Psychology),
    ConditionIconOption("eye", Icons.Default.Visibility),
    ConditionIconOption("lungs", Icons.Default.Air),
    ConditionIconOption("bandage", Icons.Default.Healing),
    ConditionIconOption("medkit", Icons.Default.MedicalServices),
    ConditionIconOption("pills", Icons.Default.Medication),
    ConditionIconOption("pulse", Icons.Default.MonitorHeart),
    ConditionIconOption("drop", Icons.Default.Opacity),
    ConditionIconOption("walk", Icons.Default.DirectionsWalk),
    ConditionIconOption("person", Icons.Default.AccountCircle)
)

val defaultConditionIconKey = conditionIconOptions.first().key

fun iconForKey(key: String): ImageVector =
    conditionIconOptions.firstOrNull { it.key == key }?.icon ?: conditionIconOptions.first().icon
