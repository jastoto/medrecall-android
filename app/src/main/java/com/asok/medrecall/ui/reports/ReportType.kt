package com.asok.medrecall.ui.reports

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * One entry per tile on the Reports grid. `id` doubles as the nav-route
 * argument (report_detail/{id}), so keep these stable once shipped.
 */
enum class ReportType(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val tileColors: List<Color>,
    /** Shown on the report's detail screen before it's generated. */
    val description: String
) {
    HISTORY(
        "history", "History", Icons.Default.Schedule,
        listOf(Color(0xFF9AA3AE), Color(0xFF5C6672)),
        "A chronological record of your appointments and notes."
    ),
    HEALTH_SUMMARY(
        "health_summary", "Health Summary", Icons.Default.MedicalServices,
        listOf(Color(0xFF4A72A8), Color(0xFF2C4F80)),
        "An overview of your profile, active conditions, and current medications."
    ),
    CONDITION_REPORT(
        "condition_report", "Condition Report", Icons.Default.FindInPage,
        listOf(Color(0xFFA477D6), Color(0xFF7C4FB5)),
        "All tracked conditions, grouped by status."
    ),
    DOCTORS_REPORT(
        "doctors_report", "Doctors Report", Icons.Default.MedicalServices,
        listOf(Color(0xFF3E5C7C), Color(0xFF1D2E42)),
        "Contact information for every doctor on file."
    ),
    MEDICATIONS_REPORT(
        "medications_report", "Medications Report", Icons.Default.Medication,
        listOf(Color(0xFFFFAA9E), Color(0xFFF07A69)),
        "All medications, grouped by prescribing doctor."
    ),
    VITALS_REPORT(
        "vitals_report", "Vitals Report", Icons.Default.MonitorHeart,
        listOf(Color(0xFF92CD7C), Color(0xFF6BA857)),
        "Vitals tracking isn't set up yet, so this report will be empty for now."
    ),
    MEDICARE_CLAIMS(
        "medicare_claims", "Medicare Claims", Icons.Default.CreditCard,
        listOf(Color(0xFF4C8C6B), Color(0xFF2E6B4B)),
        "Claims tracking isn't set up yet, so this report will be empty for now."
    );

    companion object {
        fun fromId(id: String): ReportType = entries.first { it.id == id }
    }
}

val reportsGrid = ReportType.entries.toList()
