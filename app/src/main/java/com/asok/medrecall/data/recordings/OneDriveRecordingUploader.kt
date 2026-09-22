package com.asok.medrecall.data.recordings

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Uploads one finished visit recording (or, per punch item #43, its paired
 * structured PDF note) to OneDrive under a NEW top-level folder --
 * OneDrive > MedRecall-Android > <Doctor Name> -- deliberately separate from
 * OneDriveBackupManager's existing OneDrive > MedRecall > Android Backup
 * JSON-backup folder, per Asok's instruction (2026-09-21), mirroring
 * DriveRecordingUploader's structure. Graph's path-addressable PUT creates
 * every missing intermediate folder automatically, same as
 * OneDriveBackupManager -- no separate folder-lookup step needed here either.
 *
 * RISK FLAG: unverified, no compiler/device available here. Simple PUT
 * upload is fine up to Graph's simple-upload size ceiling (well beyond a
 * typical visit recording or note PDF) -- an unusually long recording could
 * need a resumable upload session instead, not implemented here.
 */
object OneDriveRecordingUploader {

    private const val ROOT_FOLDER_NAME = "MedRecall-Android"
    private const val GRAPH_BASE = "https://graph.microsoft.com/v1.0/me/drive/root:"

    /** Returns a human-readable path for the confirmation message shown to Asok. [mimeType] is whatever [file] actually is -- "audio/mp4" for a recording, "application/pdf" for a visit-note PDF. */
    fun upload(accessToken: String, doctorFolderName: String, fileName: String, file: File, mimeType: String): String {
        val folderPath = "$ROOT_FOLDER_NAME/$doctorFolderName"
        val encodedPath = encodeGraphPath("$folderPath/$fileName")
        val url = URL("$GRAPH_BASE/$encodedPath:/content")

        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", mimeType)
            doOutput = true
        }
        connection.outputStream.use { output ->
            file.inputStream().use { it.copyTo(output) }
        }

        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            val error = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
            connection.disconnect()
            throw IllegalStateException("OneDrive upload failed: $error")
        }
        connection.disconnect()
        return "OneDrive/$folderPath/$fileName"
    }

    /** Percent-encodes each path segment (spaces, etc.) while keeping "/" as the path separator -- same helper OneDriveBackupManager/OneDriveRestoreManager use. */
    private fun encodeGraphPath(path: String): String =
        path.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
}
