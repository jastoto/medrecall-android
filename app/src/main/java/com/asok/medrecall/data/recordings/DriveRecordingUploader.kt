package com.asok.medrecall.data.recordings

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Uploads one finished visit recording (or, per punch item #43, its paired
 * structured PDF note) to Google Drive under a NEW top-level folder structure
 * -- My Drive > MedRecall-Android > <Doctor Name> -- kept deliberately
 * separate from DriveBackupManager's existing
 * My Drive > MedRecall > Android Backup JSON-backup folder, per Asok's
 * instruction (2026-09-21) that recordings/notes get their own per-doctor
 * structure, mirrored on OneDrive too (see OneDriveRecordingUploader).
 *
 * Same plain-REST-via-HttpURLConnection approach and folder find-or-create
 * dance as DriveBackupManager (Drive has no path-addressable upload API the
 * way Microsoft Graph does) -- duplicated here rather than shared, matching
 * how DriveBackupManager/OneDriveBackupManager are already two independent
 * objects rather than a shared base class.
 *
 * RISK FLAG: unverified, no compiler/device available here. The multipart
 * upload endpoint is fine for a typical visit-length recording or a note PDF,
 * but isn't meant for very large files -- an unusually long recording may
 * need a resumable upload session instead (not implemented here; same
 * caveat DriveBackupManager already carries for its own uploads).
 */
object DriveRecordingUploader {

    private const val ROOT_FOLDER_NAME = "MedRecall-Android"
    private const val DRIVE_FILES_URL = "https://www.googleapis.com/drive/v3/files"
    private const val DRIVE_UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"

    /** Returns a human-readable path for the confirmation message shown to Asok. [mimeType] is whatever [file] actually is -- "audio/mp4" for a recording, "application/pdf" for a visit-note PDF. */
    fun upload(accessToken: String, doctorFolderName: String, fileName: String, file: File, mimeType: String): String {
        val rootFolderId = findOrCreateFolder(accessToken, ROOT_FOLDER_NAME, parentId = null)
        val doctorFolderId = findOrCreateFolder(accessToken, doctorFolderName, parentId = rootFolderId)
        uploadFile(accessToken, doctorFolderId, fileName, file, mimeType)
        return "My Drive/$ROOT_FOLDER_NAME/$doctorFolderName/$fileName"
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
            if (parentId != null) put("parents", JSONArray().put(parentId))
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

    /** Uploads [file] into Drive folder [folderId] under [fileName], via a multipart request (JSON metadata part + binary content part). */
    private fun uploadFile(accessToken: String, folderId: String, fileName: String, file: File, mimeType: String) {
        val boundary = "medrecall-recording-${System.currentTimeMillis()}"
        val metadata = JSONObject().apply {
            put("name", fileName)
            put("parents", JSONArray().put(folderId))
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
            output.write("Content-Type: $mimeType\r\n\r\n".toByteArray())
            file.inputStream().use { it.copyTo(output) }
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
