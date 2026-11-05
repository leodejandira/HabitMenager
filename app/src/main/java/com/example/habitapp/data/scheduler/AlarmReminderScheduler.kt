package com.example.habitapp.data.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import com.example.habitapp.data.local.dao.HabitDao
import com.example.habitapp.data.local.dao.TaskDao
import com.example.habitapp.data.repository.toDomain
import com.example.habitapp.domain.model.Habit
import com.example.habitapp.domain.model.HabitReminder
import com.example.habitapp.domain.model.Task
import com.example.habitapp.domain.repository.ReminderScheduler
import com.example.habitapp.domain.util.HabitCalculator
import com.example.habitapp.domain.util.TaskRecurrenceCalculator
import com.example.habitapp.domain.util.TimeProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules one alarm per habit and one per task (the next upcoming reminder only).
 * After an alarm fires, the receiver asks for the next one, so there are never
 * orphaned or duplicate alarms. Exact alarms are used only when the user allows them;
 * otherwise Android may deliver the reminder up to ~10 minutes late.
 */
@Singleton
class AlarmReminderScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val habitDao: HabitDao,
    private val taskDao: TaskDao,
    private val time: TimeProvider,
) : ReminderScheduler {

    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)

    override suspend fun rescheduleHabit(habitId: String) {
        val habit = habitDao.getById(habitId)?.toDomain()
        val pending = habitIntent(habitId)
        alarmManager.cancel(pending)
        if (habit == null || habit.archived) return
        val reminders = habitDao.getReminders(habitId).map { it.toDomain() }.filter { it.enabled }
        nextHabitTrigger(habit, reminders, time.nowInstant())?.let { schedule(it, pending) }
    }

    override suspend fun cancelHabit(habitId: String) = alarmManager.cancel(habitIntent(habitId))

    override suspend fun rescheduleTask(taskId: String) {
        val pending = taskIntent(taskId)
        alarmManager.cancel(pending)
        val task = taskDao.getTask(taskId)?.toDomain() ?: return
        val completions = taskDao.getAllCompletions().filter { it.taskId == taskId }
            .map { LocalDate.ofEpochDay(it.occurrenceDate) }.toSet()
        val exceptions = taskDao.getAllExceptions().filter { it.taskId == taskId }
            .map { LocalDate.ofEpochDay(it.occurrenceDate) }.toSet()
        nextTaskTrigger(task, completions, exceptions, time.nowInstant())?.let { schedule(it, pending) }
    }

    override suspend fun cancelTask(taskId: String) = alarmManager.cancel(taskIntent(taskId))

    override suspend fun rescheduleAll() {
        habitDao.getAll().forEach { rescheduleHabit(it.id) }
        taskDao.getAllTasks().forEach { rescheduleTask(it.id) }
    }

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun nextHabitTrigger(habit: Habit, reminders: List<HabitReminder>, now: Instant): Instant? {
        if (reminders.isEmpty()) return null
        val zone = time.zone()
        val today = now.atZone(zone).toLocalDate()
        for (offset in 0L..LOOKAHEAD_DAYS) {
            val date = today.plusDays(offset)
            if (!HabitCalculator.isScheduledOn(habit, date)) continue
            val candidate = reminders
                .filter { it.weekdays.isEmpty() || date.dayOfWeek in it.weekdays }
                .map { date.atTime(LocalTime.of(it.minuteOfDay / 60, it.minuteOfDay % 60)).atZone(zone).toInstant() }
                .filter { it.isAfter(now) }
                .minOrNull()
            if (candidate != null) return candidate
        }
        return null
    }

    private fun nextTaskTrigger(
        task: Task,
        completions: Set<LocalDate>,
        exceptions: Set<LocalDate>,
        now: Instant,
    ): Instant? {
        val minutesBefore = task.reminderMinutesBefore ?: return null
        if (task.dueDate == null || task.completedAt != null) return null
        val zone = time.zone()
        var from = now.atZone(zone).toLocalDate().minusDays(1)
        repeat(MAX_TASK_ATTEMPTS) {
            val date = TaskRecurrenceCalculator.nextOccurrenceOnOrAfter(task, from, exceptions) { it in completions }
                ?: return null
            val dueAt = date.atTime(task.dueTime ?: DEFAULT_TASK_TIME).atZone(zone).toInstant()
            val trigger = dueAt.minusSeconds(minutesBefore * 60L)
            if (trigger.isAfter(now)) return trigger
            if (!task.recurrence.isRecurring) return null
            from = date.plusDays(1)
        }
        return null
    }

    private fun schedule(at: Instant, operation: PendingIntent) {
        val millis = at.toEpochMilli()
        try {
            if (canScheduleExact()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, operation)
            } else {
                alarmManager.setWindow(AlarmManager.RTC_WAKEUP, millis, INEXACT_WINDOW_MILLIS, operation)
            }
        } catch (security: SecurityException) {
            // Exact-alarm permission revoked between the check and the call: fall back to inexact.
            Log.w(TAG, "Exact alarm not permitted, using inexact window", security)
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, millis, INEXACT_WINDOW_MILLIS, operation)
        }
    }

    private fun habitIntent(habitId: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_HABIT_REMINDER)
            .setData(Uri.parse("steady://habit/$habitId"))
            .putExtra(ReminderReceiver.EXTRA_ID, habitId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun taskIntent(taskId: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_TASK_REMINDER)
            .setData(Uri.parse("steady://task/$taskId"))
            .putExtra(ReminderReceiver.EXTRA_ID, taskId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val TAG = "ReminderScheduler"
        const val LOOKAHEAD_DAYS = 400L
        const val MAX_TASK_ATTEMPTS = 60
        const val INEXACT_WINDOW_MILLIS = 10 * 60 * 1000L
        val DEFAULT_TASK_TIME: LocalTime = LocalTime.of(9, 0)
    }
}
