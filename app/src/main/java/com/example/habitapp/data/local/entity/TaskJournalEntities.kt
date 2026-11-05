package com.example.habitapp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "task_lists",
    indices = [Index("sortOrder")],
)
data class TaskListEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorKey: String,
    val sortOrder: Int,
    val archived: Boolean,
    /** The Inbox list cannot be deleted; tasks of a deleted list move there. */
    val isInbox: Boolean,
    val createdAt: Long,
)

@Serializable
@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = TaskListEntity::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("listId"), Index("dueDate"), Index("completedAt")],
)
data class TaskEntity(
    @PrimaryKey val id: String,
    val listId: String,
    val title: String,
    val description: String,
    /** Epoch day; for recurring tasks this is the first occurrence (series anchor). */
    val dueDate: Long?,
    /** Minute of day, null when the task has no time. */
    val dueMinute: Int?,
    val priority: Int,
    val colorKey: String?,
    val recurrenceType: String,
    val recurrenceWeekdaysMask: Int,
    /** Last epoch day (inclusive) a recurring series produces occurrences. */
    val recurrenceUntil: Long?,
    /** Minutes before the due time to remind; null for no reminder. */
    val reminderMinutesBefore: Int?,
    /** Completion of a non-recurring task. Recurring occurrences use [TaskCompletionEntity]. */
    val completedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Completion of one occurrence of a recurring task. */
@Serializable
@Entity(
    tableName = "task_completions",
    primaryKeys = ["taskId", "occurrenceDate"],
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("completedAt")],
)
data class TaskCompletionEntity(
    val taskId: String,
    val occurrenceDate: Long,
    val completedAt: Long,
)

/** An occurrence removed from a recurring series (deleted or moved to its own task). */
@Serializable
@Entity(
    tableName = "task_exceptions",
    primaryKeys = ["taskId", "occurrenceDate"],
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class TaskExceptionEntity(
    val taskId: String,
    val occurrenceDate: Long,
)

@Serializable
@Entity(
    tableName = "journal_entries",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("date"), Index("habitId"), Index("bookmarked")],
)
data class JournalEntryEntity(
    @PrimaryKey val id: String,
    val date: Long,
    val title: String,
    /** Body in the app's lightweight markup (see RichText). */
    val content: String,
    val mood: Int?,
    val bookmarked: Boolean,
    val habitId: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
