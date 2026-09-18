package com.asok.medrecall.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.asok.medrecall.data.local.VitalReading
import kotlinx.coroutines.flow.Flow

@Dao
interface VitalReadingDao {
    // Restore (Backup & Restore, "replace everything"): wipes the table
    // before reinserting the backup's rows.
    @Query("DELETE FROM vital_readings")
    suspend fun deleteAll()

    @Insert
    suspend fun insert(reading: VitalReading): Long

    @Update
    suspend fun update(reading: VitalReading)

    @Delete
    suspend fun delete(reading: VitalReading)

    @Query("SELECT * FROM vital_readings WHERE type = :type ORDER BY recordedAt DESC")
    fun observeByType(type: String): Flow<List<VitalReading>>

    @Query("SELECT * FROM vital_readings WHERE id = :id")
    suspend fun getById(id: Int): VitalReading?

    @Query("SELECT * FROM vital_readings ORDER BY recordedAt DESC")
    suspend fun getAllOnce(): List<VitalReading>

    /** Bulk-inserts, replacing on id conflict -- used by BackupImporter after deleteAll() to reload a section from a Drive backup with its original ids intact. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllForRestore(items: List<VitalReading>)
}
