package com.asok.medrecall.data.repository

import com.asok.medrecall.data.local.Condition
import com.asok.medrecall.data.local.Medication
import com.asok.medrecall.data.local.Patient
import com.asok.medrecall.data.local.dao.ConditionDao
import com.asok.medrecall.data.local.dao.MedicationDao
import com.asok.medrecall.data.local.dao.PatientDao
import kotlinx.coroutines.flow.Flow

class PatientRepository(
    private val patientDao: PatientDao,
    private val conditionDao: ConditionDao,
    private val medicationDao: MedicationDao
) {
    fun observePatient(): Flow<Patient?> = patientDao.observe()

    fun observeConditions(): Flow<List<Condition>> = conditionDao.observeAll()

    fun observeActiveMedications(): Flow<List<Medication>> = medicationDao.observeActive()

    suspend fun save(patient: Patient) = patientDao.upsert(patient)

    suspend fun updateCondition(condition: Condition) = conditionDao.update(condition)
}
