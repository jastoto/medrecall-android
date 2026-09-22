package com.asok.medrecall.data.calendar

/** One calendar from the signed-in Google account's calendar list, for the picker in Settings > Account. */
data class GoogleCalendarInfo(
    val id: String,
    val name: String,
    val isPrimary: Boolean
)

/**
 * One changed event returned by [GoogleCalendarService.fetchChangedEvents]
 * since the last reconciliation pass -- either an edit (cancelled = false)
 * or a deletion (cancelled = true, in which case only [id] and
 * [updatedAtMillis] are meaningful). See calendar-two-way-sync-scope.md.
 */
data class GoogleCalendarEventDelta(
    val id: String,
    val cancelled: Boolean,
    val updatedAtMillis: Long,
    val summary: String? = null,
    val description: String? = null,
    val location: String? = null,
    val startMillis: Long? = null
)
