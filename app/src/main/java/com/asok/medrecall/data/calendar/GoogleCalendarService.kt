package com.asok.medrecall.data.calendar

import android.accounts.Account
import android.content.Context
import com.asok.medrecall.data.local.Appointment
import com.google.android.gms.auth.GoogleAuthUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * One-way sync destination: MedRecall+ Appointments -> Google Calendar.
 * Sign-in itself (the account picker UI) happens in
 * [com.asok.medrecall.ui.settings.AccountViewModel] via Play Services'
 * GoogleSignInClient, since that needs an Activity to launch an intent
 * from; once signed in, [onSignedIn] is called with the chosen account's
 * name and everything else here is a plain REST call to the Calendar v3
 * API using that account's OAuth token.
 *
 * This is a separate, independent sign-in from Google Drive backups
 * (data/backup/GoogleDriveBackupService.kt) -- its own SharedPreferences,
 * its own scopes, its own account state -- even though both happen to
 * use the same Google account picker UI pattern.
 *
 * Appointment.dateTime has no stored duration, so events default to a
 * 30 minute block starting at that time.
 */
class GoogleCalendarService(private val context: Context) {

    private val prefs = context.getSharedPreferences("google_calendar_sync", Context.MODE_PRIVATE)

    fun onSignedIn(accountEmail: String) {
        prefs.edit().putString(KEY_ACCOUNT, accountEmail).apply()
    }

    suspend fun isSignedIn(): Boolean = prefs.getString(KEY_ACCOUNT, null) != null

    suspend fun signedInAccountLabel(): String? = prefs.getString(KEY_ACCOUNT, null)

    suspend fun signOut() {
        prefs.edit().remove(KEY_ACCOUNT).remove(KEY_CALENDAR_ID).remove(KEY_CALENDAR_NAME).remove(KEY_SYNC_TOKEN).apply()
    }

    fun selectedCalendarId(): String? = prefs.getString(KEY_CALENDAR_ID, null)

    fun selectedCalendarName(): String? = prefs.getString(KEY_CALENDAR_NAME, null)

    fun selectCalendar(calendar: GoogleCalendarInfo) {
        prefs.edit()
            .putString(KEY_CALENDAR_ID, calendar.id)
            .putString(KEY_CALENDAR_NAME, calendar.name)
            // A sync token is scoped to one calendar -- switching calendars
            // invalidates whatever incremental-sync cursor we had.
            .remove(KEY_SYNC_TOKEN)
            .apply()
    }

    /** True once both a Google account and a specific calendar have been chosen -- the point at which sync should run. */
    suspend fun isReadyToSync(): Boolean = isSignedIn() && selectedCalendarId() != null

    suspend fun listCalendars(): Result<List<GoogleCalendarInfo>> = runCatching {
        withContext(Dispatchers.IO) {
            val token = accessToken()
            val body = httpGet(
                "https://www.googleapis.com/calendar/v3/users/me/calendarList?minAccessRole=writer",
                token
            )
            val items = JSONObject(body).getJSONArray("items")
            (0 until items.length()).map { i ->
                val cal = items.getJSONObject(i)
                GoogleCalendarInfo(
                    id = cal.getString("id"),
                    name = cal.optString("summary", cal.getString("id")),
                    isPrimary = cal.optBoolean("primary", false)
                )
            }
        }
    }

