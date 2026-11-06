package com.example.habitapp.domain.util

import com.example.habitapp.domain.model.DayProgress
import com.example.habitapp.domain.model.FrequencyType
import com.example.habitapp.domain.model.GoalPeriod
import com.example.habitapp.domain.model.GoalType
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

enum class StreakUnit { DAY, WEEK, MONTH, YEAR }

data class StreakInfo(val current: Int, val longest: Int, val unit: StreakUnit)

/** A finished or ongoing run of consecutive successful periods. */
data class StreakRun(val start: LocalDate, val end: LocalDate, val length: Int)

data class HabitStats(
    val streak: StreakInfo,
    /** 0..1 share of evaluated scheduled days (or periods) that met the goal. */
    val completionRate: Float,
    /** Number of days (DAY goals) or periods (longer goals) that met the goal. */
    val totalCompletions: Int,
    val totalValue: Double,
    /** Average value on days that have at least one entry. */
    val averageValue: Double,
    /** Highest single-day total (personal record). */
    val bestDayValue: Double,
    val activityCount: Int,
)

/** Progress toward the goal for the period (day/week/month/year) containing a date. */
data class PeriodProgress(
    val start: LocalDate,
    val end: LocalDate,
    val total: Double,
    val target: Double,
    val goalType: GoalType,
    /** True while the period has not finished yet. */
    val ongoing: Boolean,
) {
    private val epsilon = 1e-9

    /** AT_LEAST: target reached. AT_MOST: still within the limit. */
    val met: Boolean
        get() = if (goalType == GoalType.AT_MOST) total <= target + epsilon else total >= target - epsilon

    /** AT_MOST goals that went over the limit. */
    val exceeded: Boolean get() = goalType == GoalType.AT_MOST && total > target + epsilon

    val fraction: Float get() = if (target <= 0) 0f else (total / target).toFloat().coerceIn(0f, 1f)
}

/** Pure business rules for habit scheduling, progress, streaks and statistics. */
object HabitCalculator {

    fun isActiveOn(habit: Habit, date: LocalDate): Boolean =
        !date.isBefore(habit.startDate) && (habit.endDate == null || !date.isAfter(habit.endDate))

    /** Whether a habit is expected on [date] (active and, for specific weekdays, on one of them). */
    fun isScheduledOn(habit: Habit, date: LocalDate): Boolean {
        if (!isActiveOn(habit, date)) return false
        return when (habit.frequency.type) {
            FrequencyType.WEEKDAYS -> date.dayOfWeek in habit.frequency.weekdays
            else -> true
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
        // DAY goals keep the target in force when the day was logged, so goal edits never rewrite history.
        val target = if (habit.goalPeriod == GoalPeriod.DAY) {
            valueEntries.maxByOrNull { it.loggedAt }?.targetSnapshot?.takeIf { it > 0 } ?: habit.dailyTarget
        } else {
            habit.dailyTarget
        }
        val hasSkip = entries.any { it.skipped }
        val atMost = habit.goalType == GoalType.AT_MOST && habit.goalPeriod == GoalPeriod.DAY
        val reached = if (atMost) total <= target else total >= target
        return DayProgress(
            date = date,
            total = total,
            target = target,
            skipped = hasSkip && !(reached && total > 0),
            entryCount = valueEntries.size,
            atMost = atMost,
        )
    }

    fun emptyProgress(habit: Habit, date: LocalDate) = DayProgress(
        date = date,
        total = 0.0,
        target = habit.dailyTarget,
        skipped = false,
        entryCount = 0,
        atMost = habit.goalType == GoalType.AT_MOST && habit.goalPeriod == GoalPeriod.DAY,
    )

    private fun Map<LocalDate, DayProgress>.dayOf(habit: Habit, date: LocalDate) = this[date] ?: emptyProgress(habit, date)

    fun periodStart(date: LocalDate, period: GoalPeriod, firstDayOfWeek: DayOfWeek): LocalDate = when (period) {
        GoalPeriod.DAY -> date
        GoalPeriod.WEEK -> date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
        GoalPeriod.MONTH -> date.withDayOfMonth(1)
        GoalPeriod.YEAR -> date.withDayOfYear(1)
    }

    fun periodEnd(start: LocalDate, period: GoalPeriod): LocalDate = when (period) {
        GoalPeriod.DAY -> start
        GoalPeriod.WEEK -> start.plusDays(6)
        GoalPeriod.MONTH -> start.plusMonths(1).minusDays(1)
        GoalPeriod.YEAR -> start.plusYears(1).minusDays(1)
    }

    private fun nextPeriod(start: LocalDate, period: GoalPeriod): LocalDate = periodEnd(start, period).plusDays(1)

    fun periodProgress(
        habit: Habit,
        progress: Map<LocalDate, DayProgress>,
        date: LocalDate,
        firstDayOfWeek: DayOfWeek,
        today: LocalDate,
    ): PeriodProgress {
        val start = periodStart(date, habit.goalPeriod, firstDayOfWeek)
        val end = periodEnd(start, habit.goalPeriod)
        val total = if (habit.goalPeriod == GoalPeriod.DAY) {
            progress[date]?.total ?: 0.0
        } else {
            progress.values.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }.sumOf { it.total }
        }
        val target = if (habit.goalPeriod == GoalPeriod.DAY) progress.dayOf(habit, date).target else habit.effectiveTarget
        return PeriodProgress(start, end, total, target, habit.goalType, ongoing = !end.isBefore(today))
    }

