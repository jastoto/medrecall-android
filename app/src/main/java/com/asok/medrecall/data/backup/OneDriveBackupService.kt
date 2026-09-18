package com.asok.medrecall.data.backup

import android.app.Activity
import android.content.Context
import com.asok.medrecall.R
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.ISingleAccountPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.exception.MsalException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OneDrive backup destination, via MSAL (sign-in) + plain Microsoft
 * Graph REST calls (everything after sign-in) -- same shape as
 * [GoogleDriveBackupService], no generated Graph SDK needed.
 *
 * MSAL keeps its own signed-in-account cache on disk (that's what
 * "single account mode" in msal_config.json means), so [isSignedIn]
 * and [signedInAccountLabel] just ask MSAL what it already has rather
 * than this class tracking that itself.
 *
 * [signIn] needs an Activity to show Microsoft's login page in, so it
 * (unlike every other method here) must be called from the UI layer,
 * not a background task.
 */
class OneDriveBackupService(private val context: Context) : CloudBackupService {

    @Volatile private var pca: ISingleAccountPublicClientApplication? = null
    private var cachedFolderChecked = false

    private suspend fun client(): ISingleAccountPublicClientApplication {
        pca?.let { return it }
        return withContext(Dispatchers.IO) {
            PublicClientApplication.createSingleAccountPublicClientApplication(context, R.raw.msal_config)
                .also { pca = it }
        }
    }

    suspend fun signIn(activity: Activity): Result<String> = runCatching {
        val app = client()
        val result = suspendCancellableCoroutine<IAuthenticationResult> { cont ->
            app.signIn(
                activity,
                null,
                SCOPES,
                object : AuthenticationCallback {
                    override fun onSuccess(authenticationResult: IAuthenticationResult) {
                        cont.resume(authenticationResult)
                    }
                    override fun onError(exception: MsalException) {
                        cont.resumeWithException(exception)
                    }
                    override fun onCancel() {
                        cont.cancel()
                    }
                }
            )
        }
        cachedFolderChecked = false
        result.account.username
    }

    override suspend fun isSignedIn(): Boolean = signedInAccountLabel() != null

    override suspend fun signedInAccountLabel(): String? = withContext(Dispatchers.IO) {
        runCatching { client().currentAccount?.currentAccount?.username?.takeIf { it.isNotBlank() } }
            .getOrNull()
    }

    override suspend fun signOut() {
        withContext(Dispatchers.IO) {
            runCatching { client().signOut() }
            cachedFolderChecked = false
        }
    }

    override suspend fun listBackups(): Result<List<CloudBackupFile>> = runCatching {
        withContext(Dispatchers.IO) {
            val token = accessToken()
            ensureFolder(token)
            val url = "$GRAPH_ROOT:/${encodePathSegment(FOLDER_NAME)}:/children?\$select=id,name,lastModifiedDateTime,size"
            val arr = JSONObject(httpGet(url, token)).getJSONArray("value")
            (0 until arr.length()).map { i -> arr.getJSONObject(i) }
                .filter { it.optString("name").startsWith(FILE_PREFIX) }
                .map { f ->
                    CloudBackupFile(
                        id = f.getString("id"),
                        name = f.getString("name"),
                        modifiedAtMillis = f.optString("lastModifiedDateTime").takeIf { it.isNotBlank() }
                            ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() },
                        sizeBytes = if (f.has("size")) f.optLong("size") else null
                    )
                }
        }
    }

    override suspend fun upload(fileName: String, content: String): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val token = accessToken()
            ensureFolder(token)
            val url = "$GRAPH_ROOT:/${encodePathSegment(FOLDER_NAME)}/${encodePathSegment(fileName)}:/content"
            httpPut(url, token, content)
            Unit
        }
    }

    override suspend fun download(file: CloudBackupFile): Result<String> = runCatching {
        withContext(Dispatchers.IO) {
            val token = accessToken()
            httpGet("https://graph.microsoft.com/v1.0/me/drive/items/${file.id}/content", token)
        }
    }

    private suspend fun accessToken(): String {
        val app = client()
        val account = app.currentAccount?.currentAccount ?: throw NotSignedInException()
        return app.acquireTokenSilent(SCOPES, account.authority).accessToken
    }

    /** Creates the "MedRecall Backups" OneDrive folder the first time it's needed. */
    private fun ensureFolder(token: String) {
        if (cachedFolderChecked) return
        val checkUrl = "$GRAPH_ROOT:/${encodePathSegment(FOLDER_NAME)}"
        val connection = (URL(checkUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $token")
        }
        when (val code = connection.responseCode) {
            in 200..299 -> Unit // folder already exists
            404 -> {
                val body = JSONObject().apply {
                    put("name", FOLDER_NAME)
                    put("folder", JSONObject())
                    put("@microsoft.graph.conflictBehavior", "rename")
                }
                httpPostJson("$GRAPH_ROOT/children", token, body.toString())
            }
            else -> {
                val errorBody = connection.errorStream
                    ?.let { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).readText() }
                    .orEmpty()
                throw IOException("OneDrive request failed ($code): $errorBody")
            }
        }
        cachedFolderChecked = true
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

    private fun httpPut(url: String, token: String, body: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        }
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        return connection.readBodyOrThrow()
    }

    private fun HttpURLConnection.readBodyOrThrow(): String {
        val code = responseCode
        val stream = if (code in 200..299) inputStream else errorStream
        val body = stream?.let { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).readText() }.orEmpty()
        if (code !in 200..299) {
            throw IOException("OneDrive request failed ($code): $body")
        }
        return body
    }

    /** URLEncoder is form-encoding (space -> '+'), which is wrong inside a URL
     *  path -- this fixes that up to proper path percent-encoding. */
    private fun encodePathSegment(segment: String): String =
        URLEncoder.encode(segment, "UTF-8").replace("+", "%20")

    companion object {
        private val SCOPES = arrayOf("Files.ReadWrite")
        private const val GRAPH_ROOT = "https://graph.microsoft.com/v1.0/me/drive/root"
        private const val FOLDER_NAME = "MedRecall Backups"
        const val FILE_PREFIX = "medrecall_backup_"
    }
}
