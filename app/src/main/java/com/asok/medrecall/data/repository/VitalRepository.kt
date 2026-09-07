package com.asok.medrecall.data.repository

import com.asok.medrecall.data.local.VitalReading
import com.asok.medrecall.data.local.dao.VitalReadingDao
import kotlinx.coroutines.flow.Flow

class VitalRepository(private val vitalReadingDao: VitalReadingDao) {
    fun observeReadings(type: String): Flow<List<VitalReading>> = vitalReadingDao.observeByType(type)

    suspend fun getReading(id: Int): VitalReading? = vitalReadingDao.getById(id)

    suspend fun save(reading: VitalReading) {
        if (reading.id == 0) {
            vitalReadingDao.insert(reading)
        } else {
            vitalReadingDao.update(reading)
        }
    }

    suspend fun delete(reading: VitalReading) = vitalReadingDao.delete(reading)
}
