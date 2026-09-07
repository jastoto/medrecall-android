package com.asok.medrecall.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Single-profile design: MedRecall+ tracks one person (you). There is always
 * exactly one row in this table, with a fixed id of 1 — upsert it to update
 * rather than inserting a second row.
 */
@Entity(tableName = "patients")
data class Patient(
    @PrimaryKey val id: Int = 1,
    val name: String,
    val addressLine: String? = null,
    val city: String? = null,
    val state: String? = null,
    val zip: String? = null,
    val dateOfBirth: Long? = null,
    val bloodType: String? = null,
    val allergies: String? = null,
    val emergencyContactName: String? = null,
    val emergencyContactRelationship: String? = null,
    val emergencyContactPhone: String? = null,
    val notes: String? = null
)
