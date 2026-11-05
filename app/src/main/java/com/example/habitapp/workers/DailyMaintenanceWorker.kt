package com.example.habitapp.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.habitapp.domain.repository.ReminderScheduler
import com.example.habitapp.widgets.WidgetUpdater
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs a few times a day (WorkManager decides the exact moment within the window):
 * re-creates reminder alarms as a safety net and refreshes widgets after midnight.
 */
@HiltWorker
class DailyMaintenanceWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val reminderScheduler: ReminderScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        reminderScheduler.rescheduleAll()
        WidgetUpdater.updateAll(applicationContext)
        Result.success()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        Log.e(TAG, "Maintenance failed", error)
        Result.retry()
    }

    companion object {
        private const val TAG = "DailyMaintenance"
        private const val UNIQUE_NAME = "daily_maintenance"

        /** Idempotent: KEEP ensures there is never more than one scheduled job. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DailyMaintenanceWorker>(6, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
