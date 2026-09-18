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
        prefs.edit().remove(KEY_ACCOUNT).remove(KEY_CALENDAR_ID).remove(KEY_CALENDAR_NAME).apply()
    }

    fun selectedCalendarId(): String? = prefs.getString(KEY_CALENDAR_ID, null)

    fun selectedCalendarName(): String? = prefs.getString(KEY_CALENDAR_NAME, null)

    fun selectCalendar(calendar: GoogleCalendarInfo) {
        prefs.edit()
            .putString(KEY_CALENDAR_ID, calendar.id)
            .putString(KEY_CALENDAR_NAME, calendar.name)
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
        private const val CALENDAR_URL = "https://www.googleapis.com/calendar/v3/calendars"
        private const val DEFAULT_DURATION_MINUTES = 30L
        const val READONLY_SCOPE = "https://www.googleapis.com/auth/calendar.readonly"
        const val EVENTS_SCOPE = "https://www.googleapis.com/auth/calendar.events"
    }
}

class NotSignedInException : Exception("Not signed in to Google Calendar.")
