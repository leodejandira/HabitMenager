package com.example.habitapp.domain.repository

import com.example.habitapp.domain.model.EditScope
import com.example.habitapp.domain.model.Habit
import com.example.habitapp.domain.model.HabitActivity
import com.example.habitapp.domain.model.HabitGroup
import com.example.habitapp.domain.model.HabitReminder
import com.example.habitapp.domain.model.JournalEntry
import com.example.habitapp.domain.model.JournalFilter
import com.example.habitapp.domain.model.Routine
import com.example.habitapp.domain.model.Task
import com.example.habitapp.domain.model.TaskList
import com.example.habitapp.domain.model.TimerMode
import com.example.habitapp.domain.model.TimerSession
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

interface HabitRepository {
    fun observeHabits(): Flow<List<Habit>>
    fun observeHabit(id: String): Flow<Habit?>
    fun observeAllActivities(): Flow<List<HabitActivity>>
    fun observeActivitiesForHabit(habitId: String): Flow<List<HabitActivity>>
    fun observeReminders(habitId: String): Flow<List<HabitReminder>>
    fun observeGroups(): Flow<List<HabitGroup>>

    suspend fun getHabit(id: String): Habit?

    /** Creates or updates a habit and replaces its reminders. Returns the habit id. */
    suspend fun saveHabit(habit: Habit, reminders: List<HabitReminder>): String
    suspend fun duplicateHabit(id: String): String?
    suspend fun setArchived(id: String, archived: Boolean)
    suspend fun deleteHabit(id: String)
    suspend fun reorderHabits(idsInOrder: List<String>)

    /** Boolean habits: marks the day done, or clears it when already done. */
    suspend fun toggleCompletion(habitId: String, date: LocalDate)

    /** Adds [amount] (may be negative) to the day's total; the total never drops below zero. */
    suspend fun addProgress(habitId: String, date: LocalDate, amount: Double, note: String? = null)

    /** Marks the day as complete by logging whatever is missing to reach the target. */
    suspend fun completeDay(habitId: String, date: LocalDate)
    suspend fun clearDay(habitId: String, date: LocalDate)
    suspend fun skipDay(habitId: String, date: LocalDate)
    suspend fun updateActivity(activityId: String, date: LocalDate, value: Double, note: String?)
    suspend fun deleteActivity(activityId: String)
    suspend fun recordRelapse(habitId: String, at: Instant, note: String?)

    suspend fun saveGroup(group: HabitGroup)
    suspend fun deleteGroup(id: String)
    suspend fun setGroupCollapsed(id: String, collapsed: Boolean)
}

interface RoutineRepository {
    fun observeRoutines(): Flow<List<Routine>>
    suspend fun saveRoutine(routine: Routine): String
    suspend fun deleteRoutine(id: String)
}

interface TimerRepository {
    fun observeActiveTimer(): Flow<TimerSession?>
    fun observeRecentSessions(limit: Int = 30): Flow<List<TimerSession>>
    suspend fun start(habitId: String, mode: TimerMode, countdownMillis: Long?)
    suspend fun pause()
    suspend fun resume()
    suspend fun reset()
    suspend fun discard()

    /** Stops the timer and logs the elapsed minutes on the habit for today. Returns minutes saved. */
    suspend fun finishAndSave(): Int
}

data class TaskData(
    val tasks: List<Task>,
    /** taskId → completed occurrence date → completion instant. */
    val completions: Map<String, Map<LocalDate, Instant>>,
    /** taskId → removed occurrence dates. */
    val exceptions: Map<String, Set<LocalDate>>,
)

interface TaskRepository {
    fun observeLists(): Flow<List<TaskList>>
    fun observeTaskData(): Flow<TaskData>
    suspend fun getTask(id: String): Task?

    suspend fun saveTask(task: Task): String

    /** Edits a recurring task. [occurrenceDate] is the occurrence the user opened. */
    suspend fun updateRecurringTask(updated: Task, occurrenceDate: LocalDate, scope: EditScope)
    suspend fun setOccurrenceCompleted(taskId: String, occurrenceDate: LocalDate?, completed: Boolean)
    suspend fun deleteTask(taskId: String, occurrenceDate: LocalDate?, scope: EditScope)

    /** Moves one occurrence (or a one-off task) to another day; used by calendar drag-and-drop. */
    suspend fun reschedule(taskId: String, fromDate: LocalDate?, toDate: LocalDate)

    suspend fun saveList(list: TaskList)
    suspend fun deleteList(id: String)
    suspend fun reorderLists(idsInOrder: List<String>)
    suspend fun moveTask(taskId: String, listId: String)
}

data class JournalQuery(
    val text: String = "",
    val filter: JournalFilter = JournalFilter.ALL,
    val habitId: String? = null,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
)

interface JournalRepository {
    fun search(query: JournalQuery): Flow<List<JournalEntry>>
    fun observeForDay(date: LocalDate): Flow<List<JournalEntry>>
    fun observeForHabit(habitId: String): Flow<List<JournalEntry>>
    fun observeDaysWithEntries(from: LocalDate, to: LocalDate): Flow<Set<LocalDate>>
    suspend fun get(id: String): JournalEntry?
    suspend fun save(entry: JournalEntry): String
    suspend fun setBookmarked(id: String, bookmarked: Boolean)
    suspend fun delete(id: String)
}

/** Schedules OS-level reminders. Implemented with AlarmManager in the data layer. */
interface ReminderScheduler {
    suspend fun rescheduleHabit(habitId: String)
    suspend fun cancelHabit(habitId: String)
    suspend fun rescheduleTask(taskId: String)
    suspend fun cancelTask(taskId: String)
    suspend fun rescheduleAll()
}
