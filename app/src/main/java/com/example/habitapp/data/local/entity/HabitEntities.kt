package com.example.habitapp.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Dates (calendar days) are stored as epoch days; instants as UTC epoch millis.

@Entity(
    tableName = "habit_groups",
    indices = [Index("sortOrder")],
)
data class HabitGroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sortOrder: Int,
    val collapsed: Boolean,
    val createdAt: Long,
)

@Entity(
    tableName = "habits",
    foreignKeys = [
        ForeignKey(
            entity = HabitGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("groupId"), Index("archived", "sortOrder")],
)
data class HabitEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val emoji: String,
    val colorKey: String,
    val type: String,
    val unit: String,
    val targetValue: Double,
    val stepValue: Double,
    val frequencyType: String,
    /** Bit i (0 = Monday … 6 = Sunday) set when the habit is scheduled on that weekday. */
    val weekdaysMask: Int,
    val timesPerPeriod: Int,
    val startDate: Long,
    val endDate: Long?,
    val groupId: String?,
    val sortOrder: Int,
    val archived: Boolean,
    /** For quit habits: the moment the user quit. */
    val quitStartedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "habit_activities",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("habitId", "date"), Index("date")],
)
data class HabitActivityEntity(
    @PrimaryKey val id: String,
    val habitId: String,
    val date: Long,
    /** Amount added on [date]. For quit habits a row with value 1 records a relapse. */
    val value: Double,
    /** Target in force when the entry was logged, so later goal edits never rewrite history. */
    val targetSnapshot: Double,
    /** A skip entry marks a scheduled day as intentionally skipped (it neither counts nor breaks streaks). */
    val skipped: Boolean,
    val note: String?,
    val loggedAt: Long,
    @ColumnInfo(defaultValue = "")
    val zoneId: String,
)

@Entity(
    tableName = "habit_reminders",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("habitId")],
)
data class HabitReminderEntity(
    @PrimaryKey val id: String,
    val habitId: String,
    val minuteOfDay: Int,
    /** Same bit layout as [HabitEntity.weekdaysMask]; 0 means every day. */
    val weekdaysMask: Int,
    val enabled: Boolean,
)

@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey val id: String,
    val name: String,
    val emoji: String,
    val estimatedMinutes: Int?,
    val sortOrder: Int,
    val createdAt: Long,
)

@Entity(
    tableName = "routine_habits",
    primaryKeys = ["routineId", "habitId"],
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("habitId")],
)
data class RoutineHabitCrossRef(
    val routineId: String,
    val habitId: String,
    val position: Int,
)

@Entity(
    tableName = "timer_sessions",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("habitId")],
)
data class TimerSessionEntity(
    @PrimaryKey val id: String,
    val habitId: String,
    val mode: String,
    /** Countdown length; null for a stopwatch. */
    val countdownMillis: Long?,
    /** Wall-clock start of the current running segment, null while paused. */
    val runningSince: Long?,
    /** Time accumulated in finished segments. */
    val accumulatedMillis: Long,
    val startedAt: Long,
    /** Set when the session was saved or discarded; active sessions have null. */
    val finishedAt: Long?,
)
