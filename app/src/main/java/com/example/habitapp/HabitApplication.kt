package com.example.habitapp

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.habitapp.data.scheduler.Notifications
import com.example.habitapp.di.ApplicationScope
import com.example.habitapp.domain.repository.HabitRepository
import com.example.habitapp.domain.repository.ReminderScheduler
import com.example.habitapp.domain.repository.TaskRepository
import com.example.habitapp.widgets.WidgetUpdater
import com.example.habitapp.workers.DailyMaintenanceWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class HabitApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var reminderScheduler: ReminderScheduler

    @Inject
    lateinit var habitRepository: HabitRepository

    @Inject
    lateinit var taskRepository: TaskRepository

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        DailyMaintenanceWorker.schedule(this)
        appScope.launch {
            // Alarms are cleared when the app is force-stopped; re-create them on every start.
            runCatching { reminderScheduler.rescheduleAll() }.onFailure { Log.e(TAG, "Reschedule failed", it) }
        }
        appScope.launch {
            // Keep home-screen widgets in sync with any change to habits, activities or tasks.
            combine(
                habitRepository.observeHabits(),
                habitRepository.observeAllActivities(),
                taskRepository.observeTaskData(),
            ) { _, _, _ -> Unit }
                .drop(1)
                .debounce(WIDGET_DEBOUNCE_MILLIS)
                .collect { WidgetUpdater.updateAll(this@HabitApplication) }
        }
    }

    // WorkManager is initialised on demand (default initializer removed in the manifest)
    // so @HiltWorker workers can receive injected dependencies.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private companion object {
        const val TAG = "HabitApplication"
        const val WIDGET_DEBOUNCE_MILLIS = 500L
    }
}
