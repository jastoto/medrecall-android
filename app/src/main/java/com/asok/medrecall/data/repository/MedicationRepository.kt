package com.asok.medrecall.data.repository

import com.asok.medrecall.data.local.Doctor
import com.asok.medrecall.data.local.Medication
import com.asok.medrecall.data.local.dao.DoctorDao
import com.asok.medrecall.data.local.dao.MedicationDao
import kotlinx.coroutines.flow.Flow

class MedicationRepository(
    private val medicationDao: MedicationDao,
    private val doctorDao: DoctorDao
) {
    fun observeMedications(): Flow<List<Medication>> = medicationDao.observeAll()

    fun observeDoctors(): Flow<List<Doctor>> = doctorDao.observeAll()

    suspend fun getMedication(id: Int): Medication? = medicationDao.getById(id)

    suspend fun save(medication: Medication) {
        if (medication.id == 0) {
            medicationDao.insert(medication)
        } else {
            medicationDao.update(medication)
        }
    }

    suspend fun delete(medication: Medication) = medicationDao.delete(medication)

    suspend fun addDoctor(doctor: Doctor): Long = doctorDao.insert(doctor)
}
