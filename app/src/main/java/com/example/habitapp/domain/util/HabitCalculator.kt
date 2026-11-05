package com.example.habitapp.domain.util

import com.example.habitapp.domain.model.DayProgress
import com.example.habitapp.domain.model.FrequencyType
import com.example.habitapp.domain.model.Habit
import com.example.habitapp.domain.model.HabitActivity
import com.example.habitapp.domain.model.HabitType
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class StreakUnit { DAY, WEEK, MONTH }

data class StreakInfo(val current: Int, val longest: Int, val unit: StreakUnit)

data class HabitStats(
    val streak: StreakInfo,
    /** 0..1 share of evaluated scheduled days (or periods) that were completed. */
    val completionRate: Float,
    val totalCompletions: Int,
    val totalValue: Double,
    /** Average value on days that have at least one entry. */
    val averageValue: Double,
    /** Highest single-day total (personal record). */
    val bestDayValue: Double,
)

/** Pure business rules for habit scheduling, progress, streaks and statistics. */
object HabitCalculator {

    fun isActiveOn(habit: Habit, date: LocalDate): Boolean =
        !date.isBefore(habit.startDate) && (habit.endDate == null || !date.isAfter(habit.endDate))

    /**
     * Whether a habit is expected on [date]. Flexible (N per week/month) habits are
     * available every active day; quit habits are tracked continuously.
     */
    fun isScheduledOn(habit: Habit, date: LocalDate): Boolean {
        if (!isActiveOn(habit, date)) return false
        return when (habit.frequency.type) {
            FrequencyType.WEEKDAYS -> date.dayOfWeek in habit.frequency.weekdays
            FrequencyType.DAILY, FrequencyType.TIMES_PER_WEEK, FrequencyType.TIMES_PER_MONTH -> true
        }
    }

    /** Aggregates activity entries into one [DayProgress] per date. */
    fun progressByDay(habit: Habit, activities: List<HabitActivity>): Map<LocalDate, DayProgress> =
        activities.filter { it.habitId == habit.id }
            .groupBy { it.date }
            .mapValues { (date, entries) -> dayProgress(habit, date, entries) }

    fun dayProgress(habit: Habit, date: LocalDate, entries: List<HabitActivity>): DayProgress {
        val valueEntries = entries.filter { !it.skipped }
        val total = valueEntries.sumOf { it.value }.coerceAtLeast(0.0)
        // The target in force when the day was logged; falls back to the current target.
        val target = valueEntries.maxByOrNull { it.loggedAt }?.targetSnapshot?.takeIf { it > 0 }
            ?: habit.effectiveTarget
        val hasSkip = entries.any { it.skipped }
        return DayProgress(
            date = date,
            total = total,
            target = target,
            skipped = hasSkip && total < target,
            entryCount = valueEntries.size,
        )
    }

    fun emptyProgress(habit: Habit, date: LocalDate) =
        DayProgress(date = date, total = 0.0, target = habit.effectiveTarget, skipped = false, entryCount = 0)

    fun periodStart(date: LocalDate, type: FrequencyType, firstDayOfWeek: DayOfWeek): LocalDate = when (type) {
        FrequencyType.TIMES_PER_MONTH -> date.withDayOfMonth(1)
        else -> date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
    }

    fun periodEnd(start: LocalDate, type: FrequencyType): LocalDate = when (type) {
        FrequencyType.TIMES_PER_MONTH -> start.plusMonths(1).minusDays(1)
        else -> start.plusDays(6)
    }

    /** Completed days in the week/month that contains [date], up to and including [date]'s period end. */
    fun completionsInPeriod(
        habit: Habit,
        progress: Map<LocalDate, DayProgress>,
        date: LocalDate,
        firstDayOfWeek: DayOfWeek,
    ): Int {
        val start = periodStart(date, habit.frequency.type, firstDayOfWeek)
        val end = periodEnd(start, habit.frequency.type)
        return progress.values.count { it.completed && !it.date.isBefore(start) && !it.date.isAfter(end) }
    }

