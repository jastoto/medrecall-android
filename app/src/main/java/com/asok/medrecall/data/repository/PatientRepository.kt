package com.asok.medrecall.data.repository

import com.asok.medrecall.data.local.Condition
import com.asok.medrecall.data.local.Patient
import com.asok.medrecall.data.local.dao.ConditionDao
import com.asok.medrecall.data.local.dao.PatientDao
import kotlinx.coroutines.flow.Flow

class PatientRepository(
    private val patientDao: PatientDao,
    private val conditionDao: ConditionDao
) {
    fun observePatient(): Flow<Patient?> = patientDao.observe()

    fun observeConditions(): Flow<List<Condition>> = conditionDao.observeAll()

    suspend fun save(patient: Patient) = patientDao.upsert(patient)
}
