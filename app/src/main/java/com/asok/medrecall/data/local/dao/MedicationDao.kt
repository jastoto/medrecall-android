package com.asok.medrecall.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.asok.medrecall.data.local.Medication
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {
    @Insert
    suspend fun insert(medication: Medication): Long

    @Update
    suspend fun update(medication: Medication)

    @Delete
    suspend fun delete(medication: Medication)

    @Query("SELECT * FROM medications ORDER BY active DESC, name ASC")
    fun observeAll(): Flow<List<Medication>>

    @Query("SELECT * FROM medications WHERE active = 1 ORDER BY name ASC")
    fun observeActive(): Flow<List<Medication>>

    @Query("SELECT * FROM medications WHERE prescribingDoctorId = :doctorId ORDER BY name ASC")
    fun observeByDoctor(doctorId: Int): Flow<List<Medication>>

    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun getById(id: Int): Medication?

    @Query("SELECT * FROM medications ORDER BY name ASC")
    suspend fun getAllOnce(): List<Medication>
}
