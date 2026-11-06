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

/**
 * Which days a habit is expected on. TIMES_PER_WEEK / TIMES_PER_MONTH are legacy (schema v1)
 * values; they are migrated to [GoalPeriod.WEEK] / [GoalPeriod.MONTH] goals.
 */
enum class FrequencyType { DAILY, WEEKDAYS, TIMES_PER_WEEK, TIMES_PER_MONTH }

/** The period a habit's target applies to: "at least 2 litres per DAY", "5 times per WEEK"… */
enum class GoalPeriod { DAY, WEEK, MONTH, YEAR }

/** AT_LEAST builds a habit (reach the target); AT_MOST limits one (stay at or under it). */
enum class GoalType { AT_LEAST, AT_MOST }

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
    val goalPeriod: GoalPeriod = GoalPeriod.DAY,
    val goalType: GoalType = GoalType.AT_LEAST,
    val listId: String? = null,
    val priority: Int = 0,
    val timerMinutes: Int? = null,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val groupId: String? = null,
    val sortOrder: Int = 0,
    val archived: Boolean = false,
    val quitStartedAt: Instant? = null,
    val createdAt: Instant,
) {
    /** Quit habits have no numeric target; everything else uses [target] for its [goalPeriod]. */
    val effectiveTarget: Double get() = if (type == HabitType.QUIT) 1.0 else target

    /** Day-level target used to decide whether a single day is "done". */
    val dailyTarget: Double get() = if (goalPeriod == GoalPeriod.DAY) effectiveTarget else step.coerceAtLeast(MIN_STEP)

    private companion object {
        const val MIN_STEP = 1e-6
    }
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
    /** For limit (AT_MOST) habits a day is "done" while the total stays within the target. */
    val atMost: Boolean = false,
) {
    val completed: Boolean
        get() = !skipped && target > 0 && if (atMost) total <= target + EPSILON else total >= target - EPSILON
    val fraction: Float get() = if (target <= 0) 0f else (total / target).toFloat().coerceIn(0f, 1f)

    companion object {
        private const val EPSILON = 1e-9
    }
}
