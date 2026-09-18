package com.asok.medrecall.data.calendar

import com.asok.medrecall.data.local.Appointment
import com.asok.medrecall.data.local.dao.AppointmentDao
import com.asok.medrecall.data.local.dao.DoctorDao

/**
 * Orchestrates one-way sync of Appointments -> Google Calendar. Called
 * from [com.asok.medrecall.ui.appointments.AppointmentsViewModel] as an
 * internal side effect of saving/deleting an appointment, so screens
 * that edit appointments don't need to know Calendar sync exists at all.
 *
 * Sync is best-effort: if the user hasn't connected a calendar yet, or a
 * sync call fails (no network, revoked access, etc.), failures are
 * swallowed here rather than surfaced -- an appointment save/delete
 * should never fail just because Calendar sync couldn't complete.
 */
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
        }
    }

    suspend fun onAppointmentDeleted(appointment: Appointment) {
        val eventId = appointment.googleCalendarEventId ?: return
        if (!calendarService.isReadyToSync()) return
        calendarService.deleteEvent(eventId)
    }

    /** Retroactively pushes every existing appointment -- run once, right after a calendar is first selected. */
    suspend fun syncAllExisting(): Result<Int> = runCatching {
        val appointments = appointmentDao.getAllOnce()
        appointments.forEach { onAppointmentSaved(it) }
        appointments.size
    }
}
