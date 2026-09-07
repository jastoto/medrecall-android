package com.asok.medrecall.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * tileColors: the light-to-dark gradient used to paint this destination's
 * "3D raised button" tile (see ui/components/RaisedIconTile.kt). Only the
 * home-grid destinations set a specific gradient; others keep the default.
 */
sealed class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val tileColors: List<Color> = listOf(Color(0xFF6650A4), Color(0xFF4A3880))
) {
    data object Home : Destination("home", "Home", Icons.Default.Home)
    data object AskMedRecall : Destination("ask_medrecall", "Ask MedRecall", Icons.Default.Chat)
    data object HelpHowTo : Destination("help_how_to", "Help & How-To", Icons.Default.HelpOutline)
    data object RecurringReminders : Destination("recurring_reminders", "Recurring Reminders", Icons.Default.Notifications)
    data object Settings : Destination("settings", "Settings", Icons.Default.Settings)

    data object RecordVisit : Destination(
        "record_visit", "Record Visit", Icons.Default.Mic,
        tileColors = listOf(Color(0xFFFF8A75), Color(0xFFE8503D))
    )
    data object Doctors : Destination(
        "doctors", "Doctors", Icons.Default.MedicalServices,
        tileColors = listOf(Color(0xFF3E5C7C), Color(0xFF1D2E42))
    )
    data object Medications : Destination(
        "medications", "Medications", Icons.Default.Medication,
        tileColors = listOf(Color(0xFFFFAA9E), Color(0xFFF07A69))
    )
    data object Vitals : Destination(
        "vitals", "Vitals", Icons.Default.MonitorHeart,
        tileColors = listOf(Color(0xFF92CD7C), Color(0xFF6BA857))
    )
    data object Health : Destination(
        "health", "Health", Icons.Default.Favorite,
        tileColors = listOf(Color(0xFF2C4F80), Color(0xFF17294A))
    )
    data object Conditions : Destination(
        "conditions", "Conditions", Icons.Default.Assignment,
        tileColors = listOf(Color(0xFF7B80EA), Color(0xFF5457C9))
    )
    data object Reports : Destination(
        "reports", "Reports", Icons.Default.Description,
        tileColors = listOf(Color(0xFFA477D6), Color(0xFF7C4FB5))
    )
    data object MedicalId : Destination(
        "medical_id", "Medical ID", Icons.Default.Shield,
        tileColors = listOf(Color(0xFFE24D62), Color(0xFFC42A44))
    )
    data object Calendar : Destination(
        "calendar", "Calendar", Icons.Default.CalendarMonth,
        tileColors = listOf(Color(0xFF3FADA3), Color(0xFF2C7E76))
    )
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
