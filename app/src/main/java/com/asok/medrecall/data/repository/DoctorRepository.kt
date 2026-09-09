package com.asok.medrecall.data.repository

import com.asok.medrecall.data.local.Appointment
import com.asok.medrecall.data.local.Condition
import com.asok.medrecall.data.local.Doctor
import com.asok.medrecall.data.local.dao.AppointmentDao
import com.asok.medrecall.data.local.dao.ConditionDao
import com.asok.medrecall.data.local.dao.DoctorDao
import kotlinx.coroutines.flow.Flow

class DoctorRepository(
    private val doctorDao: DoctorDao,
    private val appointmentDao: AppointmentDao,
    private val conditionDao: ConditionDao
) {
    fun observeDoctors(): Flow<List<Doctor>> = doctorDao.observeAll()

    fun observeUpcomingAppointments(fromMillis: Long): Flow<List<Appointment>> =
        appointmentDao.observeUpcoming(fromMillis)

    /** Used by DoctorsScreen (punch item #7) to show which conditions each doctor manages. */
    fun observeConditions(): Flow<List<Condition>> = conditionDao.observeAll()

    suspend fun getDoctor(id: Int): Doctor? = doctorDao.getById(id)

    suspend fun save(doctor: Doctor) {
        if (doctor.id == 0) {
            doctorDao.insert(doctor)
        } else {
            doctorDao.update(doctor)
        }
    }

    suspend fun delete(doctor: Doctor) = doctorDao.delete(doctor)
}
