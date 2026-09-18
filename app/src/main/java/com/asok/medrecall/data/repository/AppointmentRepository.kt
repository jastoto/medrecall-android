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

    /** @return [appointment] with its final id filled in (Room only assigns one on insert). */
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

    /** See AppointmentDao.clearAllDeviceCalendarEventIds -- used when switching or turning off calendar sync. */
    suspend fun detachAllFromDeviceCalendar() = appointmentDao.clearAllDeviceCalendarEventIds()
}