    /**
     * Whether the habit counts toward the daily completion percentage on [date].
     * Longer goals count only while still open (or when worked on that day); quit habits
     * are tracked as elapsed time and never count.
     */
    fun countsTowardDailyProgress(
        habit: Habit,
        progress: Map<LocalDate, DayProgress>,
        date: LocalDate,
        firstDayOfWeek: DayOfWeek,
    ): Boolean {
        if (habit.archived || habit.type == HabitType.QUIT || !isScheduledOn(habit, date)) return false
        if (habit.goalPeriod == GoalPeriod.DAY) return true
        if (habit.goalType == GoalType.AT_MOST) return false
        if ((progress[date]?.total ?: 0.0) > 0) return true
        val start = periodStart(date, habit.goalPeriod, firstDayOfWeek)
        val before = progress.values.filter { !it.date.isBefore(start) && it.date.isBefore(date) }.sumOf { it.total }
        return before < habit.effectiveTarget
    }

    /** Whether the habit is "done" for [date]: the day goal, or the period goal for longer goals. */
    fun isDone(
        habit: Habit,
        progress: Map<LocalDate, DayProgress>,
        date: LocalDate,
        firstDayOfWeek: DayOfWeek,
        today: LocalDate,
    ): Boolean = if (habit.goalPeriod == GoalPeriod.DAY) {
        progress.dayOf(habit, date).completed
    } else {
        val p = periodProgress(habit, progress, date, firstDayOfWeek, today)
        if (habit.goalType == GoalType.AT_MOST) !p.exceeded else p.met
    }

    /** Legacy flexible habits (pre-v2 data that was not migrated) behave like weekly/monthly goals. */
    private fun normalized(habit: Habit): Habit = when (habit.frequency.type) {
        FrequencyType.TIMES_PER_WEEK -> habit.copy(goalPeriod = GoalPeriod.WEEK, frequency = habit.frequency.copy(type = FrequencyType.DAILY))
        FrequencyType.TIMES_PER_MONTH -> habit.copy(goalPeriod = GoalPeriod.MONTH, frequency = habit.frequency.copy(type = FrequencyType.DAILY))
        else -> habit
    }

    private enum class Outcome { MET, MISSED, NEUTRAL }

    /** Outcome of each evaluable day or period in chronological order. */
    private fun outcomes(
        raw: Habit,
        progress: Map<LocalDate, DayProgress>,
        today: LocalDate,
        firstDayOfWeek: DayOfWeek,
    ): List<Pair<LocalDate, Outcome>> {
        val habit = normalized(raw)
        val last = minOf(today, habit.endDate ?: today)
        val result = mutableListOf<Pair<LocalDate, Outcome>>()
        if (habit.goalPeriod == GoalPeriod.DAY) {
            var date = habit.startDate
            while (!date.isAfter(last)) {
                if (isScheduledOn(habit, date)) {
                    val day = progress.dayOf(habit, date)
                    val outcome = when {
                        day.skipped -> Outcome.NEUTRAL
                        habit.goalType == GoalType.AT_MOST -> when {
                            !day.completed -> Outcome.MISSED
                            date == today -> Outcome.NEUTRAL
                            else -> Outcome.MET
                        }
                        day.completed -> Outcome.MET
                        date == today -> Outcome.NEUTRAL
                        else -> Outcome.MISSED
                    }
                    result += date to outcome
                }
                date = date.plusDays(1)
            }
        } else {
            var start = periodStart(habit.startDate, habit.goalPeriod, firstDayOfWeek)
            while (!start.isAfter(last)) {
                val p = periodProgress(habit, progress, start, firstDayOfWeek, today)
                val outcome = when {
                    habit.goalType == GoalType.AT_MOST -> when {
                        p.exceeded -> Outcome.MISSED
                        p.ongoing -> Outcome.NEUTRAL
                        else -> Outcome.MET
                    }
                    p.met -> Outcome.MET
                    p.ongoing -> Outcome.NEUTRAL
                    else -> Outcome.MISSED
                }
                result += start to outcome
                start = nextPeriod(start, habit.goalPeriod)
            }
        }
        return result
    }

