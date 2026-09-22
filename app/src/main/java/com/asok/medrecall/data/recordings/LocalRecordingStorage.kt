package com.asok.medrecall.data.recordings

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream

/**
 * Saves a finished visit recording (and, per punch item #43, its paired
 * structured PDF note) under a browsable "MedRecall-Android/<Doctor Name>/"
 * folder -- a brand-new top-level folder structure per Asok's instruction
 * (2026-09-21), deliberately separate from the existing Google Drive/OneDrive
 * JSON *backup* folder (ROOT_FOLDER_NAME "MedRecall" in DriveBackupManager/
 * OneDriveBackupManager) -- one subfolder per doctor, mirrored the same way
 * on Google Drive and OneDrive (see DriveRecordingUploader /
 * OneDriveRecordingUploader).
 *
 * Modern scoped storage (API 29+) won't let an app write to an arbitrary path
 * under the storage root, so this lands under
 * Downloads/MedRecall-Android/<Doctor>/ via MediaStore -- still fully visible
 * in the Files app and to a PC over USB, just nested one level under
 * Downloads instead of at the storage root. Pre-scoped-storage devices
 * (API 26-28) use the legacy File API directly against the public Downloads
 * directory, which needs the WRITE_EXTERNAL_STORAGE permission (declared
 * maxSdkVersion="28" in the manifest since it's unnecessary/a no-op above
 * that level).
 *
 * RISK FLAG: unverified against a real build/device, same category as every
 * other storage/account connector in this app -- no compiler available here.
 */
object LocalRecordingStorage {

    private const val ROOT_FOLDER_NAME = "MedRecall-Android"

    /** Returns a human-readable path for the confirmation message shown to Asok (the real on-disk location is owned by MediaStore on API 29+, this is informational). [mimeType] is whatever the file actually is -- "audio/mp4" for the recording, "application/pdf" for the visit-note PDF. */
    fun save(context: Context, sourceFile: File, doctorFolderName: String, fileName: String, mimeType: String): String {
        val safeDoctorName = sanitize(doctorFolderName)
        val relativeDir = "$ROOT_FOLDER_NAME/$safeDoctorName"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(context, sourceFile, relativeDir, fileName, mimeType)
        } else {
            saveViaLegacyFile(sourceFile, relativeDir, fileName)
        }
        return "Downloads/$relativeDir/$fileName"
    }

    private fun saveViaMediaStore(context: Context, sourceFile: File, relativeDir: String, fileName: String, mimeType: String) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$relativeDir")
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Could not create the file in Downloads.")
        val opened = resolver.openOutputStream(uri)?.use { out ->
            FileInputStream(sourceFile).use { input -> input.copyTo(out) }
        }
        if (opened == null) throw IllegalStateException("Could not write the file to Downloads.")
    }

    private fun saveViaLegacyFile(sourceFile: File, relativeDir: String, fileName: String) {
        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), relativeDir)
        dir.mkdirs()
        val dest = File(dir, fileName)
        sourceFile.copyTo(dest, overwrite = true)
    }

    /** Doctor names can contain characters that are awkward/illegal in folder names (slashes, etc.) -- strip anything that isn't alphanumeric, space, hyphen, or underscore. */
    private fun sanitize(name: String): String =
        name.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifBlank { "Unknown Doctor" }
}
