package com.asok.medrecall.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.asok.medrecall.data.local.Appointment
import kotlinx.coroutines.flow.Flow

@Dao
interface AppointmentDao {
    @Insert
    suspend fun insert(appointment: Appointment): Long

    @Update
    suspend fun update(appointment: Appointment)

    @Delete
    suspend fun delete(appointment: Appointment)

    @Query("SELECT * FROM appointments ORDER BY dateTime ASC")
    fun observeAll(): Flow<List<Appointment>>

    @Query("SELECT * FROM appointments WHERE dateTime >= :fromMillis ORDER BY dateTime ASC")
    fun observeUpcoming(fromMillis: Long): Flow<List<Appointment>>

    @Query("SELECT * FROM appointments WHERE id = :id")
    suspend fun getById(id: Int): Appointment?

    @Query("SELECT * FROM appointments ORDER BY dateTime DESC")
    suspend fun getAllOnce(): List<Appointment>

    @Query("UPDATE appointments SET deviceCalendarEventId = :eventId WHERE id = :id")
    suspend fun updateDeviceCalendarEventId(id: Int, eventId: Long?)

    // Run when the user switches Settings > Calendar Sync to a different
    // calendar -- existing appointments stay pointed at nothing rather than
    // silently updating events that now live in the wrong (old) calendar.
    @Query("UPDATE appointments SET deviceCalendarEventId = NULL")
    suspend fun clearAllDeviceCalendarEventIds()

    /** Bulk-inserts, replacing on id conflict -- used by BackupImporter after deleteAll() to reload a section from a Drive backup with its original ids intact. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllForRestore(items: List<Appointment>)

    /** Clears this table -- used by BackupImporter for a REPLACE-mode restore before reloading from a Drive backup. */
    @Query("DELETE FROM appointments")
    suspend fun deleteAll()
}
