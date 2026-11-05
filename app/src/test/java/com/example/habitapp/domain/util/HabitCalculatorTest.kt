package com.example.habitapp.domain.util

import com.example.habitapp.domain.model.FrequencyType
import com.example.habitapp.domain.model.Habit
import com.example.habitapp.domain.model.HabitActivity
import com.example.habitapp.domain.model.HabitFrequency
import com.example.habitapp.domain.model.HabitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class HabitCalculatorTest {

    // 2026-11-05 is a Thursday.
    private val today = LocalDate.of(2026, 11, 5)
    private val monday = DayOfWeek.MONDAY
    private var counter = 0

    private fun habit(
        type: HabitType = HabitType.BOOLEAN,
        target: Double = 1.0,
        frequency: HabitFrequency = HabitFrequency(),
        start: LocalDate = today.minusDays(30),
    ) = Habit(
        id = "h",
        name = "Test",
        type = type,
        target = target,
        frequency = frequency,
        startDate = start,
        createdAt = Instant.EPOCH,
    )

    private fun entry(date: LocalDate, value: Double = 1.0, target: Double = 1.0, skipped: Boolean = false) = HabitActivity(
        id = "a${counter++}",
        habitId = "h",
        date = date,
        value = value,
        targetSnapshot = target,
        skipped = skipped,
        loggedAt = date.atTime(12, 0).toInstant(ZoneOffset.UTC).plusSeconds(counter.toLong()),
    )

    private fun streak(h: Habit, entries: List<HabitActivity>) =
        HabitCalculator.streak(h, HabitCalculator.progressByDay(h, entries), today, monday)

    @Test
    fun `daily streak counts consecutive completed days`() {
        val h = habit()
        val entries = (0L..4L).map { entry(today.minusDays(it)) }
        assertEquals(5, streak(h, entries).current)
    }

    @Test
    fun `today not yet done does not break the streak`() {
        val h = habit()
        val entries = (1L..3L).map { entry(today.minusDays(it)) }
        assertEquals(3, streak(h, entries).current)
    }

    @Test
    fun `a missed past day resets the current streak but keeps the longest`() {
        val h = habit()
        val entries = (5L..9L).map { entry(today.minusDays(it)) } + (1L..2L).map { entry(today.minusDays(it)) }
        val s = streak(h, entries)
        assertEquals(2, s.current)
        assertEquals(5, s.longest)
    }

    @Test
    fun `unscheduled weekdays do not break a specific-days streak`() {
        // Mon, Wed, Fri habit; completed on the last three scheduled days only.
        val h = habit(frequency = HabitFrequency(FrequencyType.WEEKDAYS, setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)), start = LocalDate.of(2026, 10, 26))
        val entries = listOf(LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 4)).map { entry(it) }
        // Oct 26 and 28 were missed, Oct 30 / Nov 2 / Nov 4 done; Nov 5 (Thu) is not scheduled.
        assertEquals(3, streak(h, entries).current)
    }

    @Test
    fun `skipped days neither count nor break the streak`() {
        val h = habit()
        val entries = listOf(entry(today.minusDays(1)), entry(today.minusDays(2), value = 0.0, skipped = true), entry(today.minusDays(3)))
        assertEquals(2, streak(h, entries).current)
    }

    @Test
    fun `numeric habit completes only when the target is reached`() {
        val h = habit(HabitType.NUMERIC, target = 2000.0)
        val entries = listOf(entry(today, 1500.0, 2000.0))
        val progress = HabitCalculator.progressByDay(h, entries)
        assertFalse(progress.getValue(today).completed)
        val more = entries + entry(today, 500.0, 2000.0)
        assertTrue(HabitCalculator.progressByDay(h, more).getValue(today).completed)
    }

    @Test
    fun `negative corrections never push the total below zero`() {
        val h = habit(HabitType.COUNT, target = 10.0)
        val entries = listOf(entry(today, 3.0, 10.0), entry(today, -5.0, 10.0))
        assertEquals(0.0, HabitCalculator.progressByDay(h, entries).getValue(today).total, 0.0)
    }

    @Test
    fun `historical days keep the target that was in force when logged`() {
        // Yesterday 1000 ml was logged when the goal was 1000; the goal is now 2000.
        val h = habit(HabitType.NUMERIC, target = 2000.0)
        val entries = listOf(entry(today.minusDays(1), 1000.0, target = 1000.0))
        assertTrue(HabitCalculator.progressByDay(h, entries).getValue(today.minusDays(1)).completed)
    }

    @Test
    fun `weekly target streak counts weeks that met the quota`() {
        val h = habit(frequency = HabitFrequency(FrequencyType.TIMES_PER_WEEK, timesPerPeriod = 2), start = LocalDate.of(2026, 10, 12))
        // Weeks start Monday: Oct 12, Oct 19, Oct 26, Nov 2 (current).
        val entries = listOf(
            LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 15), // week 1: 2 ✓
            LocalDate.of(2026, 10, 20), // week 2: 1 ✗
            LocalDate.of(2026, 10, 27), LocalDate.of(2026, 10, 29), // week 3: 2 ✓
            LocalDate.of(2026, 11, 3), // current week: 1 (pending)
        ).map { entry(it) }
        val s = streak(h, entries)
        assertEquals(StreakUnit.WEEK, s.unit)
        assertEquals(1, s.current)
        assertEquals(1, s.longest)
    }

    @Test
    fun `monthly target streak spans consecutive months`() {
        val h = habit(frequency = HabitFrequency(FrequencyType.TIMES_PER_MONTH, timesPerPeriod = 2), start = LocalDate.of(2026, 9, 1))
        val entries = listOf(
            LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 10),
            LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 20),
            LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 4),
        ).map { entry(it) }
        val s = streak(h, entries)
        assertEquals(StreakUnit.MONTH, s.unit)
        assertEquals(3, s.current)
    }

    @Test
    fun `completion rate ignores today while pending and skipped days`() {
        val h = habit(start = today.minusDays(4))
        // Days -4..-1: done, skipped, missed, done. Today pending.
        val entries = listOf(entry(today.minusDays(4)), entry(today.minusDays(3), 0.0, skipped = true), entry(today.minusDays(1)))
        val rate = HabitCalculator.completionRate(h, HabitCalculator.progressByDay(h, entries), today, monday)
        assertEquals(2f / 3f, rate, 0.0001f)
    }

    @Test
    fun `flexible habit stops counting toward daily progress once the quota is met`() {
        val h = habit(frequency = HabitFrequency(FrequencyType.TIMES_PER_WEEK, timesPerPeriod = 2), start = LocalDate.of(2026, 10, 1))
        val entries = listOf(entry(LocalDate.of(2026, 11, 2)), entry(LocalDate.of(2026, 11, 3)))
        val progress = HabitCalculator.progressByDay(h, entries)
        assertFalse(HabitCalculator.countsTowardDailyProgress(h, progress, today, monday))
        assertTrue(HabitCalculator.countsTowardDailyProgress(h, progress, LocalDate.of(2026, 11, 3), monday))
    }

    @Test
    fun `habit is not scheduled before start or after end`() {
        val h = habit(start = today).copy(endDate = today.plusDays(2))
        assertFalse(HabitCalculator.isScheduledOn(h, today.minusDays(1)))
        assertTrue(HabitCalculator.isScheduledOn(h, today.plusDays(2)))
        assertFalse(HabitCalculator.isScheduledOn(h, today.plusDays(3)))
    }

    @Test
    fun `stats report totals averages and best day`() {
        val h = habit(HabitType.NUMERIC, target = 10.0)
        val entries = listOf(entry(today.minusDays(2), 4.0, 10.0), entry(today.minusDays(1), 12.0, 10.0), entry(today, 8.0, 10.0))
        val stats = HabitCalculator.stats(h, entries, today, monday)
        assertEquals(24.0, stats.totalValue, 0.0)
        assertEquals(8.0, stats.averageValue, 0.0)
        assertEquals(12.0, stats.bestDayValue, 0.0)
        assertEquals(1, stats.totalCompletions)
    }
}
