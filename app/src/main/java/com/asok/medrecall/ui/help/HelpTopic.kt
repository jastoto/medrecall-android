package com.asok.medrecall.ui.help

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.asok.medrecall.navigation.Destination
import com.asok.medrecall.ui.vitals.VitalType
import com.asok.medrecall.ui.vitals.watchOnlyVitals

/**
 * One entry per row on the Help & How-To screen (see [HelpScreen] /
 * [HelpDetailScreen]). `id` doubles as the nav-route argument
 * (help_detail/{id}), so keep these stable once shipped.
 *
 * comingSoonNote is non-null for anything that already has a real screen
 * in the app but isn't fully wired up yet (matches the "Coming Soon"
 * rows already in Settings/Account) -- the detail screen shows it as a
 * callout instead of pretending the feature is finished.
 *
 * Icons/colors are pulled straight from [Destination] and [VitalType]
 * wherever a topic matches an existing home-grid or vitals entry, so
 * Help never drifts out of sync with the rest of the app's look.
 */
data class HelpTopic(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val tint: Color,
    val summary: String,
    val setupSteps: List<String> = emptyList(),
    val useSteps: List<String> = emptyList(),
    val comingSoonNote: String? = null
)

fun helpTopicFromId(id: String): HelpTopic = helpTopics.firstOrNull { it.id == id } ?: helpTopics.first()

private fun vitalSetupSteps(title: String) = listOf(
    "From Home, tap Vitals, then tap \"$title.\"",
    "To have readings fill in automatically from your watch or fitness app, grant Health Connect access for $title -- either from the all-at-once banner on the main Vitals screen, or the \"Connect\" button on this vital's own screen (see the Health Connect topic for the full setup)."
)

private fun vitalUseSteps(title: String) = listOf(
    "Tap the + button any time to log a $title reading yourself.",
    "Readings pulled in from Health Connect show up automatically and are tagged \"Watch\"; anything you type in yourself is tagged \"Manual.\"",
    "Only Manual readings can be edited or deleted -- tap one to change or remove it.",
    "All readings for this vital are listed together on one screen, most recent first."
)

private fun vitalTopic(type: VitalType, summary: String, displayTitle: String = type.title): HelpTopic = HelpTopic(
    id = type.id,
    title = displayTitle,
    icon = type.icon,
    tint = type.tileColors.first(),
    summary = summary,
    setupSteps = vitalSetupSteps(displayTitle),
    useSteps = vitalUseSteps(displayTitle)
)

