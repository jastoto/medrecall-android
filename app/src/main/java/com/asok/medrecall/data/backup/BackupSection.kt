package com.asok.medrecall.data.backup

/**
 * The app's data sections a backup can cover -- mirrors MedRecall's own
 * navigation categories (see navigation/Destinations.kt) so "back up
 * Medications" means the same thing here as it does on the Home screen.
 *
 * HEALTH has no Room table yet (Destination.Health is still a StubScreen
 * with no data behind it) -- it's included per Asok's request so the
 * section already shows up in backups, exporting a placeholder today and
 * ready to gain real content whenever that screen is built.
 */
enum class BackupSection(val label: String) {
    DOCTORS("Doctors"),
    MEDICATIONS("Medications"),
    VITALS("Vitals"),
    CONDITIONS("Conditions"),
    APPOINTMENTS("Appointments"),
    VISIT_NOTES("Visit Notes"),
    MEDICAL_ID("Medical ID"),
    HEALTH("Health")
}
