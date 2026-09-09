package com.asok.medrecall.data.repository

import com.asok.medrecall.data.local.Condition
import com.asok.medrecall.data.local.Doctor
import com.asok.medrecall.data.local.Medication
import com.asok.medrecall.data.local.dao.ConditionDao
import com.asok.medrecall.data.local.dao.DoctorDao
import com.asok.medrecall.data.local.dao.MedicationDao
import kotlinx.coroutines.flow.Flow

class MedicationRepository(
    private val medicationDao: MedicationDao,
    private val doctorDao: DoctorDao,
    private val conditionDao: ConditionDao
) {
    fun observeMedications(): Flow<List<Medication>> = medicationDao.observeAll()

    fun observeDoctors(): Flow<List<Doctor>> = doctorDao.observeAll()

    fun observeConditions(): Flow<List<Condition>> = conditionDao.observeAll()

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

    /** Used by MedicationFormScreen's "+ Add new condition" quick-add (punch item #4). */
    suspend fun addCondition(condition: Condition): Long = conditionDao.insert(condition)
}
