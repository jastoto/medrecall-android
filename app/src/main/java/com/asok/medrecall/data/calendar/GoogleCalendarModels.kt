package com.asok.medrecall.data.calendar

/** One calendar from the signed-in Google account's calendar list, for the picker in Settings > Account. */
data class GoogleCalendarInfo(
    val id: String,
    val name: String,
    val isPrimary: Boolean
)
