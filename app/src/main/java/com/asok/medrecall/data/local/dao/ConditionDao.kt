package com.asok.medrecall.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.asok.medrecall.data.local.Condition
import kotlinx.coroutines.flow.Flow

@Dao
interface ConditionDao {
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
}
