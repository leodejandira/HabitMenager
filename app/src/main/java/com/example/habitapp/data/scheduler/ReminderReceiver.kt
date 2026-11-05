package com.example.habitapp.data.scheduler

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.habitapp.MainActivity
import com.example.habitapp.R
import com.example.habitapp.domain.model.HabitType
import com.example.habitapp.domain.repository.HabitRepository
import com.example.habitapp.domain.repository.ReminderScheduler
import com.example.habitapp.domain.repository.TaskRepository
import com.example.habitapp.domain.util.TimeProvider
import com.example.habitapp.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var habitRepository: HabitRepository
    @Inject lateinit var taskRepository: TaskRepository
    @Inject lateinit var scheduler: ReminderScheduler
    @Inject lateinit var time: TimeProvider
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val pending = goAsync()
        appScope.launch {
            try {
                handle(context, intent.action, id)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Log.e(TAG, "Failed to handle ${intent.action}", error)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(context: Context, action: String?, id: String) {
        when (action) {
            ACTION_HABIT_REMINDER -> {
                val habit = habitRepository.getHabit(id)
                if (habit != null && !habit.archived) {
                    Notifications.showHabitReminder(context, habit.id, "${habit.emoji} ${habit.name}", habit.type)
                }
                scheduler.rescheduleHabit(id)
            }
            ACTION_TASK_REMINDER -> {
                val task = taskRepository.getTask(id)
                if (task != null) Notifications.showTaskReminder(context, task.id, task.title)
                scheduler.rescheduleTask(id)
            }
            ACTION_COMPLETE_HABIT -> {
                habitRepository.completeDay(id, time.today())
                NotificationManagerCompat.from(context).cancel(Notifications.habitNotificationId(id))
            }
            ACTION_COMPLETE_TASK -> {
                val task = taskRepository.getTask(id)
                if (task != null) {
                    val occurrence = if (task.recurrence.isRecurring) time.today() else task.dueDate
                    taskRepository.setOccurrenceCompleted(id, occurrence, true)
                }
                NotificationManagerCompat.from(context).cancel(Notifications.taskNotificationId(id))
            }
        }
    }

    companion object {
        private const val TAG = "ReminderReceiver"
        const val ACTION_HABIT_REMINDER = "com.example.habitapp.action.HABIT_REMINDER"
        const val ACTION_TASK_REMINDER = "com.example.habitapp.action.TASK_REMINDER"
        const val ACTION_COMPLETE_HABIT = "com.example.habitapp.action.COMPLETE_HABIT"
        const val ACTION_COMPLETE_TASK = "com.example.habitapp.action.COMPLETE_TASK"
        const val EXTRA_ID = "id"
    }
}

/** Re-creates alarms after reboot, app update, clock or time-zone change. */
@AndroidEntryPoint
class RescheduleReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduler: ReminderScheduler
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        appScope.launch {
            try {
                scheduler.rescheduleAll()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Log.e("RescheduleReceiver", "Failed to reschedule reminders", error)
            } finally {
                pending.finish()
            }
        }
    }
}

object Notifications {
    const val CHANNEL_HABITS = "habit_reminders"
    const val CHANNEL_TASKS = "task_reminders"
    const val EXTRA_OPEN_HABIT = "open_habit_id"
    const val EXTRA_OPEN_TASKS = "open_tasks"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_HABITS, context.getString(R.string.channel_habits), NotificationManager.IMPORTANCE_DEFAULT),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_TASKS, context.getString(R.string.channel_tasks), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    fun habitNotificationId(habitId: String): Int = ("h$habitId").hashCode()
    fun taskNotificationId(taskId: String): Int = ("t$taskId").hashCode()

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun showHabitReminder(context: Context, habitId: String, title: String, type: HabitType) {
        if (!canPost(context)) return
        val open = PendingIntent.getActivity(
            context,
            habitNotificationId(habitId),
            Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .setData(Uri.parse("steady://open/habit/$habitId"))
                .putExtra(EXTRA_OPEN_HABIT, habitId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_HABITS)
            .setSmallIcon(R.drawable.ic_habits)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.notification_habit_text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
        if (type != HabitType.QUIT) {
            val complete = PendingIntent.getBroadcast(
                context,
                habitNotificationId(habitId),
                Intent(context, ReminderReceiver::class.java)
                    .setAction(ReminderReceiver.ACTION_COMPLETE_HABIT)
                    .setData(Uri.parse("steady://complete/habit/$habitId"))
                    .putExtra(ReminderReceiver.EXTRA_ID, habitId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(R.drawable.ic_check, context.getString(R.string.action_mark_done), complete)
        }
        notify(context, habitNotificationId(habitId), builder)
    }

    fun showTaskReminder(context: Context, taskId: String, title: String) {
        if (!canPost(context)) return
        val open = PendingIntent.getActivity(
            context,
            taskNotificationId(taskId),
            Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .setData(Uri.parse("steady://open/tasks/$taskId"))
                .putExtra(EXTRA_OPEN_TASKS, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val complete = PendingIntent.getBroadcast(
            context,
            taskNotificationId(taskId),
            Intent(context, ReminderReceiver::class.java)
                .setAction(ReminderReceiver.ACTION_COMPLETE_TASK)
                .setData(Uri.parse("steady://complete/task/$taskId"))
                .putExtra(ReminderReceiver.EXTRA_ID, taskId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_TASKS)
            .setSmallIcon(R.drawable.ic_tasks)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.notification_task_text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .addAction(R.drawable.ic_check, context.getString(R.string.action_mark_done), complete)
        notify(context, taskNotificationId(taskId), builder)
    }

    private fun notify(context: Context, id: Int, builder: NotificationCompat.Builder) {
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (security: SecurityException) {
            Log.w("Notifications", "Notification permission missing", security)
        }
    }
}
