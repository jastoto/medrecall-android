package com.asok.medrecall.data.recordings

/** Where a finished visit recording can be saved -- shown as checkboxes in RecordVisitScreen's destination picker, punch items #37/#39. */
enum class RecordingDestination(val label: String) {
    LOCAL("This phone"),
    GOOGLE_DRIVE("Google Drive"),
    ONE_DRIVE("OneDrive")
}

/** Outcome of trying to save/upload the recording to one [RecordingDestination]. */
sealed class RecordingSaveResult {
    abstract val destination: RecordingDestination

    data class Success(override val destination: RecordingDestination, val locationLabel: String) : RecordingSaveResult()
    data class Failure(override val destination: RecordingDestination, val message: String) : RecordingSaveResult()
}
