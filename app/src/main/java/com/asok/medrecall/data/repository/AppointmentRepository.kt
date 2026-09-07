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

    suspend fun save(appointment: Appointment) {
        if (appointment.id == 0) {
            appointmentDao.insert(appointment)
        } else {
            appointmentDao.update(appointment)
        }
    }

    suspend fun delete(appointment: Appointment) = appointmentDao.delete(appointment)

    suspend fun addDoctor(doctor: Doctor): Long = doctorDao.insert(doctor)
}
