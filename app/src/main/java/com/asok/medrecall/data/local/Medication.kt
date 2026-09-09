package com.asok.medrecall.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medications")
data class Medication(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val dosage: String? = null,
    val schedule: String? = null,
    val prescribingDoctorId: Int? = null,
    val conditionId: Int? = null,
    val startDate: Long? = null,
    val endDate: Long? = null,
    val active: Boolean = true,
    val notes: String? = null
)
