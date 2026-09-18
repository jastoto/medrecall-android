package com.asok.medrecall.data.backup

/**
 * One cloud backup destination (Google Drive or OneDrive). Both
 * implementations talk to their REST API directly with the signed-in
 * account's OAuth token rather than pulling in a full generated SDK --
 * see [GoogleDriveBackupService] / [OneDriveBackupService].
 *
 * Every backup file this app writes is named "medrecall_backup_<epoch
 * millis>.json" and lives in an app-created folder ("Android Backup"
 * on Google Drive, "MedRecall Backups" on OneDrive), so [listBackups]
 * only ever needs to look inside that one folder rather than searching
 * the whole account.
 */
interface CloudBackupService {
    /** True once a usable, signed-in account is cached (survives process death). */
    suspend fun isSignedIn(): Boolean

    /** Display name/email of the signed-in account, or null if not signed in. */
    suspend fun signedInAccountLabel(): String?

    suspend fun signOut()

    suspend fun listBackups(): Result<List<CloudBackupFile>>

    suspend fun upload(fileName: String, content: String): Result<Unit>

    suspend fun download(file: CloudBackupFile): Result<String>
}

/** Thrown by a [CloudBackupService] when a call needs interactive sign-in first. */
class NotSignedInException(message: String = "Not signed in.") : Exception(message)
