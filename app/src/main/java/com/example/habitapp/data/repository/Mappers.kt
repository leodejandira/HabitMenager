package com.example.habitapp.data.repository

import com.example.habitapp.data.local.entity.HabitActivityEntity
import com.example.habitapp.data.local.entity.HabitEntity
import com.example.habitapp.data.local.entity.HabitGroupEntity
import com.example.habitapp.data.local.entity.HabitReminderEntity
import com.example.habitapp.data.local.entity.JournalEntryEntity
import com.example.habitapp.data.local.entity.TaskEntity
import com.example.habitapp.data.local.entity.TaskListEntity
import com.example.habitapp.data.local.entity.TimerSessionEntity
import com.example.habitapp.domain.model.FrequencyType
import com.example.habitapp.domain.model.GoalPeriod
import com.example.habitapp.domain.model.GoalType
import com.example.habitapp.domain.model.Habit
import com.example.habitapp.domain.model.HabitActivity
import com.example.habitapp.domain.model.HabitFrequency
import com.example.habitapp.domain.model.HabitGroup
import com.example.habitapp.domain.model.HabitReminder
import com.example.habitapp.domain.model.HabitType
import com.example.habitapp.domain.model.JournalEntry
import com.example.habitapp.domain.model.RecurrenceType
import com.example.habitapp.domain.model.Task
import com.example.habitapp.domain.model.TaskList
import com.example.habitapp.domain.model.TaskPriority
import com.example.habitapp.domain.model.TaskRecurrence
import com.example.habitapp.domain.model.TimerMode
import com.example.habitapp.domain.model.TimerSession
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** Bit i (0 = Monday … 6 = Sunday). */
fun Set<DayOfWeek>.toMask(): Int = fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }

fun Int.toWeekdays(): Set<DayOfWeek> = DayOfWeek.entries.filter { this and (1 shl (it.value - 1)) != 0 }.toSet()

inline fun <reified T : Enum<T>> enumOrDefault(name: String, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default

fun HabitEntity.toDomain() = Habit(
    id = id,
    name = name,
    description = description,
    emoji = emoji,
    colorKey = colorKey,
    type = enumOrDefault(type, HabitType.BOOLEAN),
    unit = unit,
    target = targetValue,
    step = stepValue,
    frequency = HabitFrequency(
        type = enumOrDefault(frequencyType, FrequencyType.DAILY),
        weekdays = weekdaysMask.toWeekdays(),
        timesPerPeriod = timesPerPeriod,
    ),
    startDate = LocalDate.ofEpochDay(startDate),
    endDate = endDate?.let(LocalDate::ofEpochDay),
    groupId = groupId,
    sortOrder = sortOrder,
    archived = archived,
    quitStartedAt = quitStartedAt?.let(Instant::ofEpochMilli),
    createdAt = Instant.ofEpochMilli(createdAt),
    goalPeriod = enumOrDefault(goalPeriod, GoalPeriod.DAY),
    goalType = enumOrDefault(goalType, GoalType.AT_LEAST),
    listId = listId,
    priority = priority,
    timerMinutes = timerMinutes,
)

fun Habit.toEntity(updatedAt: Long) = HabitEntity(
    id = id,
    name = name.trim(),
    description = description.trim(),
    emoji = emoji,
    colorKey = colorKey,
    type = type.name,
    unit = unit.trim(),
    targetValue = target,
    stepValue = step,
    frequencyType = frequency.type.name,
    weekdaysMask = frequency.weekdays.toMask(),
    timesPerPeriod = frequency.timesPerPeriod,
    startDate = startDate.toEpochDay(),
    endDate = endDate?.toEpochDay(),
    groupId = groupId,
    sortOrder = sortOrder,
    archived = archived,
    quitStartedAt = quitStartedAt?.toEpochMilli(),
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt,
    goalPeriod = goalPeriod.name,
    goalType = goalType.name,
    listId = listId,
    priority = priority,
    timerMinutes = timerMinutes,
)

fun HabitActivityEntity.toDomain() = HabitActivity(
    id = id,
    habitId = habitId,
    date = LocalDate.ofEpochDay(date),
    value = value,
    targetSnapshot = targetSnapshot,
    skipped = skipped,
    note = note,
    loggedAt = Instant.ofEpochMilli(loggedAt),
)

fun HabitGroupEntity.toDomain() = HabitGroup(id = id, name = name, sortOrder = sortOrder, collapsed = collapsed)

fun HabitReminderEntity.toDomain() = HabitReminder(
    id = id,
    habitId = habitId,
    minuteOfDay = minuteOfDay,
    weekdays = weekdaysMask.toWeekdays(),
    enabled = enabled,
)

fun HabitReminder.toEntity() = HabitReminderEntity(
    id = id,
    habitId = habitId,
    minuteOfDay = minuteOfDay,
    weekdaysMask = weekdays.toMask(),
    enabled = enabled,
)

fun TaskListEntity.toDomain() = TaskList(
    id = id,
    name = name,
    colorKey = colorKey,
    sortOrder = sortOrder,
    archived = archived,
    isInbox = isInbox,
    icon = icon,
)

fun TaskEntity.toDomain() = Task(
    id = id,
    listId = listId,
    title = title,
    description = description,
    dueDate = dueDate?.let(LocalDate::ofEpochDay),
    dueTime = dueMinute?.let { LocalTime.of(it / 60, it % 60) },
    priority = TaskPriority.fromLevel(priority),
    colorKey = colorKey,
    recurrence = TaskRecurrence(
        type = enumOrDefault(recurrenceType, RecurrenceType.NONE),
        weekdays = recurrenceWeekdaysMask.toWeekdays(),
        until = recurrenceUntil?.let(LocalDate::ofEpochDay),
    ),
    reminderMinutesBefore = reminderMinutesBefore,
    completedAt = completedAt?.let(Instant::ofEpochMilli),
    createdAt = Instant.ofEpochMilli(createdAt),
)

fun Task.toEntity(updatedAt: Long) = TaskEntity(
    id = id,
    listId = listId,
    title = title.trim(),
    description = description.trim(),
    dueDate = dueDate?.toEpochDay(),
    dueMinute = dueTime?.let { it.hour * 60 + it.minute },
    priority = priority.level,
    colorKey = colorKey,
    recurrenceType = recurrence.type.name,
    recurrenceWeekdaysMask = recurrence.weekdays.toMask(),
    recurrenceUntil = recurrence.until?.toEpochDay(),
    reminderMinutesBefore = reminderMinutesBefore,
    completedAt = completedAt?.toEpochMilli(),
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt,
)

fun JournalEntryEntity.toDomain() = JournalEntry(
    id = id,
    date = LocalDate.ofEpochDay(date),
    title = title,
    content = content,
    mood = mood,
    bookmarked = bookmarked,
    habitId = habitId,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

fun TimerSessionEntity.toDomain() = TimerSession(
    id = id,
    habitId = habitId,
    mode = enumOrDefault(mode, TimerMode.STOPWATCH),
    countdownMillis = countdownMillis,
    runningSince = runningSince?.let(Instant::ofEpochMilli),
    accumulatedMillis = accumulatedMillis,
    startedAt = Instant.ofEpochMilli(startedAt),
)