    /**
     * Incremental sync: fetches every event that changed (edited, or
     * deleted -- Google reports deletions as status "cancelled") on the
     * selected calendar since the last call, using Google Calendar's
     * syncToken so this doesn't have to re-fetch and re-check every synced
     * event on every reconciliation pass. The very first call (no stored
     * token yet) establishes a baseline token without reporting any deltas
     * -- there's nothing to "change from" yet, so treating every existing
     * event as a delta on that first pass would be a false positive.
     *
     * If the stored token has expired or is otherwise rejected by Google
     * (HTTP 410), it's discarded and this falls back to establishing a
     * fresh baseline, same as a first call.
     */
    suspend fun fetchChangedEvents(): Result<List<GoogleCalendarEventDelta>> = runCatching {
        withContext(Dispatchers.IO) {
            val calendarId = selectedCalendarId() ?: throw NotReadyToSyncException()
            val token = accessToken()
            var syncToken = prefs.getString(KEY_SYNC_TOKEN, null)
            var isBaselineOnlyPass = syncToken == null
            var pageToken: String? = null
            var triedFreshBaseline = false
            val deltas = mutableListOf<GoogleCalendarEventDelta>()

            while (true) {
                val url = StringBuilder("$CALENDAR_URL/${encode(calendarId)}/events?showDeleted=true&maxResults=250")
                pageToken?.let { url.append("&pageToken=${encode(it)}") }
                syncToken?.let { url.append("&syncToken=${encode(it)}") }
                val connection = (URL(url.toString()).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Authorization", "Bearer $token")
                }
                val code = connection.responseCode
                if (code == 410) {
                    if (triedFreshBaseline) throw IOException("Google Calendar sync token repeatedly rejected")
                    triedFreshBaseline = true
                    syncToken = null
                    pageToken = null
                    isBaselineOnlyPass = true
                    deltas.clear()
                    continue
                }
                if (code !in 200..299) connection.throwForError(code)
                val body = connection.inputStream.let { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).readText() }
                val json = JSONObject(body)
                if (!isBaselineOnlyPass) {
                    val items = json.optJSONArray("items")
                    if (items != null) {
                        for (i in 0 until items.length()) {
                            val item = items.getJSONObject(i)
                            val updatedAt = item.optStringOrNull("updated")?.let { Instant.parse(it).toEpochMilli() } ?: continue
                            if (item.optString("status") == "cancelled") {
                                deltas.add(GoogleCalendarEventDelta(id = item.getString("id"), cancelled = true, updatedAtMillis = updatedAt))
                            } else {
                                val startMillis = item.optJSONObject("start")?.optStringOrNull("dateTime")?.let { Instant.parse(it).toEpochMilli() }
                                deltas.add(
                                    GoogleCalendarEventDelta(
                                        id = item.getString("id"),
                                        cancelled = false,
                                        updatedAtMillis = updatedAt,
                                        summary = item.optStringOrNull("summary"),
                                        description = item.optStringOrNull("description"),
                                        location = item.optStringOrNull("location"),
                                        startMillis = startMillis
                                    )
                                )
                            }
                        }
                    }
                }
                pageToken = json.optStringOrNull("nextPageToken")
                if (pageToken == null) {
                    json.optStringOrNull("nextSyncToken")?.let { prefs.edit().putString(KEY_SYNC_TOKEN, it).apply() }
                    break
                }
            }
            deltas
        }
    }

    suspend fun createEvent(appointment: Appointment, doctorName: String?): Result<String> = runCatching {
        withContext(Dispatchers.IO) {
            val calendarId = selectedCalendarId() ?: throw NotReadyToSyncException()
            val token = accessToken()
            val url = "$CALENDAR_URL/${encode(calendarId)}/events"
            val created = JSONObject(httpPostJson(url, token, eventJson(appointment, doctorName).toString()))
            created.getString("id")
        }
    }

    suspend fun updateEvent(eventId: String, appointment: Appointment, doctorName: String?): Result<String> = runCatching {
        withContext(Dispatchers.IO) {
            val calendarId = selectedCalendarId() ?: throw NotReadyToSyncException()
            val token = accessToken()
            val url = "$CALENDAR_URL/${encode(calendarId)}/events/${encode(eventId)}"
            val updated = JSONObject(httpPutJson(url, token, eventJson(appointment, doctorName).toString()))
            updated.getString("id")
        }
    }

    suspend fun deleteEvent(eventId: String): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val calendarId = selectedCalendarId() ?: throw NotReadyToSyncException()
            val token = accessToken()
            val url = "$CALENDAR_URL/${encode(calendarId)}/events/${encode(eventId)}"
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "DELETE"
                setRequestProperty("Authorization", "Bearer $token")
            }
            val code = connection.responseCode
            // 404/410 means the event is already gone on Google's side -- treat as done, not a failure.
            if (code !in 200..299 && code != 404 && code != 410) {
                connection.throwForError(code)
            }
        }
    }

    private fun eventJson(appointment: Appointment, doctorName: String?): JSONObject {
        val zone = ZoneId.systemDefault()
        val start = Instant.ofEpochMilli(appointment.dateTime).atZone(zone)
        val end = start.plusMinutes(DEFAULT_DURATION_MINUTES)
        val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
        val summary = if (doctorName != null) "${appointment.reason} - $doctorName" else appointment.reason
        return JSONObject().apply {
            put("summary", summary)
            appointment.location?.let { put("location", it) }
            appointment.notes?.let { put("description", it) }
            put("start", JSONObject().put("dateTime", formatter.format(start)))
            put("end", JSONObject().put("dateTime", formatter.format(end)))
        }
    }

    private fun accessToken(): String {
        val email = prefs.getString(KEY_ACCOUNT, null) ?: throw NotSignedInException()
        val account = Account(email, "com.google")
        return GoogleAuthUtil.getToken(context, account, "oauth2:$READONLY_SCOPE $EVENTS_SCOPE")
    }

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")

    private fun httpGet(url: String, token: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $token")
        }
        return connection.readBodyOrThrow()
    }

    private fun httpPostJson(url: String, token: String, jsonBody: String): String =
        httpSendJson(url, "POST", token, jsonBody)

    private fun httpPutJson(url: String, token: String, jsonBody: String): String =
        httpSendJson(url, "PUT", token, jsonBody)

    private fun httpSendJson(url: String, method: String, token: String, jsonBody: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        }
        connection.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
        return connection.readBodyOrThrow()
    }

    private fun HttpURLConnection.readBodyOrThrow(): String {
        val code = responseCode
        if (code !in 200..299) throwForError(code)
        return inputStream?.let { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).readText() }.orEmpty()
    }

    private fun HttpURLConnection.throwForError(code: Int): Nothing {
        val body = errorStream?.let { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).readText() }.orEmpty()
        throw IOException("Google Calendar request failed ($code): $body")
    }

    class NotReadyToSyncException : Exception("No Google Calendar has been selected to sync to yet.")

    companion object {
        private const val KEY_ACCOUNT = "account_email"
        private const val KEY_CALENDAR_ID = "calendar_id"
        private const val KEY_CALENDAR_NAME = "calendar_name"
        private const val KEY_SYNC_TOKEN = "calendar_sync_token"
        private const val CALENDAR_URL = "https://www.googleapis.com/calendar/v3/calendars"
        private const val DEFAULT_DURATION_MINUTES = 30L
        const val READONLY_SCOPE = "https://www.googleapis.com/auth/calendar.readonly"
        const val EVENTS_SCOPE = "https://www.googleapis.com/auth/calendar.events"
    }
}

class NotSignedInException : Exception("Not signed in to Google Calendar.")

/** org.json.JSONObject.optString() returns "" for a missing/null key by default -- this distinguishes "absent" from "empty string". */
private fun JSONObject.optStringOrNull(name: String): String? = if (has(name) && !isNull(name)) getString(name) else null