    /**
     * Whether the habit counts toward the daily completion percentage on [date].
     * Flexible habits count only while their period quota is still open (or when done that day).
     * Quit habits are excluded: they are tracked as elapsed time, not as a daily task.
     */
    fun countsTowardDailyProgress(
        habit: Habit,
        progress: Map<LocalDate, DayProgress>,
        date: LocalDate,
        firstDayOfWeek: DayOfWeek,
    ): Boolean {
        if (habit.archived || habit.type == HabitType.QUIT || !isScheduledOn(habit, date)) return false
        if (!habit.frequency.isFlexible) return true
        if (progress[date]?.completed == true) return true
        val start = periodStart(date, habit.frequency.type, firstDayOfWeek)
        val doneBefore = progress.values.count { it.completed && !it.date.isBefore(start) && it.date.isBefore(date) }
        return doneBefore < habit.frequency.timesPerPeriod
    }

    fun streak(
        habit: Habit,
        progress: Map<LocalDate, DayProgress>,
        today: LocalDate,
        firstDayOfWeek: DayOfWeek,
    ): StreakInfo = when {
        habit.type == HabitType.QUIT -> StreakInfo(0, 0, StreakUnit.DAY)
        habit.frequency.isFlexible -> periodStreak(habit, progress, today, firstDayOfWeek)
        else -> dayStreak(habit, progress, today)
    }

    /**
     * Streak over scheduled days only. Unscheduled days are ignored, skipped days neither
     * count nor break, and today being incomplete does not break the streak yet.
     */
    private fun dayStreak(habit: Habit, progress: Map<LocalDate, DayProgress>, today: LocalDate): StreakInfo {
        var run = 0
        var best = 0
        var date = habit.startDate
        val last = minOf(today, habit.endDate ?: today)
        while (!date.isAfter(last)) {
            if (isScheduledOn(habit, date)) {
                val day = progress[date]
                when {
                    day?.completed == true -> {
                        run++
                        best = maxOf(best, run)
                    }
                    day?.skipped == true -> Unit
                    date == today -> Unit
                    else -> run = 0
                }
            }
            date = date.plusDays(1)
        }
        return StreakInfo(current = run, longest = best, unit = StreakUnit.DAY)
    }

    /** Streak in consecutive weeks/months whose quota was met; the current period is pending. */
    private fun periodStreak(
        habit: Habit,
        progress: Map<LocalDate, DayProgress>,
        today: LocalDate,
        firstDayOfWeek: DayOfWeek,
    ): StreakInfo {
        val type = habit.frequency.type
        val unit = if (type == FrequencyType.TIMES_PER_MONTH) StreakUnit.MONTH else StreakUnit.WEEK
        val currentPeriod = periodStart(today, type, firstDayOfWeek)
        var start = periodStart(habit.startDate, type, firstDayOfWeek)
        var run = 0
        var best = 0
        val completedDates = progress.values.filter { it.completed }.map { it.date }
        while (!start.isAfter(currentPeriod)) {
            val end = periodEnd(start, type)
            val count = completedDates.count { !it.isBefore(start) && !it.isAfter(end) }
            when {
                count >= habit.frequency.timesPerPeriod -> {
                    run++
                    best = maxOf(best, run)
                }
                start == currentPeriod -> Unit
                else -> run = 0
            }
            start = if (type == FrequencyType.TIMES_PER_MONTH) start.plusMonths(1) else start.plusWeeks(1)
        }
        return StreakInfo(current = run, longest = best, unit = unit)
    }

