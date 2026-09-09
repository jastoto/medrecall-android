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

    suspend fun getAppointment(id: Int): Appointment? = appointmentDao.getById(id)

    /**
     * Returns the saved appointment with its real id filled in (Room's
     * autoGenerate id isn't known until after insert) -- callers use this to
     * sync the just-saved appointment to the device calendar right after.
     */
    suspend fun save(appointment: Appointment): Appointment {
        return if (appointment.id == 0) {
            val newId = appointmentDao.insert(appointment)
            appointment.copy(id = newId.toInt())
        } else {
            appointmentDao.update(appointment)
            appointment
        }
    }

    suspend fun delete(appointment: Appointment) = appointmentDao.delete(appointment)

    suspend fun addDoctor(doctor: Doctor): Long = doctorDao.insert(doctor)

    suspend fun updateDeviceCalendarEventId(id: Int, eventId: Long?) =
        appointmentDao.updateDeviceCalendarEventId(id, eventId)

    /** See AppointmentDao.clearAllDeviceCalendarEventIds -- used when the sync calendar changes. */
    suspend fun detachAllFromDeviceCalendar() = appointmentDao.clearAllDeviceCalendarEventIds()
}
