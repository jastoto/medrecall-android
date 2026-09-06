package com.asok.medrecall.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "appointments")
data class Appointment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val dateTime: Long,
    val reason: String,
    val doctorId: Int? = null,
    val location: String? = null,
    val notes: String? = null,
    val completed: Boolean = false
)
