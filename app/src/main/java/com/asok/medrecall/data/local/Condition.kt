package com.asok.medrecall.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * status is stored as its plain display label ("Active" / "Monitoring" /
 * "Resolved") rather than an enum ordinal -- matching how Medication keeps
 * dosage/schedule as flexible free-text elsewhere in this app. iconKey is a
 * lookup key into ui/conditions/ConditionIcons.kt's icon list.
 */
@Entity(tableName = "conditions")
data class Condition(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val status: String = "Active",
    val iconKey: String = "assignment",
    val notes: String? = null
)
