package com.asok.medrecall.data.calendar

import com.asok.medrecall.data.local.Appointment
import com.asok.medrecall.data.local.dao.AppointmentDao
import android.util.Log
import com.asok.medrecall.data.local.dao.DoctorDao

/**
 * Orchestrates two-way sync between Appointments and Google Calendar --
 * MedRecall+ -> Google on every save/delete (below), and Google -> MedRecall+
 * on a periodic reconciliation pass (see [reconcileFromGoogle],
 * CalendarReconciliationWorker, CalendarSyncScheduler). Called from
 * [com.asok.medrecall.ui.appointments.AppointmentsViewModel] as an internal
 * side effect of saving/deleting an appointment, so screens that edit
 * appointments don't need to know Calendar sync exists at all.
 *
 * Sync is best-effort: if the user hasn't connected a calendar yet, or a
 * sync call fails (no network, revoked access, etc.), failures are
 * swallowed here rather than surfaced -- an appointment save/delete
 * should never fail just because Calendar sync couldn't complete.
 *
 * Two-way sync only ever applies to appointments MedRecall+ itself created
 * (tracked via [Appointment.googleCalendarEventId]) -- see
 * calendar-two-way-sync-scope.md §3. A Google Calendar event MedRecall+
 * didn't create is never imported as a new appointment.
 */
private const val TAG = "GoogleCalendarSync"

class GoogleCalendarSyncManager(
    private val calendarService: GoogleCalendarService,
    private val appointmentDao: AppointmentDao,
    private val doctorDao: DoctorDao
) {

    suspend fun onAppointmentSaved(appointment: Appointment) {
        if (!calendarService.isReadyToSync()) return
        val doctorName = appointment.doctorId?.let { doctorDao.getById(it)?.name }
        val existingEventId = appointment.googleCalendarEventId
        val result = if (existingEventId != null) {
            calendarService.updateEvent(existingEventId, appointment, doctorName)
        } else {
            calendarService.createEvent(appointment, doctorName)
        }
        result.onSuccess { eventId ->
            if (eventId != existingEventId) {
                appointmentDao.update(appointment.copy(googleCalendarEventId = eventId))
            }
        }.onFailure {
            Log.w(TAG, "Failed to push appointment ${appointment.id} to Google Calendar", it)
        }
    }

    suspend fun onAppointmentDeleted(appointment: Appointment) {
        val eventId = appointment.googleCalendarEventId ?: return
        if (!calendarService.isReadyToSync()) return
        calendarService.deleteEvent(eventId).onFailure {
            Log.w(TAG, "Failed to delete Google Calendar event $eventId for appointment ${appointment.id}", it)
        }
    }

    /** Retroactively pushes every existing appointment -- run once, right after a calendar is first selected. */
    suspend fun syncAllExisting(): Result<Int> = runCatching {
        val appointments = appointmentDao.getAllOnce()
        appointments.forEach { onAppointmentSaved(it) }
        appointments.size
    }

    /**
     * Pulls in changes made directly in Google Calendar -- run periodically
     * (CalendarReconciliationWorker) and on app foreground/resume. Only
     * touches appointments MedRecall+ already linked to a Google event.
     *
     * Conflict rule: most-recent-edit-wins, comparing Google's own
     * "updated" timestamp for the event against [Appointment.lastModifiedAt]
     * -- if Google's edit isn't newer than our last local edit, it's
     * ignored (our local edit already "wins" that comparison, whether it
     * happened before or after Google's, since anything we push updates
     * Google's timestamp to roughly our own save time too).
     *
     * A deletion on Google's side is never applied automatically -- see
     * calendar-two-way-sync-scope.md §5.1 -- instead the appointment is
     * flagged via [Appointment.pendingGoogleDeletion] for a one-time
     * confirmation on the Appointments screen.
     *
     * @return how many local appointments were changed by this pass.
     */
    suspend fun reconcileFromGoogle(): Result<Int> = runCatching {
        if (!calendarService.isReadyToSync()) return@runCatching 0
        val deltas = calendarService.fetchChangedEvents()
            .onFailure { Log.w(TAG, "Failed to fetch changed Google Calendar events", it) }
            .getOrThrow()
        if (deltas.isEmpty()) return@runCatching 0

        val trackedByEventId = appointmentDao.getAllOnce()
            .filter { it.googleCalendarEventId != null }
            .associateBy { it.googleCalendarEventId }

        var changedCount = 0
        for (delta in deltas) {
            val appointment = trackedByEventId[delta.id] ?: continue

            if (delta.cancelled) {
                if (!appointment.pendingGoogleDeletion) {
                    appointmentDao.update(appointment.copy(pendingGoogleDeletion = true))
                    changedCount++
                }
                continue
            }

            if (delta.updatedAtMillis <= appointment.lastModifiedAt) continue // our local edit wins

            val doctorName = appointment.doctorId?.let { doctorDao.getById(it)?.name }
            // GoogleCalendarService.eventJson bakes the doctor's name into the
            // event title as "reason - doctorName" -- strip that back off
            // when it's still the same doctor, so an edit made purely to the
            // Google event's title doesn't duplicate the doctor's name.
            val pulledReason = delta.summary?.let { summary ->
                if (doctorName != null && summary.endsWith(" - $doctorName")) summary.removeSuffix(" - $doctorName") else summary
            } ?: appointment.reason

            appointmentDao.update(
                appointment.copy(
                    reason = pulledReason,
                    location = delta.location ?: appointment.location,
                    notes = delta.description ?: appointment.notes,
                    dateTime = delta.startMillis ?: appointment.dateTime,
                    lastModifiedAt = delta.updatedAtMillis
                )
            )
            changedCount++
        }
        changedCount
    }
}
