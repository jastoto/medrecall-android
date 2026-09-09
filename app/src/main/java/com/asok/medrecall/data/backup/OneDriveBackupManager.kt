package com.asok.medrecall.data.backup

import com.asok.medrecall.data.local.MedRecallDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Uploads one JSON file per requested [BackupSection] to the connected
 * OneDrive account, always under the same folder path: OneDrive > MedRecall >
 * Android Backup. File names look like
 * "MedRecall Android Medications - 2026-09-09.json" -- identical naming to
 * DriveBackupManager, so DriveRestoreManager's file-name pattern/format
 * conventions (and BackupExporter/BackupImporter) are reused as-is.
 *
 * Unlike Google Drive's folder-id lookup/create dance, Microsoft Graph lets
 * a file be addressed straight by path -- PUT to
 * /me/drive/root:/MedRecall/Android Backup/{fileName}:/content creates any
 * missing intermediate folders automatically, so there's no separate
 * findOrCreateFolder step here.
 *
 * RISK FLAG: unverified external-API surface, same category as
 * DriveBackupManager -- no compiler available here.
 */
object OneDriveBackupManager {

    private const val FOLDER_PATH = "MedRecall/Android Backup"
    private const val GRAPH_BASE = "https://graph.microsoft.com/v1.0/me/drive/root:"

    suspend fun backupSections(
        database: MedRecallDatabase,
        accessToken: String,
        sections: List<BackupSection>
    ): List<BackupResult> = withContext(Dispatchers.IO) {
        val date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

        sections.map { section ->
            try {
                val json = BackupExporter.export(database, section)
                val fileName = "MedRecall Android ${section.label} - $date.json"
                uploadJsonFile(accessToken, fileName, json)
                BackupResult.Success(section, fileName)
            } catch (e: Exception) {
                BackupResult.Failure(section, e.message ?: "Backup failed.")
            }
        }
    }

    /** Uploads [jsonContent] to OneDrive under FOLDER_PATH/[fileName] via Graph's simple upload (fine for our small JSON files, well under the 4MB simple-upload limit). */
    private fun uploadJsonFile(accessToken: String, fileName: String, jsonContent: String) {
        val encodedPath = encodeGraphPath("$FOLDER_PATH/$fileName")
        val url = URL("$GRAPH_BASE/$encodedPath:/content")

        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
        }
        connection.outputStream.use { it.write(jsonContent.toByteArray(Charsets.UTF_8)) }

        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            val error = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
            connection.disconnect()
            throw IllegalStateException("OneDrive upload failed: $error")
        }
        connection.disconnect()
    }

    /** Percent-encodes each path segment (spaces, etc.) while keeping "/" as the path separator -- shared with OneDriveRestoreManager. */
    internal fun encodeGraphPath(path: String): String =
        path.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
}
