package com.asok.medrecall.data.calendar

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.asok.medrecall.data.local.MedRecallDatabase

/**
 * Runs [GoogleCalendarSyncManager.reconcileFromGoogle] in the background --
 * enqueued periodically and on app foreground/resume by
 * [CalendarSyncScheduler]. See calendar-two-way-sync-scope.md.
 */
class CalendarReconciliationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val db = MedRecallDatabase.getInstance(applicationContext)
        val syncManager = GoogleCalendarSyncManager(
            GoogleCalendarService(applicationContext),
            db.appointmentDao(),
            db.doctorDao()
        )
        // Best-effort, same philosophy as the rest of Calendar sync (see
        // GoogleCalendarSyncManager's doc comment) -- a failed pass (offline,
        // revoked access, no calendar selected yet) just retries at the
        // next scheduled run rather than surfacing anywhere.
        val result = syncManager.reconcileFromGoogle()
        return if (result.isSuccess) Result.success() else Result.retry()
    }
}
