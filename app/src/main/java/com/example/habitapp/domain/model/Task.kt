package com.example.habitapp.domain.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

enum class TaskPriority(val level: Int) {
    NONE(0), LOW(1), MEDIUM(2), HIGH(3), URGENT(4);

    companion object {
        fun fromLevel(level: Int): TaskPriority = entries.firstOrNull { it.level == level } ?: NONE
    }
}

enum class RecurrenceType { NONE, DAILY, WEEKLY, BIWEEKLY, MONTHLY, YEARLY, WEEKDAYS }

data class TaskRecurrence(
    val type: RecurrenceType = RecurrenceType.NONE,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val until: LocalDate? = null,
) {
    val isRecurring: Boolean get() = type != RecurrenceType.NONE
}

data class TaskList(
    val id: String,
    val name: String,
    val colorKey: String,
    val sortOrder: Int,
    val archived: Boolean,
    val isInbox: Boolean,
)

data class Task(
    val id: String,
    val listId: String,
    val title: String,
    val description: String = "",
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val priority: TaskPriority = TaskPriority.NONE,
    val colorKey: String? = null,
    val recurrence: TaskRecurrence = TaskRecurrence(),
    val reminderMinutesBefore: Int? = null,
    val completedAt: Instant? = null,
    val createdAt: Instant,
)

/** A concrete appearance of a task on a date (one-off tasks have exactly one). */
data class TaskOccurrence(
    val task: Task,
    /** Null for tasks without a due date. */
    val date: LocalDate?,
    val completedAt: Instant?,
) {
    val completed: Boolean get() = completedAt != null
    val key: String get() = "${task.id}@${date?.toEpochDay() ?: "none"}"
}

/** Which occurrences an edit to a recurring task applies to. */
enum class EditScope { THIS_OCCURRENCE, THIS_AND_FUTURE, ALL }
