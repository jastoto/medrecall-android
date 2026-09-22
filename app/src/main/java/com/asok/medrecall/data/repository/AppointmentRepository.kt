package com.asok.medrecall.data.repository

import com.asok.medrecall.data.local.Appointment
import com.asok.medrecall.data.local.Doctor
import com.asok.medrecall.data.local.dao.AppointmentDao
import com.asok.medrecall.data.local.dao.DoctorDao
import kotlinx.coroutines.flow.Flow

class AppointmentRepository(
    private val appointmentDao: AppointmentDao,
    private val doctorDao: DoctorDao
) {
    fun observeAppointments(): Flow<List<Appointment>> = appointmentDao.observeAll()

    fun observeDoctors(): Flow<List<Doctor>> = doctorDao.observeAll()

    /** Backs the one-time "removed from Google Calendar" confirmation -- see AppointmentDao.observePendingGoogleDeletions. */
    fun observePendingGoogleDeletions(): Flow<List<Appointment>> = appointmentDao.observePendingGoogleDeletions()

    suspend fun getAppointment(id: Int): Appointment? = appointmentDao.getById(id)

    /**
     * @return [appointment] with its final id filled in (Room only assigns
     * one on insert) and [Appointment.lastModifiedAt] stamped to now --
     * every local save counts as "the newest edit" for the two-way sync
     * reconciliation's most-recent-edit-wins rule (see
     * GoogleCalendarSyncManager.reconcileFromGoogle).
     */
    suspend fun save(appointment: Appointment): Appointment {
        val stamped = appointment.copy(lastModifiedAt = System.currentTimeMillis())
        return if (stamped.id == 0) {
            val newId = appointmentDao.insert(stamped)
            stamped.copy(id = newId.toInt())
        } else {
            appointmentDao.update(stamped)
            stamped
        }
    }

    suspend fun delete(appointment: Appointment) = appointmentDao.delete(appointment)

    suspend fun addDoctor(doctor: Doctor): Long = doctorDao.insert(doctor)

    /** See AppointmentDao.clearAllDeviceCalendarEventIds -- used when switching or turning off calendar sync. */
    suspend fun detachAllFromDeviceCalendar() = appointmentDao.clearAllDeviceCalendarEventIds()

    /**
     * User confirmed "yes, remove it here too" for an appointment whose
     * Google Calendar event was deleted on Google's side.
     */
    suspend fun confirmGoogleDeletion(appointment: Appointment) = appointmentDao.delete(appointment)

    /**
     * User said "no, keep it" for an appointment flagged as removed from
     * Google Calendar. Un-links it from that (now-gone) event rather than
     * leaving pendingGoogleDeletion set, so it stops being flagged and a
     * later edit/save creates a fresh Google Calendar event for it instead
     * of trying to update one that no longer exists.
     */
    suspend fun dismissGoogleDeletion(appointment: Appointment) {
        appointmentDao.update(
            appointment.copy(pendingGoogleDeletion = false, googleCalendarEventId = null)
        )
    }
}
