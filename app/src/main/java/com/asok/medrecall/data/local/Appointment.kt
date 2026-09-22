package com.asok.medrecall.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "appointments")
data class Appointment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val dateTime: Long,
    val reason: String,
    val doctorId: Int? = null,
    val location: String? = null,
    val notes: String? = null,
    val completed: Boolean = false,
    // Set once this appointment has been pushed to Google Calendar (see
    // data/calendar/), so later edits update the same event instead of
    // creating a duplicate, and deleting the appointment removes the
    // right event. Null means either Calendar isn't connected, or this
    // appointment hasn't synced yet.
    val googleCalendarEventId: String? = null,
    // Set when this appointment is synced via the on-device Calendar
    // Provider (see data/calendar/DeviceCalendarManager.kt / the Settings >
    // Calendar Sync picker). Kept alongside googleCalendarEventId for now;
    // the two sync paths aren't unified yet (see calendar-two-way-sync-scope.md).
    val deviceCalendarEventId: Long? = null,
    // Last time THIS appointment was edited locally in MedRecall+ (set on
    // every save in AppointmentRepository). Compared against a Google
    // Calendar event's own "updated" timestamp during reconciliation
    // (see GoogleCalendarSyncManager.reconcileFromGoogle) to decide whose
    // edit wins -- see calendar-two-way-sync-scope.md.
    val lastModifiedAt: Long = 0L,
    // True when the linked Google Calendar event was found deleted on
    // Google's side during a reconciliation pass. Rather than silently
    // deleting the local appointment too, this flags it for a one-time
    // confirmation shown on the Appointments screen (Asok's call, see
    // calendar-two-way-sync-scope.md §5.1) -- medical appointment data is
    // not something to auto-delete on a sync signal that could be a glitch.
    val pendingGoogleDeletion: Boolean = false
)
