package com.asok.medrecall.data.repository

import com.asok.medrecall.data.local.Condition
import com.asok.medrecall.data.local.Doctor
import com.asok.medrecall.data.local.Medication
import com.asok.medrecall.data.local.dao.ConditionDao
import com.asok.medrecall.data.local.dao.DoctorDao
import com.asok.medrecall.data.local.dao.MedicationDao
import kotlinx.coroutines.flow.Flow

class ConditionRepository(
    private val conditionDao: ConditionDao,
    private val medicationDao: MedicationDao,
    private val doctorDao: DoctorDao
) {
    fun observeConditions(): Flow<List<Condition>> = conditionDao.observeAll()

    fun observeMedications(): Flow<List<Medication>> = medicationDao.observeAll()

    fun observeDoctors(): Flow<List<Doctor>> = doctorDao.observeAll()

    suspend fun getCondition(id: Int): Condition? = conditionDao.getById(id)

    suspend fun save(condition: Condition) {
        if (condition.id == 0) {
            conditionDao.insert(condition)
        } else {
            conditionDao.update(condition)
        }
    }

    suspend fun delete(condition: Condition) = conditionDao.delete(condition)
}
