package com.asok.medrecall.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.asok.medrecall.data.local.Patient
import kotlinx.coroutines.flow.Flow

@Dao
interface PatientDao {
    @Upsert
    suspend fun upsert(patient: Patient)

    @Query("SELECT * FROM patients WHERE id = 1")
    fun observe(): Flow<Patient?>

    @Query("SELECT * FROM patients WHERE id = 1")
    suspend fun getOnce(): Patient?
}