val helpTopics: List<HelpTopic> = buildList {
    add(
        HelpTopic(
            id = "ask_medrecall",
            title = Destination.AskMedRecall.label,
            icon = Destination.AskMedRecall.icon,
            tint = Destination.AskMedRecall.tileColors.first(),
            summary = "Search everything you've saved -- doctors, meds, conditions, vitals, appointments, notes, and your Medical ID -- from one box.",
            setupSteps = listOf("Nothing to set up -- it searches whatever you've already entered elsewhere in the app."),
            useSteps = listOf(
                "Tap \"Ask MedRecall\" in the bottom bar.",
                "Type a name, condition, medication, or term -- a doctor's name, \"insulin,\" or \"Dr.\" all work.",
                "Tap the search icon, or your keyboard's search button, to run it -- results don't appear as you type.",
                "Results are grouped by category (Doctors, Medications, Conditions, Vitals, Appointments, Visit Notes, Medical ID), each with a matching icon and color.",
                "Tap a result to jump straight to that record. A Visit Note opens in a quick read-only popup instead, since notes don't have their own screen yet.",
                "No matches shows a message -- try a shorter or different term."
            )
        )
    )
    add(
        HelpTopic(
            id = "backup_restore",
            title = "Backup & Restore",
            icon = Icons.Default.Backup,
            tint = Color(0xFF8A94A6),
            summary = "Choose where backups go -- Google Drive, OneDrive, or files on this phone -- and back up or restore your data.",
            setupSteps = listOf(
                "Open Settings from the bottom bar, then tap \"Backup & Restore.\"",
                "If you plan to use Google Drive or OneDrive, sign in first from Settings > Account > Cloud Storage Accounts."
            ),
            useSteps = listOf(
                "On the Backup & Restore screen, pick a destination -- Google Drive, OneDrive, or Files on This Phone.",
                "Tap \"Back Up Now\" to save a copy, or \"Restore\" to bring one back."
            ),
            comingSoonNote = "Backup & Restore is fully navigable, but \"Back Up Now\" and \"Restore\" currently just explain what they'll do -- no cloud accounts are wired up yet for any of the three destinations."
        )
    )
    add(vitalTopic(VitalType.BLOOD_GLUCOSE, "Track glucose readings and their context (before/after a meal, fasting, etc.), logged by hand or pulled in from Health Connect."))
    add(vitalTopic(VitalType.BLOOD_OXYGEN, "See SpO2 readings from your watch, or log one yourself.", displayTitle = "Blood Oxygen (SpO2)"))
    add(vitalTopic(VitalType.BLOOD_PRESSURE, "Chart systolic/diastolic readings over time, logged by hand or pulled in from Health Connect."))
    add(vitalTopic(VitalType.BODY_TEMPERATURE, "Track your body temperature, logged by hand or pulled in from Health Connect."))
    add(
        HelpTopic(
            id = "calendar_appointments",
            title = "Calendar Appointments & Follow-ups",
            icon = Destination.Calendar.icon,
            tint = Destination.Calendar.tileColors.first(),
            summary = "Schedule and track doctor appointments and follow-ups.",
            setupSteps = listOf("Nothing required, though adding your doctors first (see the Doctors topic) makes scheduling faster -- you'll pick them from a list instead of typing a name every time."),
            useSteps = listOf(
                "Tap Calendar from the Home grid.",
                "Tap + to add an appointment.",
                "Fill in the date, time, and doctor -- pick from your saved Doctors, or add a new one without leaving the form.",
                "Save it -- it appears in your appointments list.",
                "Tap an existing appointment to edit its details or reschedule it.",
                "Open a doctor's own page from the Doctors screen to see every appointment tied to them."
            )
        )
    )
    add(
        HelpTopic(
            id = "conditions",
            title = Destination.Conditions.label,
            icon = Destination.Conditions.icon,
            tint = Destination.Conditions.tileColors.first(),
            summary = "Keep a running list of your health conditions, grouped by status.",
            useSteps = listOf(
                "Tap Conditions from the Home grid.",
                "Tap + to add one -- give it a name, pick an icon, and set its status: Active, Monitoring, or Resolved.",
                "Conditions are grouped on screen by that status.",
                "Tap a condition any time to edit it or change its status as things change.",
                "Conditions you've saved here are selectable on your Medical ID card too."
            )
        )
    )
    add(
        HelpTopic(
            id = "doctors",
            title = Destination.Doctors.label,
            icon = Destination.Doctors.icon,
            tint = Destination.Doctors.tileColors.first(),
            summary = "Keep contact info for every doctor you see, and see their appointments in one place.",
            useSteps = listOf(
                "Tap Doctors from the Home grid.",
                "Tap + to add one -- name, specialty, phone number, address, and so on.",
                "Tap a doctor to view or edit their info, and see appointments linked to them.",
                "Any doctor you add here becomes pickable when scheduling an appointment, adding a medication's prescriber, or recording a visit."
            )
        )
    )
    val ecgIcon = watchOnlyVitals.first { it.title == "Electrocardiogram" }.icon
    val fallsIcon = watchOnlyVitals.first { it.title == "Falls" }.icon
    add(
        HelpTopic(
            id = "ecg",
            title = "Electrocardiogram (ECG)",
            icon = ecgIcon,
            tint = Color(0xFF6BA857),
            summary = "ECG readings come from your watch's own ECG app -- Health Connect doesn't yet support sharing ECG waveforms with other apps.",
            setupSteps = listOf("Take an ECG using your watch's own app (Samsung Health, or whatever ECG app your watch ships with)."),
            useSteps = listOf("Check the reading in your watch's own health app for now. MedRecall+ shows Electrocardiogram as a watch-only placeholder on the Vitals screen until Health Connect can pass ECG data to other apps."),
            comingSoonNote = "ECG tracking inside MedRecall+ isn't available yet -- it's a platform limitation (Health Connect has no ECG data type), not something to configure here."
        )
    )
    add(
        HelpTopic(
            id = "falls",
            title = "Falls (Fall Detection)",
            icon = fallsIcon,
            tint = Color(0xFFE8756B),
            summary = "Fall alerts come from your watch, not from MedRecall+ itself.",
            setupSteps = listOf("Turn on fall detection in your watch's own app or settings, if your watch supports it."),
            useSteps = listOf("Your watch handles the alert and any emergency-contact flow on its own. MedRecall+ shows Falls as a watch-only placeholder on the Vitals screen for now."),
            comingSoonNote = "Fall detection inside MedRecall+ isn't available yet -- there's no Health Connect data type for it, so there's nothing to connect here yet."
        )
    )
    add(
        HelpTopic(
            id = "health_connect",
            title = "Health Connect",
            icon = Icons.Default.HealthAndSafety,
            tint = Color(0xFF6BA857),
            summary = "MedRecall+ reads (never writes) Blood Pressure, Weight, Blood Glucose, Heart Rate, Resting Heart Rate, HRV, Blood Oxygen, Respiratory Rate, and Body Temperature from Android's Health Connect -- the shared hub your watch or fitness app syncs into.",
            setupSteps = listOf(
                "Install Health Connect from the Play Store if it isn't already on your phone (it comes built in on Android 14 and up).",
                "Make sure your watch or fitness app (Samsung Health, Google Fit, etc.) is set to sync its data into Health Connect.",
                "In MedRecall+, open Vitals and tap the Health Connect banner to grant all nine permissions at once, or open a single vital and tap its own \"Connect\" button to grant just that one."
            ),
            useSteps = listOf(
                "Once connected, each vital's detail screen shows the last 30 days of Health Connect readings tagged \"Watch,\" alongside anything you entered yourself tagged \"Manual.\"",
                "You can grant permissions one vital at a time -- a vital without permission just shows your Manual entries until you connect it.",
                "MedRecall+ never writes anything back to Health Connect -- it only reads."
            )
        )
    )
    add(
        HelpTopic(
            id = "health_system_connections",
            title = "Health System Connections",
            icon = Icons.Default.LocalHospital,
            tint = Color(0xFF4C8C6B),
            summary = "Connect a patient-portal account (like Epic) so MedRecall+ can pull records directly from your health system.",
            setupSteps = listOf("Open Settings, tap Account, then find \"Epic Sandbox\" under Health System Connections."),
            useSteps = listOf("Tap the row to sign in and start pulling records from that health system, once it's live."),
            comingSoonNote = "Health system connections aren't wired up yet -- tapping the row currently explains what it will do rather than starting a real sign-in."
        )
    )
    add(vitalTopic(VitalType.HEART_RATE, "See heart rate readings from your watch, or log one yourself."))
    add(vitalTopic(VitalType.HEART_RATE_VARIABILITY, "Track HRV -- a marker of recovery and stress -- from Health Connect, or log one yourself.", displayTitle = "Heart Rate Variability (HRV)"))
    add(
        HelpTopic(
            id = "medical_id",
            title = Destination.MedicalId.label,
            icon = Destination.MedicalId.icon,
            tint = Destination.MedicalId.tileColors.first(),
            summary = "A quick-glance emergency card with your blood type, active conditions, medications, and date of birth.",
            setupSteps = listOf(
                "Tap Medical ID from the Home grid, then tap Edit.",
                "Fill in your date of birth and blood type.",
                "Select which of your saved Conditions should show on the card."
            ),
            useSteps = listOf(
                "Tap Medical ID any time to view the card -- your current medications list in automatically from Medications, so you don't have to re-enter them.",
                "Tap Edit whenever your conditions, meds, or details change."
            )
        )
    )
    add(
        HelpTopic(
            id = "medications",
            title = Destination.Medications.label,
            icon = Destination.Medications.icon,
            tint = Destination.Medications.tileColors.first(),
            summary = "Track what you take, the dosage, the schedule, and who prescribed it.",
            useSteps = listOf(
                "Tap Medications from the Home grid.",
                "Tap + to add one -- name, dosage, schedule, and prescribing doctor (pick from your saved Doctors).",
                "Tap an existing medication to edit it or mark it no longer active.",
                "Active medications automatically appear on your Medical ID card."
            )
        )
    )
    add(
        HelpTopic(
            id = "record_visit",
            title = Destination.RecordVisit.label,
            icon = Destination.RecordVisit.icon,
            tint = Destination.RecordVisit.tileColors.first(),
            summary = "Dictate notes from a doctor visit using speech-to-text, right after your appointment.",
            useSteps = listOf(
                "Tap Record Visit from the Home grid.",
                "Tap the mic button and speak your notes -- MedRecall+ transcribes as you talk.",
                "Save when you're done.",
                "From this same screen you can also jump to Calendar, schedule a follow-up appointment, or add the doctor you just saw if they're not already on file."
            )
        )
    )
    add(
        HelpTopic(
            id = "recurring_reminders",
            title = Destination.RecurringReminders.label,
            icon = Destination.RecurringReminders.icon,
            tint = Destination.RecurringReminders.tileColors.first(),
            summary = "Set repeating reminders for medications and appointments.",
            comingSoonNote = "Recurring Reminders isn't built yet -- tapping it in the bottom bar currently shows a coming-soon placeholder. Once live, it'll let you set repeating reminders for meds and appointments."
        )
    )
    add(
        HelpTopic(
            id = "reports",
            title = Destination.Reports.label,
            icon = Destination.Reports.icon,
            tint = Destination.Reports.tileColors.first(),
            summary = "Generate PDF reports from your records that you can view, print, or share.",
            useSteps = listOf(
                "Tap Reports from the Home grid.",
                "Choose a tile -- History, Health Summary, Condition Report, Doctors Report, or Medications Report.",
                "Tap it to generate a real PDF built from your current data.",
                "From the report's detail screen, use Print or Share to send it through any app on your phone (email, Drive, and so on)."
            ),
            comingSoonNote = "The Vitals Report and Medicare Claims tiles exist on the Reports grid but aren't wired up to real data yet -- they'll generate an empty report for now."
        )
    )
    add(vitalTopic(VitalType.RESPIRATORY_RATE, "Track breaths-per-minute from Health Connect, or log one yourself."))
    add(vitalTopic(VitalType.RESTING_HEART_RATE, "Track your resting heart rate over time from Health Connect, or log one yourself."))
    add(
        HelpTopic(
            id = "security",
            title = "Security (PIN & Biometric Lock)",
            icon = Icons.Default.Fingerprint,
            tint = Color(0xFF8A94A6),
            summary = "Lock MedRecall+ behind your fingerprint/face, a PIN, or both.",
            setupSteps = listOf(
                "Open Settings, then find the Security section.",
                "Turn on Biometric Lock -- if your phone has no fingerprint or face unlock set up yet, MedRecall+ offers to open your phone's own enrollment screen.",
                "Tap \"Set Backup PIN\" to also set a numeric PIN. It's used automatically as a fallback if biometrics fail, or as your only lock if you skip biometrics."
            ),
            useSteps = listOf(
                "Whenever MedRecall+ is backgrounded and reopened, you'll be asked to unlock -- with your fingerprint/face (plus a \"Use PIN instead\" option) or your PIN, depending on what you turned on.",
                "Change or remove your PIN any time from Settings > Set Backup PIN."
            )
        )
    )
    add(vitalTopic(VitalType.WEIGHT, "Track your weight over time, logged by hand or pulled in from Health Connect."))
}
