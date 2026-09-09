package com.asok.medrecall.data.backup

import com.asok.medrecall.data.local.MedRecallDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Outcome of backing up one [BackupSection] to Google Drive. */
sealed class BackupResult {
    data class Success(val section: BackupSection, val fileName: String) : BackupResult()
    data class Failure(val section: BackupSection, val message: String) : BackupResult()
}

/**
 * Uploads one JSON file per requested [BackupSection] to the connected
 * Google Drive account, always under the same folder path so every backup
 * lands in one place: My Drive > MedRecall > Android Backup. File names
 * look like "MedRecall Android Medications - 2026-09-09.json".
 *
 * Plain REST calls via java.net.HttpURLConnection rather than the Drive
 * client library -- matches GoogleAccountManager's choice to avoid a heavy
 * new dependency for a handful of calls (folder lookup/create, per-section
 * upload).
 *
 * RISK FLAG: unverified external-API surface, same category as the rest of
 * the Google account connector -- no compiler available here. The likeliest
 * failure point is the hand-rolled multipart upload body.
 */
object DriveBackupManager {

    private const val ROOT_FOLDER_NAME = "MedRecall"
    private const val BACKUP_FOLDER_NAME = "Android Backup"
    private const val DRIVE_FILES_URL = "https://www.googleapis.com/drive/v3/files"
    private const val DRIVE_UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"

    /**
     * Exports and uploads each of [sections] as its own JSON file. Resolves
     * (or creates) the destination folder once, then uploads sequentially --
     * fine at this app's personal-use scale, and simpler than juggling
     * concurrent requests against one access token.
     */
    suspend fun backupSections(
        database: MedRecallDatabase,
        accessToken: String,
        sections: List<BackupSection>
    ): List<BackupResult> = withContext(Dispatchers.IO) {
        val backupFolderId = try {
            val rootFolderId = findOrCreateFolder(accessToken, ROOT_FOLDER_NAME, parentId = null)
            findOrCreateFolder(accessToken, BACKUP_FOLDER_NAME, parentId = rootFolderId)
        } catch (e: Exception) {
            return@withContext sections.map { BackupResult.Failure(it, e.message ?: "Could not reach Google Drive.") }
        }

        val date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

        sections.map { section ->
            try {
                val json = BackupExporter.export(database, section)
                val fileName = "MedRecall Android ${section.label} - $date.json"
                uploadJsonFile(accessToken, backupFolderId, fileName, json)
                BackupResult.Success(section, fileName)
            } catch (e: Exception) {
                BackupResult.Failure(section, e.message ?: "Backup failed.")
            }
        }
    }

    /** Finds a folder by exact name (scoped to [parentId], or Drive root when null) or creates it if missing. Returns its Drive file id. */
    private fun findOrCreateFolder(accessToken: String, name: String, parentId: String?): String {
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

        val existing = JSONObject(response).optJSONArray("files")
        if (existing != null && existing.length() > 0) {
            return existing.getJSONObject(0).getString("id")
        }

        val metadata = JSONObject().apply {
            put("name", name)
            put("mimeType", "application/vnd.google-apps.folder")
            if (parentId != null) put("parents", listOf(parentId))
        }
        val createConnection = (URL(DRIVE_FILES_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            doOutput = true
        }
        createConnection.outputStream.use { it.write(metadata.toString().toByteArray(Charsets.UTF_8)) }
        val createResponse = createConnection.inputStream.bufferedReader().use { it.readText() }
        createConnection.disconnect()
        return JSONObject(createResponse).getString("id")
    }

    /** Uploads [jsonContent] into Drive folder [folderId] under [fileName], via a simple multipart request (JSON metadata part + JSON content part). */
    private fun uploadJsonFile(accessToken: String, folderId: String, fileName: String, jsonContent: String) {
        val boundary = "medrecall-backup-${System.currentTimeMillis()}"
        val metadata = JSONObject().apply {
            put("name", fileName)
            put("parents", listOf(folderId))
        }

        val connection = (URL(DRIVE_UPLOAD_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
            doOutput = true
        }

        connection.outputStream.use { output ->
            output.write("--$boundary\r\n".toByteArray())
            output.write("Content-Type: application/json; charset=UTF-8\r\n\r\n".toByteArray())
            output.write(metadata.toString().toByteArray(Charsets.UTF_8))
            output.write("\r\n--$boundary\r\n".toByteArray())
            output.write("Content-Type: application/json; charset=UTF-8\r\n\r\n".toByteArray())
            output.write(jsonContent.toByteArray(Charsets.UTF_8))
            output.write("\r\n--$boundary--".toByteArray())
        }

        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            val error = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
            connection.disconnect()
            throw IllegalStateException("Drive upload failed: $error")
        }
        connection.disconnect()
    }
}
