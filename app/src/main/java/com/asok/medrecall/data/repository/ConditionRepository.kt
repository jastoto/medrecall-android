package com.asok.medrecall.data.repository

import com.asok.medrecall.data.local.Condition
import com.asok.medrecall.data.local.dao.ConditionDao
import kotlinx.coroutines.flow.Flow

class ConditionRepository(private val conditionDao: ConditionDao) {
    fun observeConditions(): Flow<List<Condition>> = conditionDao.observeAll()

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
