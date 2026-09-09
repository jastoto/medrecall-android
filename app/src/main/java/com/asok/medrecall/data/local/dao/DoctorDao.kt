package com.asok.medrecall.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.asok.medrecall.data.local.Doctor
import kotlinx.coroutines.flow.Flow

@Dao
interface DoctorDao {
    @Insert
    suspend fun insert(doctor: Doctor): Long

    @Update
    suspend fun update(doctor: Doctor)

    @Delete
    suspend fun delete(doctor: Doctor)

    @Query("SELECT * FROM doctors ORDER BY name ASC")
    fun observeAll(): Flow<List<Doctor>>

    @Query("SELECT * FROM doctors WHERE id = :id")
    suspend fun getById(id: Int): Doctor?

    @Query("SELECT * FROM doctors ORDER BY name ASC")
    suspend fun getAllOnce(): List<Doctor>

    /** Bulk-inserts, replacing on id conflict -- used by BackupImporter after deleteAll() to reload a section from a Drive backup with its original ids intact. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllForRestore(items: List<Doctor>)

    /** Clears this table -- used by BackupImporter for a REPLACE-mode restore before reloading from a Drive backup. */
    @Query("DELETE FROM doctors")
    suspend fun deleteAll()
}
