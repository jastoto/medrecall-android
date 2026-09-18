package com.asok.medrecall.data.backup

/**
 * One checkbox on the Backup & Restore screen -- lets Asok back up
 * everything or just a subset (e.g. only Medications before a doctor
 * visit). The enum's [name] is also the exact string stored in a
 * backup's "sections" array (see [BackupCodec]), so don't rename an
 * existing constant without also handling old backups that used the
 * old name.
 */
enum class BackupSection(val label: String) {
    MEDICAL_ID("Medical ID"),
    DOCTORS("Doctors"),
    CONDITIONS("Conditions"),
    MEDICATIONS("Medications"),
    APPOINTMENTS("Appointments"),
    VITALS("Vitals"),
    NOTES("Notes")
}

/** One backup file as listed from a cloud destination, for the Restore picker. */
data class CloudBackupFile(
    val id: String,
    val name: String,
    val modifiedAtMillis: Long?,
    val sizeBytes: Long?
)

/** Where a backup can be sent to / restored from. */
enum class BackupDestination(val label: String) {
    GOOGLE_DRIVE("Google Drive"),
    ONE_DRIVE("OneDrive"),
    THIS_PHONE("Files on This Phone")
}
