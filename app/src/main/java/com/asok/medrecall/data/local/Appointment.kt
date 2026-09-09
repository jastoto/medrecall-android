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
    val completed: Boolean = false,
    // Id of the event this appointment was synced to in the device's chosen
    // calendar (Settings > Calendar Sync) -- see data/calendar/DeviceCalendarManager.kt.
    // Null means never synced (or sync is off). Cleared for every appointment
    // whenever the user switches to a different sync calendar.
    val deviceCalendarEventId: Long? = null
)
