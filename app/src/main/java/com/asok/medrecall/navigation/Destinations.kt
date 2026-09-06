package com.asok.medrecall.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Destination(val route: String, val label: String, val icon: ImageVector) {
    data object Home : Destination("home", "Home", Icons.Default.Home)
    data object AskMedRecall : Destination("ask_medrecall", "Ask MedRecall", Icons.Default.Chat)
    data object HelpHowTo : Destination("help_how_to", "Help & How-To", Icons.Default.HelpOutline)
    data object RecurringReminders : Destination("recurring_reminders", "Recurring Reminders", Icons.Default.Notifications)
    data object Settings : Destination("settings", "Settings", Icons.Default.Settings)

    data object RecordVisit : Destination("record_visit", "Record Visit", Icons.Default.EditNote)
    data object Doctors : Destination("doctors", "Doctors", Icons.Default.LocalHospital)
    data object Medications : Destination("medications", "Medications", Icons.Default.Medication)
    data object Vitals : Destination("vitals", "Vitals", Icons.Default.MonitorHeart)
    data object Health : Destination("health", "Health", Icons.Default.Favorite)
    data object Conditions : Destination("conditions", "Conditions", Icons.Default.Warning)
    data object Reports : Destination("reports", "Reports", Icons.Default.Description)
    data object MedicalId : Destination("medical_id", "Medical ID", Icons.Default.Shield)
    data object Calendar : Destination("calendar", "Calendar", Icons.Default.CalendarMonth)
}

val bottomNavDestinations = listOf(
    Destination.Home, Destination.AskMedRecall, Destination.HelpHowTo,
    Destination.RecurringReminders, Destination.Settings
)

val homeGridDestinations = listOf(
    Destination.RecordVisit, Destination.Doctors, Destination.Medications,
    Destination.Vitals, Destination.Health, Destination.Conditions,
    Destination.Reports, Destination.MedicalId, Destination.Calendar
)
