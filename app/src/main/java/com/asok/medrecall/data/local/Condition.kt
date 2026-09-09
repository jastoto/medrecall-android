package com.asok.medrecall.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * status is stored as its plain display label ("Active" / "Monitoring" /
 * "Resolved") rather than an enum ordinal -- matching how Medication keeps
 * dosage/schedule as flexible free-text elsewhere in this app. iconKey is a
 * lookup key into ui/conditions/ConditionIcons.kt's icon list.
 *
 * includedInMedicalId: whether this condition should appear in the
 * Conditions section of the Medical ID card -- defaults to true so existing
 * conditions keep showing up there until the user opts one out from the
 * Medical ID edit screen.
 *
 * doctorId: the doctor who manages/prescribes for this condition (punch
 * item #5) -- nullable since not every condition has one assigned yet.
 */
@Entity(tableName = "conditions")
data class Condition(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val status: String = "Active",
    val iconKey: String = "assignment",
    val notes: String? = null,
    val includedInMedicalId: Boolean = true,
    val doctorId: Int? = null
)
