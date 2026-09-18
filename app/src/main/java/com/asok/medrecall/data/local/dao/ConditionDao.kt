package com.asok.medrecall.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.asok.medrecall.data.local.Condition
import kotlinx.coroutines.flow.Flow

@Dao
interface ConditionDao {
    // Restore (Backup & Restore, "replace everything"): wipes the table
    // before reinserting the backup's rows.
    @Query("DELETE FROM conditions")
    suspend fun deleteAll()

    @Insert
    suspend fun insert(condition: Condition): Long

    @Update
    suspend fun update(condition: Condition)

    @Delete
    suspend fun delete(condition: Condition)

    @Query("SELECT * FROM conditions ORDER BY name ASC")
    fun observeAll(): Flow<List<Condition>>

    @Query("SELECT * FROM conditions WHERE id = :id")
    suspend fun getById(id: Int): Condition?

    @Query("SELECT * FROM conditions ORDER BY name ASC")
    suspend fun getAllOnce(): List<Condition>

    /** Bulk-inserts, replacing on id conflict -- used by BackupImporter after deleteAll() to reload a section from a Drive backup with its original ids intact. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllForRestore(items: List<Condition>)
}
