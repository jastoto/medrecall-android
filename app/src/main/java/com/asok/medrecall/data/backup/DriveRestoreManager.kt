package com.asok.medrecall.data.backup

import com.asok.medrecall.data.local.MedRecallDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One backup file found on Google Drive, parsed from its file name (see FILE_NAME_PATTERN below). */
data class DriveBackupFile(
    val fileId: String,
    val fileName: String,
    val section: BackupSection,
    val dateLabel: String
)

/** One section's available Drive backups, newest first -- what the restore picker dialog shows a row for. */
data class SectionBackups(val section: BackupSection, val files: List<DriveBackupFile>)

/** Outcome of restoring one chosen Drive backup file into Room. */
sealed class RestoreResult {
    data class Success(val section: BackupSection, val fileName: String) : RestoreResult()
    data class Failure(val section: BackupSection, val fileName: String, val message: String) : RestoreResult()
}

/**
 * Reads backups back from the same Drive folder DriveBackupManager writes
 * to (My Drive > MedRecall > Android Backup): lists what's there so the
 * user can pick a date per section, downloads the chosen file's JSON, and
 * hands it to BackupImporter to write into Room.
 *
 * RISK FLAG: same unverified-external-API category as the rest of this
 * feature -- no compiler available. Likeliest failure point is the file
 * name regex if a backup file was ever renamed by hand in Drive.
 */
object DriveRestoreManager {

    private const val ROOT_FOLDER_NAME = "MedRecall"
    private const val BACKUP_FOLDER_NAME = "Android Backup"
    private const val DRIVE_FILES_URL = "https://www.googleapis.com/drive/v3/files"
    private val FILE_NAME_PATTERN = Regex("""^MedRecall Android (.+) - (\d{4}-\d{2}-\d{2})\.json$""")

    /** Lists every backup file in the Android Backup folder. Empty if that folder doesn't exist yet (nothing backed up so far). */
    suspend fun listAvailableBackups(accessToken: String): List<DriveBackupFile> = withContext(Dispatchers.IO) {
        val rootFolderId = findFolder(accessToken, ROOT_FOLDER_NAME, parentId = null) ?: return@withContext emptyList()
        val backupFolderId = findFolder(accessToken, BACKUP_FOLDER_NAME, parentId = rootFolderId) ?: return@withContext emptyList()

        val query = "'$backupFolderId' in parents and trashed=false"
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = URL("$DRIVE_FILES_URL?q=$encodedQuery&fields=files(id,name)")

        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $accessToken")
        }
        val response = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()

        val files = JSONObject(response).optJSONArray("files") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until files.length()) {
                val f = files.getJSONObject(i)
                val name = f.getString("name")
                val match = FILE_NAME_PATTERN.matchEntire(name) ?: continue
                val (labelText, date) = match.destructured
                val section = BackupSection.entries.find { it.label == labelText } ?: continue
                add(DriveBackupFile(fileId = f.getString("id"), fileName = name, section = section, dateLabel = date))
            }
        }
    }

    /** Groups flat listAvailableBackups() results into one row per section (newest date first), for the restore picker. Health is excluded -- its backup is just a placeholder note, nothing to restore. */
    fun groupForPicker(files: List<DriveBackupFile>): List<SectionBackups> =
        files
            .filter { it.section != BackupSection.HEALTH }
            .groupBy { it.section }
            .map { (section, filesForSection) -> SectionBackups(section, filesForSection.sortedByDescending { it.dateLabel }) }
            .sortedBy { it.section }

    /** Downloads [file]'s JSON content and imports it into Room, replacing that section's current data. */
    suspend fun restoreFile(database: MedRecallDatabase, accessToken: String, file: DriveBackupFile): RestoreResult =
        withContext(Dispatchers.IO) {
            try {
                val json = downloadFile(accessToken, file.fileId)
                BackupImporter.import(database, file.section, json)
                RestoreResult.Success(file.section, file.fileName)
            } catch (e: Exception) {
                RestoreResult.Failure(file.section, file.fileName, e.message ?: "Restore failed.")
            }
        }

    private fun downloadFile(accessToken: String, fileId: String): String {
        val url = URL("$DRIVE_FILES_URL/$fileId?alt=media")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $accessToken")
        }
        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            val error = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
            connection.disconnect()
            throw IllegalStateException("Drive download failed: $error")
        }
        val content = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()
        return content
    }

    /** Read-only folder lookup by exact name -- unlike DriveBackupManager.findOrCreateFolder, this never creates one. Null if missing. */
    private fun findFolder(accessToken: String, name: String, parentId: String?): String? {
        val parentClause = if (parentId != null) "'$parentId' in parents" else "'root' in parents"
        val query = "mimeType='application/vnd.google-apps.folder' and name='$name' and $parentClause and trashed=false"
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = URL("$DRIVE_FILES_URL?q=$encodedQuery&fields=files(id,name)")

        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $accessToken")
        }
        val response = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()

        val files = JSONObject(response).optJSONArray("files")
        return if (files != null && files.length() > 0) files.getJSONObject(0).getString("id") else null
    }
}