    private fun unitOf(habit: Habit): StreakUnit = when (normalized(habit).goalPeriod) {
        GoalPeriod.DAY -> StreakUnit.DAY
        GoalPeriod.WEEK -> StreakUnit.WEEK
        GoalPeriod.MONTH -> StreakUnit.MONTH
        GoalPeriod.YEAR -> StreakUnit.YEAR
    }

    /**
     * Streak over scheduled days (DAY goals) or periods (longer goals). Unscheduled days are
     * ignored, skipped days are neutral, and an unfinished today/period never breaks the streak.
     */
    fun streak(
        habit: Habit,
        progress: Map<LocalDate, DayProgress>,
        today: LocalDate,
        firstDayOfWeek: DayOfWeek,
    ): StreakInfo {
        if (habit.type == HabitType.QUIT) return StreakInfo(0, 0, StreakUnit.DAY)
        var run = 0
        var best = 0
        outcomes(habit, progress, today, firstDayOfWeek).forEach { (_, outcome) ->
            when (outcome) {
                Outcome.MET -> {
                    run++
                    best = maxOf(best, run)
                }
                Outcome.MISSED -> run = 0
                Outcome.NEUTRAL -> Unit
            }
        }
        return StreakInfo(current = run, longest = best, unit = unitOf(habit))
    }

    /** Every streak run (longest first), for the "all streaks" history. */
    fun streakRuns(
        habit: Habit,
        progress: Map<LocalDate, DayProgress>,
        today: LocalDate,
        firstDayOfWeek: DayOfWeek,
    ): List<StreakRun> {
        if (habit.type == HabitType.QUIT) return emptyList()
        val runs = mutableListOf<StreakRun>()
        var start: LocalDate? = null
        var end: LocalDate? = null
        var length = 0
        outcomes(habit, progress, today, firstDayOfWeek).forEach { (date, outcome) ->
            when (outcome) {
                Outcome.MET -> {
                    if (start == null) start = date
                    end = date
                    length++
                }
                Outcome.MISSED -> {
                    if (start != null) runs += StreakRun(start!!, end!!, length)
                    start = null
                    length = 0
                }
                Outcome.NEUTRAL -> Unit
            }
        }
        if (start != null) runs += StreakRun(start!!, end!!, length)
        return runs.sortedWith(compareByDescending<StreakRun> { it.length }.thenByDescending { it.end })
    }

    fun completionRate(
        habit: Habit,
        progress: Map<LocalDate, DayProgress>,
        today: LocalDate,
        firstDayOfWeek: DayOfWeek,
    ): Float {
        val evaluated = outcomes(habit, progress, today, firstDayOfWeek).filter { it.second != Outcome.NEUTRAL }
        if (evaluated.isEmpty()) return 0f
        return evaluated.count { it.second == Outcome.MET }.toFloat() / evaluated.size
    }

    fun stats(
        habit: Habit,
        activities: List<HabitActivity>,
        today: LocalDate,
        firstDayOfWeek: DayOfWeek,
    ): HabitStats {
        val progress = progressByDay(habit, activities)
        val daysWithValues = progress.values.filter { it.entryCount > 0 }
        val outcomes = outcomes(habit, progress, today, firstDayOfWeek)
        return HabitStats(
            streak = streak(habit, progress, today, firstDayOfWeek),
            completionRate = completionRate(habit, progress, today, firstDayOfWeek),
            totalCompletions = outcomes.count { it.second == Outcome.MET },
            totalValue = daysWithValues.sumOf { it.total },
            averageValue = if (daysWithValues.isEmpty()) 0.0 else daysWithValues.sumOf { it.total } / daysWithValues.size,
            bestDayValue = daysWithValues.maxOfOrNull { it.total } ?: 0.0,
            activityCount = activities.count { it.habitId == habit.id && !it.skipped },
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