    fun completionRate(
        habit: Habit,
        progress: Map<LocalDate, DayProgress>,
        today: LocalDate,
        firstDayOfWeek: DayOfWeek,
    ): Float {
        if (habit.frequency.isFlexible) {
            val type = habit.frequency.type
            val currentPeriod = periodStart(today, type, firstDayOfWeek)
            var start = periodStart(habit.startDate, type, firstDayOfWeek)
            var met = 0
            var evaluated = 0
            val completedDates = progress.values.filter { it.completed }.map { it.date }
            while (!start.isAfter(currentPeriod)) {
                val end = periodEnd(start, type)
                val ok = completedDates.count { !it.isBefore(start) && !it.isAfter(end) } >= habit.frequency.timesPerPeriod
                if (ok) met++
                if (ok || start != currentPeriod) evaluated++
                start = if (type == FrequencyType.TIMES_PER_MONTH) start.plusMonths(1) else start.plusWeeks(1)
            }
            return if (evaluated == 0) 0f else met.toFloat() / evaluated
        }
        var done = 0
        var evaluated = 0
        var date = habit.startDate
        val last = minOf(today, habit.endDate ?: today)
        while (!date.isAfter(last)) {
            if (isScheduledOn(habit, date)) {
                val day = progress[date]
                when {
                    day?.completed == true -> {
                        done++
                        evaluated++
                    }
                    day?.skipped == true -> Unit
                    date == today -> Unit
                    else -> evaluated++
                }
            }
            date = date.plusDays(1)
        }
        return if (evaluated == 0) 0f else done.toFloat() / evaluated
    }

    fun stats(
        habit: Habit,
        activities: List<HabitActivity>,
        today: LocalDate,
        firstDayOfWeek: DayOfWeek,
    ): HabitStats {
        val progress = progressByDay(habit, activities)
        val daysWithValues = progress.values.filter { it.entryCount > 0 }
        return HabitStats(
            streak = streak(habit, progress, today, firstDayOfWeek),
            completionRate = completionRate(habit, progress, today, firstDayOfWeek),
            totalCompletions = progress.values.count { it.completed },
            totalValue = daysWithValues.sumOf { it.total },
            averageValue = if (daysWithValues.isEmpty()) 0.0 else daysWithValues.sumOf { it.total } / daysWithValues.size,
            bestDayValue = daysWithValues.maxOfOrNull { it.total } ?: 0.0,
        )
    }
}

data class QuitStatus(
    /** Start of the current abstinence run. */
    val since: Instant,
    val elapsed: Duration,
    val longest: Duration,
    val relapseCount: Int,
    val nextMilestoneDays: Int?,
)

/** Quit-habit calculations. Relapses are activity entries with a positive value. */
object QuitCalculator {

    val MILESTONE_DAYS = listOf(1, 3, 7, 14, 30, 60, 90, 180, 365, 730, 1095)

    fun status(habit: Habit, activities: List<HabitActivity>, now: Instant, zone: ZoneId): QuitStatus {
        val quitStart = habit.quitStartedAt ?: habit.startDate.atStartOfDay(zone).toInstant()
        val relapses = activities
            .filter { it.habitId == habit.id && !it.skipped && it.value > 0 && !it.loggedAt.isBefore(quitStart) }
            .map { it.loggedAt }
            .sorted()
        val boundaries = listOf(quitStart) + relapses
        val since = boundaries.last()
        val elapsed = Duration.between(since, now).coerceAtLeastZero()
        val pastRuns = boundaries.zipWithNext { a, b -> Duration.between(a, b) }
        val longest = (pastRuns + elapsed).maxOrNull() ?: elapsed
        val elapsedDays = elapsed.toDays()
        return QuitStatus(
            since = since,
            elapsed = elapsed,
            longest = longest,
            relapseCount = relapses.size,
            nextMilestoneDays = MILESTONE_DAYS.firstOrNull { it > elapsedDays },
        )
    }

    fun reachedMilestones(status: QuitStatus): List<Int> =
        MILESTONE_DAYS.filter { it <= status.longest.toDays() }

    private fun Duration.coerceAtLeastZero(): Duration = if (isNegative) Duration.ZERO else this

    /** Whole days between two dates, never negative. */
    fun daysBetween(from: LocalDate, to: LocalDate): Long = ChronoUnit.DAYS.between(from, to).coerceAtLeast(0)
}
