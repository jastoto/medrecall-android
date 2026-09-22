package com.asok.medrecall.data.calendar

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules [CalendarReconciliationWorker] -- the periodic pass (interval
 * from Settings > Calendar Sync, default 15 minutes, the OS floor for
 * periodic WorkManager jobs) plus an immediate one-off pass on app
 * foreground/resume, so opening the app always shows current data
 * regardless of where the periodic timer is. See
 * calendar-two-way-sync-scope.md §3.1.
 */
object CalendarSyncScheduler {
    private const val PERIODIC_WORK_NAME = "calendar_reconciliation_periodic"
    private const val ON_RESUME_WORK_NAME = "calendar_reconciliation_on_resume"

    /** Call at app startup and whenever the user changes the sync interval in Settings. */
    fun schedulePeriodic(context: Context, intervalMinutes: Long) {
        val request = PeriodicWorkRequestBuilder<CalendarReconciliationWorker>(
            intervalMinutes.coerceAtLeast(15), TimeUnit.MINUTES
        )
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /** Call on app foreground/resume. KEEP so a burst of quick resumes doesn't queue up redundant passes. */
    fun syncNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<CalendarReconciliationWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            ON_RESUME_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}
