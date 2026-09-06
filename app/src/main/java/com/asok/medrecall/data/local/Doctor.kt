package com.asok.medrecall.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "doctors")
data class Doctor(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val specialty: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val notes: String? = null
)
