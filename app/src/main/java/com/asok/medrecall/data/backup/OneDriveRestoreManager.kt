package com.asok.medrecall.data.backup

import com.asok.medrecall.data.local.MedRecallDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** One backup file found on OneDrive, parsed from its file name (see FILE_NAME_PATTERN below) -- same shape as DriveBackupFile, just no Drive file id (OneDrive downloads are addressed by path instead). */
data class OneDriveBackupFile(
    val fileName: String,
    val section: BackupSection,
    val dateLabel: String
)

/**
 * Reads backups back from the same OneDrive folder OneDriveBackupManager
 * writes to (OneDrive > MedRecall > Android Backup): lists what's there so
 * the user can pick a date per section, downloads the chosen file's JSON,
 * and hands it to BackupImporter to write into Room. Mirrors
 * DriveRestoreManager; see that class's doc comment for the overall shape.
 *
 * RISK FLAG: unverified external-API surface, same category as
 * DriveRestoreManager -- no compiler available here. The download step
 * (Graph's :/content redirects to a separate pre-signed URL) is the part
 * most likely to need adjustment on Asok's first real test.
 */
object OneDriveRestoreManager {

    private const val FOLDER_PATH = "MedRecall/Android Backup"
    private const val GRAPH_BASE = "https://graph.microsoft.com/v1.0/me/drive/root:"
    private val FILE_NAME_PATTERN = Regex("""^MedRecall Android (.+) - (\d{4}-\d{2}-\d{2})\.json$""")

    /** Lists every backup file in the Android Backup folder. Empty if that folder doesn't exist yet (nothing backed up so far -- Graph returns 404 for a missing path, treated the same as an empty folder). */
    suspend fun listAvailableBackups(accessToken: String): List<OneDriveBackupFile> = withContext(Dispatchers.IO) {
        val encodedPath = OneDriveBackupManager.encodeGraphPath(FOLDER_PATH)
        val url = URL("$GRAPH_BASE/$encodedPath:/children?\$select=name")

        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $accessToken")
        }

        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
            connection.disconnect()
            return@withContext emptyList()
        }
        if (responseCode !in 200..299) {
            val error = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
            connection.disconnect()
            throw IllegalStateException("OneDrive listing failed: $error")
        }

        val response = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()

        val items = JSONObject(response).optJSONArray("value") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until items.length()) {
                val name = items.getJSONObject(i).getString("name")
                val match = FILE_NAME_PATTERN.matchEntire(name) ?: continue
                val (labelText, date) = match.destructured
                val section = BackupSection.entries.find { it.label == labelText } ?: continue
                add(OneDriveBackupFile(fileName = name, section = section, dateLabel = date))
            }
        }
    }

    /** Groups flat listAvailableBackups() results into one row per section (newest date first), for the restore picker. Health is excluded -- its backup is just a placeholder note, nothing to restore. */
    fun groupForPicker(files: List<OneDriveBackupFile>): List<OneDriveSectionBackups> =
        files
            .filter { it.section != BackupSection.HEALTH }
            .groupBy { it.section }
            .map { (section, filesForSection) -> OneDriveSectionBackups(section, filesForSection.sortedByDescending { it.dateLabel }) }
            .sortedBy { it.section }

    /** Downloads [file]'s JSON content and imports it into Room, replacing that section's current data. */
    suspend fun restoreFile(database: MedRecallDatabase, accessToken: String, file: OneDriveBackupFile): RestoreResult =
        withContext(Dispatchers.IO) {
            try {
                val json = downloadFile(accessToken, file.fileName)
                BackupImporter.import(database, file.section, json)
                RestoreResult.Success(file.section, file.fileName)
            } catch (e: Exception) {
                RestoreResult.Failure(file.section, file.fileName, e.message ?: "Restore failed.")
            }
        }

    /**
     * Graph's :/content endpoint responds with a 302 to a separate,
     * pre-authenticated download URL -- that second request must NOT carry
     * our Authorization header (some hosts reject an unexpected auth
     * header), so redirects are followed manually here rather than relying
     * on HttpURLConnection's automatic redirect handling.
     */
    private fun downloadFile(accessToken: String, fileName: String): String {
        val encodedPath = OneDriveBackupManager.encodeGraphPath("$FOLDER_PATH/$fileName")
        val firstUrl = URL("$GRAPH_BASE/$encodedPath:/content")

        val firstConnection = (firstUrl.openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $accessToken")
        }

        val firstCode = firstConnection.responseCode
        if (firstCode == HttpURLConnection.HTTP_MOVED_TEMP || firstCode == HttpURLConnection.HTTP_MOVED_PERM || firstCode == 303) {
            val redirectUrl = firstConnection.getHeaderField("Location")
            firstConnection.disconnect()
            if (redirectUrl == null) throw IllegalStateException("OneDrive download did not provide a redirect location.")

            val secondConnection = (URL(redirectUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
            }
            val secondCode = secondConnection.responseCode
            if (secondCode !in 200..299) {
                val error = secondConnection.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $secondCode"
                secondConnection.disconnect()
                throw IllegalStateException("OneDrive download failed: $error")
            }
            val content = secondConnection.inputStream.bufferedReader().use { it.readText() }
            secondConnection.disconnect()
            return content
        }

        if (firstCode !in 200..299) {
            val error = firstConnection.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $firstCode"
            firstConnection.disconnect()
            throw IllegalStateException("OneDrive download failed: $error")
        }
        val content = firstConnection.inputStream.bufferedReader().use { it.readText() }
        firstConnection.disconnect()
        return content
    }
}

/** One section's available OneDrive backups, newest first -- what the restore picker dialog shows a row for. */
data class OneDriveSectionBackups(val section: BackupSection, val files: List<OneDriveBackupFile>)
