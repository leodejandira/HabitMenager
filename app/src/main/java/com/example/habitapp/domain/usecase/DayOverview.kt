package com.example.habitapp.domain.usecase

import com.example.habitapp.domain.model.DayProgress
import com.example.habitapp.domain.model.Habit
import com.example.habitapp.domain.model.HabitActivity
import com.example.habitapp.domain.model.HabitType
import com.example.habitapp.domain.model.TaskOccurrence
import com.example.habitapp.domain.model.TaskPriority
import com.example.habitapp.domain.repository.HabitRepository
import com.example.habitapp.domain.repository.TaskData
import com.example.habitapp.domain.repository.TaskRepository
import com.example.habitapp.domain.repository.UserPreferencesRepository
import com.example.habitapp.domain.util.HabitCalculator
import com.example.habitapp.domain.util.QuitCalculator
import com.example.habitapp.domain.util.QuitStatus
import com.example.habitapp.domain.util.StreakInfo
import com.example.habitapp.domain.util.TaskRecurrenceCalculator
import com.example.habitapp.domain.util.TimeProvider
import com.example.habitapp.domain.util.resolveFirstDayOfWeek
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

data class HabitDayItem(
    val habit: Habit,
    val progress: DayProgress,
    val streak: StreakInfo,
    /** Completed days in the habit's week/month (flexible habits). */
    val periodCompletions: Int,
    val countsTowardProgress: Boolean,
    val quit: QuitStatus?,
)

data class DayOverview(
    val date: LocalDate,
    val habits: List<HabitDayItem>,
    val tasks: List<TaskOccurrence>,
    val overdueTasks: List<TaskOccurrence>,
) {
    private val counted = habits.filter { it.countsTowardProgress }
    val totalHabits: Int get() = counted.size
    val completedHabits: Int get() = counted.count { it.progress.completed }
    val totalTasks: Int get() = tasks.size
    val completedTasks: Int get() = tasks.count { it.completed }

    /** Combined completion of habits and tasks due on [date]. */
    val fraction: Float
        get() {
            val total = totalHabits + totalTasks
            return if (total == 0) 0f else (completedHabits + completedTasks).toFloat() / total
        }

    val isEmpty: Boolean get() = habits.isEmpty() && tasks.isEmpty() && overdueTasks.isEmpty()
}

/** Task occurrence queries shared by the dashboard, calendar, task list and widgets. */
object TaskQueries {

    fun occurrencesOn(data: TaskData, date: LocalDate): List<TaskOccurrence> =
        data.tasks.mapNotNull { task ->
            val exceptions = data.exceptions[task.id].orEmpty()
            when {
                task.recurrence.isRecurring ->
                    if (TaskRecurrenceCalculator.occursOn(task, date, exceptions)) {
                        TaskOccurrence(task, date, data.completions[task.id]?.get(date))
                    } else {
                        null
                    }
                task.dueDate == date -> TaskOccurrence(task, date, task.completedAt)
                else -> null
            }
        }.sortedWith(occurrenceOrder)

    /** One-off tasks due before [today] that are still open. */
    fun overdue(data: TaskData, today: LocalDate): List<TaskOccurrence> =
        data.tasks.filter { !it.recurrence.isRecurring && it.completedAt == null && it.dueDate?.isBefore(today) == true }
            .map { TaskOccurrence(it, it.dueDate, null) }
            .sortedWith(occurrenceOrder)

    /**
     * The occurrence that represents a task in list views: the task itself for one-off
     * tasks, the next open occurrence (from [today]) for recurring series.
     */
    fun representative(data: TaskData, today: LocalDate): List<TaskOccurrence> =
        data.tasks.mapNotNull { task ->
            if (!task.recurrence.isRecurring) {
                TaskOccurrence(task, task.dueDate, task.completedAt)
            } else {
                val done = data.completions[task.id].orEmpty()
                val next = TaskRecurrenceCalculator.nextOccurrenceOnOrAfter(
                    task,
                    today,
                    data.exceptions[task.id].orEmpty(),
                ) { it in done }
                next?.let { TaskOccurrence(task, it, null) }
            }
        }

    /** Every completed occurrence, newest first (completion history). */
    fun completedHistory(data: TaskData): List<TaskOccurrence> {
        val oneOff = data.tasks.filter { !it.recurrence.isRecurring && it.completedAt != null }
            .map { TaskOccurrence(it, it.dueDate, it.completedAt) }
        val byId = data.tasks.associateBy { it.id }
        val recurring = data.completions.flatMap { (taskId, dates) ->
            val task = byId[taskId] ?: return@flatMap emptyList()
            dates.map { (date, at) -> TaskOccurrence(task, date, at) }
        }
        return (oneOff + recurring).sortedByDescending { it.completedAt }
    }

    val occurrenceOrder: Comparator<TaskOccurrence> =
        compareBy<TaskOccurrence> { it.completed }
            .thenBy { it.task.dueTime == null }
            .thenBy { it.task.dueTime }
            .thenByDescending { it.task.priority.level }
            .thenBy { it.task.createdAt }

    fun priorityColorLevel(priority: TaskPriority): Int = priority.level
}

class BuildDayOverviewUseCase @Inject constructor(
    private val time: TimeProvider,
) {
    operator fun invoke(
        date: LocalDate,
        habits: List<Habit>,
        activities: List<HabitActivity>,
        taskData: TaskData,
        firstDayOfWeek: DayOfWeek,
    ): DayOverview {
        val today = time.today()
        val byHabit = activities.groupBy { it.habitId }
        val items = habits
            .filter { !it.archived && HabitCalculator.isScheduledOn(it, date) }
            .sortedBy { it.sortOrder }
            .map { habit ->
                val habitActivities = byHabit[habit.id].orEmpty()
                val progress = HabitCalculator.progressByDay(habit, habitActivities)
                HabitDayItem(
                    habit = habit,
                    progress = progress[date] ?: HabitCalculator.emptyProgress(habit, date),
                    streak = HabitCalculator.streak(habit, progress, today, firstDayOfWeek),
                    periodCompletions = HabitCalculator.completionsInPeriod(habit, progress, date, firstDayOfWeek),
                    countsTowardProgress = HabitCalculator.countsTowardDailyProgress(habit, progress, date, firstDayOfWeek),
                    quit = if (habit.type == HabitType.QUIT) {
                        QuitCalculator.status(habit, habitActivities, time.nowInstant(), time.zone())
                    } else {
                        null
                    },
                )
            }
        return DayOverview(
            date = date,
            habits = items,
            tasks = TaskQueries.occurrencesOn(taskData, date),
            overdueTasks = if (date == today) TaskQueries.overdue(taskData, today) else emptyList(),
        )
    }
}

class ObserveDayOverviewUseCase @Inject constructor(
    private val habitRepository: HabitRepository,
    private val taskRepository: TaskRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val build: BuildDayOverviewUseCase,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(dates: Flow<LocalDate>): Flow<DayOverview> = combine(
        dates,
        habitRepository.observeHabits(),
        habitRepository.observeAllActivities(),
        taskRepository.observeTaskData(),
        preferencesRepository.preferences,
    ) { date, habits, activities, tasks, prefs ->
        build(date, habits, activities, tasks, resolveFirstDayOfWeek(prefs.firstDayOfWeek))
    }
}
