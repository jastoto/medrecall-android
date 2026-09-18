package com.asok.medrecall.data.backup

import android.accounts.Account
import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.util.UUID

/**
 * Google Drive backup destination. Sign-in itself (the account picker
 * UI) happens in [com.asok.medrecall.ui.settings.BackupViewModel] via
 * Play Services' GoogleSignInClient, since that needs an Activity to
 * launch an intent from; once signed in, [onSignedIn] is called with
 * the chosen account's name and everything else here is a plain REST
 * call to the Drive v3 API using that account's OAuth token.
 *
 * drive.file scope is used deliberately (not full Drive access) --
 * this app can only see/modify files IT created, i.e. its own backups,
 * never anything else already in Asok's Drive.
 */
class GoogleDriveBackupService(private val context: Context) : CloudBackupService {

    private val prefs = context.getSharedPreferences("google_drive_backup", Context.MODE_PRIVATE)
    private var cachedFolderId: String? = null

    fun onSignedIn(accountEmail: String) {
        prefs.edit().putString(KEY_ACCOUNT, accountEmail).apply()
        cachedFolderId = null
    }

    override suspend fun isSignedIn(): Boolean = prefs.getString(KEY_ACCOUNT, null) != null

    override suspend fun signedInAccountLabel(): String? = prefs.getString(KEY_ACCOUNT, null)

    override suspend fun signOut() {
        prefs.edit().remove(KEY_ACCOUNT).apply()
        cachedFolderId = null
    }

    override suspend fun listBackups(): Result<List<CloudBackupFile>> = runCatching {
        withContext(Dispatchers.IO) {
            val token = accessToken()
            val folder = folderId(token)
            val q = URLEncoder.encode(
                "'$folder' in parents and trashed=false and name contains 'medrecall_backup_'",
                "UTF-8"
            )
            val url =
                "$FILES_URL?q=$q&fields=files(id,name,modifiedTime,size)&orderBy=modifiedTime desc&spaces=drive"
            val arr = JSONObject(httpGet(url, token)).getJSONArray("files")
            (0 until arr.length()).map { i ->
                val f = arr.getJSONObject(i)
                CloudBackupFile(
                    id = f.getString("id"),
                    name = f.getString("name"),
                    modifiedAtMillis = f.optString("modifiedTime").takeIf { it.isNotBlank() }
                        ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() },
                    sizeBytes = f.optString("size").takeIf { it.isNotBlank() }?.toLongOrNull()
                )
            }
        }
    }

    override suspend fun upload(fileName: String, content: String): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val token = accessToken()
            val folder = folderId(token)
            val metadata = JSONObject().apply {
                put("name", fileName)
                put("parents", JSONArray(listOf(folder)))
            }
            multipartUpload(token, metadata, content)
            Unit
        }
    }

    override suspend fun download(file: CloudBackupFile): Result<String> = runCatching {
        withContext(Dispatchers.IO) {
            val token = accessToken()
            httpGet("$FILES_URL/${file.id}?alt=media", token)
        }
    }

    /**
     * Blocking call to Play Services for a fresh OAuth token -- must run
     * off the main thread. Throws [NotSignedInException] if [onSignedIn]
     * was never called, or the underlying [UserRecoverableAuthException]
     * if Google needs the user to re-consent (the caller should catch
     * that and re-run the sign-in flow).
     */
    private fun accessToken(): String {
        val email = prefs.getString(KEY_ACCOUNT, null) ?: throw NotSignedInException()
        val account = Account(email, "com.google")
        return GoogleAuthUtil.getToken(context, account, "oauth2:$DRIVE_FILE_SCOPE")
    }

    private fun folderId(token: String): String {
        cachedFolderId?.let { return it }
        val q = URLEncoder.encode(
            "mimeType='application/vnd.google-apps.folder' and name='$FOLDER_NAME' and trashed=false",
            "UTF-8"
        )
        val existing = JSONObject(httpGet("$FILES_URL?q=$q&fields=files(id)&spaces=drive", token))
            .getJSONArray("files")
        if (existing.length() > 0) {
            return existing.getJSONObject(0).getString("id").also { cachedFolderId = it }
        }
        val body = JSONObject().apply {
            put("name", FOLDER_NAME)
            put("mimeType", "application/vnd.google-apps.folder")
        }
        val created = JSONObject(httpPostJson("$FILES_URL?fields=id", token, body.toString()))
        return created.getString("id").also { cachedFolderId = it }
    }

    private fun httpGet(url: String, token: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $token")
        }
        return connection.readBodyOrThrow()
    }

    private fun httpPostJson(url: String, token: String, jsonBody: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        }
        connection.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
        return connection.readBodyOrThrow()
    }

    private fun multipartUpload(token: String, metadata: JSONObject, content: String): String {
        val boundary = "medrecall-${UUID.randomUUID()}"
        val url = URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
        }
        connection.outputStream.use { out: OutputStream ->
            fun writeLine(s: String) = out.write((s + "\r\n").toByteArray(Charsets.UTF_8))
            writeLine("--$boundary")
            writeLine("Content-Type: application/json; charset=UTF-8")
            writeLine("")
            writeLine(metadata.toString())
            writeLine("--$boundary")
            writeLine("Content-Type: application/json; charset=UTF-8")
            writeLine("")
            writeLine(content)
            writeLine("--$boundary--")
        }
        return connection.readBodyOrThrow()
    }

    private fun HttpURLConnection.readBodyOrThrow(): String {
        val code = responseCode
        val stream = if (code in 200..299) inputStream else errorStream
        val body = stream?.let { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).readText() }.orEmpty()
        if (code !in 200..299) {
            throw IOException("Google Drive request failed ($code): $body")
        }
        return body
    }

    companion object {
        private const val KEY_ACCOUNT = "account_email"
        private const val FOLDER_NAME = "Android Backup"
        private const val FILES_URL = "https://www.googleapis.com/drive/v3/files"
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    }
}
