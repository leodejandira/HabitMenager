package com.example.habitapp.domain.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

enum class HabitType {
    /** Done / not done. */
    BOOLEAN,

    /** Accumulated quantity in a custom unit (ml, pages, km…). */
    NUMERIC,

    /** Minutes spent. */
    DURATION,

    /** Repetitions. */
    COUNT,

    /** Time since quitting something. */
    QUIT;

    val isMeasurable: Boolean get() = this == NUMERIC || this == DURATION || this == COUNT
}

enum class FrequencyType { DAILY, WEEKDAYS, TIMES_PER_WEEK, TIMES_PER_MONTH }

data class HabitFrequency(
    val type: FrequencyType = FrequencyType.DAILY,
    val weekdays: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val timesPerPeriod: Int = 1,
) {
    val isFlexible: Boolean get() = type == FrequencyType.TIMES_PER_WEEK || type == FrequencyType.TIMES_PER_MONTH
}

data class Habit(
    val id: String,
    val name: String,
    val description: String = "",
    val emoji: String = "✅",
    val colorKey: String = "evergreen",
    val type: HabitType = HabitType.BOOLEAN,
    val unit: String = "",
    val target: Double = 1.0,
    val step: Double = 1.0,
    val frequency: HabitFrequency = HabitFrequency(),
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val groupId: String? = null,
    val sortOrder: Int = 0,
    val archived: Boolean = false,
    val quitStartedAt: Instant? = null,
    val createdAt: Instant,
) {
    /** Boolean and quit habits always have a target of one completion per day. */
    val effectiveTarget: Double get() = if (type == HabitType.BOOLEAN || type == HabitType.QUIT) 1.0 else target
}

data class HabitActivity(
    val id: String,
    val habitId: String,
    val date: LocalDate,
    val value: Double,
    val targetSnapshot: Double,
    val skipped: Boolean = false,
    val note: String? = null,
    val loggedAt: Instant,
)

data class HabitGroup(
    val id: String,
    val name: String,
    val sortOrder: Int,
    val collapsed: Boolean,
)

data class HabitReminder(
    val id: String,
    val habitId: String,
    val minuteOfDay: Int,
    /** Empty means every day. */
    val weekdays: Set<DayOfWeek>,
    val enabled: Boolean = true,
)

data class Routine(
    val id: String,
    val name: String,
    val emoji: String,
    val estimatedMinutes: Int?,
    val sortOrder: Int,
    /** Habit ids in the order the routine is followed. */
    val habitIds: List<String>,
)

/** Progress of one habit on one day, derived from its activity entries. */
data class DayProgress(
    val date: LocalDate,
    val total: Double,
    val target: Double,
    val skipped: Boolean,
    val entryCount: Int,
) {
    val completed: Boolean get() = !skipped && target > 0 && total >= target - EPSILON
    val fraction: Float get() = if (target <= 0) 0f else (total / target).toFloat().coerceIn(0f, 1f)

    companion object {
        private const val EPSILON = 1e-9
    }
}
