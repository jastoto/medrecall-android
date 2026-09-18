package com.asok.medrecall.data.backup

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
