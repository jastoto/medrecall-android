package com.asok.medrecall.data.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.util.TimeZone

/** One calendar already present on the phone (from CalendarContract.Calendars). */
data class DeviceCalendarInfo(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val accountType: String
) {
    /** Friendly grouping label shown in the picker -- Google / Office / a local-only calendar. */
    val providerLabel: String
        get() = when {
            accountType.equals("com.google", ignoreCase = true) -> "Google"
            accountType.contains("exchange", ignoreCase = true) ||
                accountType.contains("office365", ignoreCase = true) ||
                accountType.contains("outlook", ignoreCase = true) -> "Office / Outlook"
            accountType.equals(CalendarContract.ACCOUNT_TYPE_LOCAL, ignoreCase = true) -> "On This Device"
            else -> accountName.ifBlank { "Other" }
        }
}

/**
 * Thin wrapper around Android's own Calendar Provider (CalendarContract).
 * This is how MedRecall syncs appointments to whichever calendar the user
 * picks in Settings > Calendar Sync -- Google, Office/Outlook (added as an
 * Exchange account), or a local-only calendar -- without MedRecall doing any
 * sign-in or network access of its own. Whatever's already set up on the
 * phone in Settings > Accounts (or a local calendar in the Calendar app)
 * shows up here automatically, synced by the OS as usual.
 */
object DeviceCalendarManager {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Every calendar MedRecall is allowed to write events into. Empty if permission isn't granted. */
    fun queryAvailableCalendars(context: Context): List<DeviceCalendarInfo> {
        if (!hasPermission(context)) return emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
        )
        val results = mutableListOf<DeviceCalendarInfo>()
        runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI, projection, null, null,
                "${CalendarContract.Calendars.ACCOUNT_NAME} ASC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
                val nameCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                val accountNameCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)
                val accountTypeCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_TYPE)
                val accessCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)
                while (cursor.moveToNext()) {
                    // Only calendars MedRecall can actually write appointments into.
                    if (cursor.getInt(accessCol) < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) continue
                    results.add(
                        DeviceCalendarInfo(
                            id = cursor.getLong(idCol),
                            displayName = cursor.getString(nameCol) ?: "(unnamed calendar)",
                            accountName = cursor.getString(accountNameCol) ?: "",
                            accountType = cursor.getString(accountTypeCol) ?: ""
                        )
                    )
                }
            }
        }
        return results
    }

    /** Inserts a new event; returns its event id, or null if the write failed or permission is missing. */
    fun insertEvent(
        context: Context,
        calendarId: Long,
        title: String,
        description: String?,
        location: String?,
        beginMillis: Long,
        endMillis: Long
    ): Long? {
        if (!hasPermission(context)) return null
        val values = eventValues(calendarId, title, description, location, beginMillis, endMillis)
        return runCatching {
            context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)?.let { ContentUris.parseId(it) }
        }.getOrNull()
    }

    /** Updates an existing event by id. Returns true on success, false if it no longer exists or the write failed. */
    fun updateEvent(
        context: Context,
        eventId: Long,
        calendarId: Long,
        title: String,
        description: String?,
        location: String?,
        beginMillis: Long,
        endMillis: Long
    ): Boolean {
        if (!hasPermission(context)) return false
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        val values = eventValues(calendarId, title, description, location, beginMillis, endMillis)
        return runCatching { context.contentResolver.update(uri, values, null, null) > 0 }.getOrDefault(false)
    }

    /** Deletes an event by id. Best-effort -- failures are swallowed since this is just cleanup. */
    fun deleteEvent(context: Context, eventId: Long) {
        if (!hasPermission(context)) return
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        runCatching { context.contentResolver.delete(uri, null, null) }
    }

    private fun eventValues(
        calendarId: Long,
        title: String,
        description: String?,
        location: String?,
        beginMillis: Long,
        endMillis: Long
    ) = ContentValues().apply {
        put(CalendarContract.Events.CALENDAR_ID, calendarId)
        put(CalendarContract.Events.TITLE, title)
        put(CalendarContract.Events.DESCRIPTION, description ?: "")
        put(CalendarContract.Events.EVENT_LOCATION, location ?: "")
        put(CalendarContract.Events.DTSTART, beginMillis)
        put(CalendarContract.Events.DTEND, endMillis)
        put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
    }
}
