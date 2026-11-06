package com.example.habitapp.domain.util

import com.example.habitapp.domain.model.Habit
import com.example.habitapp.domain.model.HabitActivity
import com.example.habitapp.domain.model.HabitType
import com.example.habitapp.domain.model.RecurrenceType
import com.example.habitapp.domain.model.Task
import com.example.habitapp.domain.model.TaskRecurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class TaskRecurrenceTest {

    private fun task(anchor: LocalDate, type: RecurrenceType, weekdays: Set<DayOfWeek> = emptySet(), until: LocalDate? = null) = Task(
        id = "t",
        listId = "l",
        title = "Task",
        dueDate = anchor,
        recurrence = TaskRecurrence(type, weekdays, until),
        createdAt = Instant.EPOCH,
    )

    @Test
    fun `upcoming lists the next 7 days once per series and skips done and later tasks`() {
        val today = LocalDate.of(2026, 11, 6)
        val daily = task(today, RecurrenceType.DAILY).copy(id = "daily", title = "Daily")
        val inThree = Task(id = "a", listId = "l", title = "In three days", dueDate = today.plusDays(3), createdAt = Instant.EPOCH)
        val inSix = inThree.copy(id = "b", title = "Last day of window", dueDate = today.plusDays(6))
        val inSeven = inThree.copy(id = "c", title = "Outside window", dueDate = today.plusDays(7))
        val done = inThree.copy(id = "d", title = "Done", completedAt = Instant.EPOCH)
        val data = com.example.habitapp.domain.repository.TaskData(
            tasks = listOf(daily, inThree, inSix, inSeven, done),
            completions = mapOf("daily" to mapOf(today to Instant.EPOCH)),
            exceptions = emptyMap(),
        )
        val result = com.example.habitapp.domain.usecase.TaskQueries.upcoming(data, today, 7)
        assertEquals(listOf("daily", "a", "b"), result.map { it.task.id })
        // Today's occurrence of the daily series is done, so the next open one (tomorrow) represents it.
        assertEquals(today.plusDays(1), result.first().date)
    }

    @Test
    fun `daily recurrence never occurs before the anchor`() {
        val t = task(LocalDate.of(2026, 11, 5), RecurrenceType.DAILY)
        assertFalse(TaskRecurrenceCalculator.occursOn(t, LocalDate.of(2026, 11, 4)))
        assertTrue(TaskRecurrenceCalculator.occursOn(t, LocalDate.of(2026, 11, 6)))
    }

    @Test
    fun `biweekly recurrence skips alternate weeks`() {
        val t = task(LocalDate.of(2026, 11, 2), RecurrenceType.BIWEEKLY)
        val dates = TaskRecurrenceCalculator.occurrencesBetween(t, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 1))
        assertEquals(listOf(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 30)), dates)
    }

    @Test
    fun `monthly recurrence on the 31st falls back to the last day of shorter months`() {
        val t = task(LocalDate.of(2026, 1, 31), RecurrenceType.MONTHLY)
        assertTrue(TaskRecurrenceCalculator.occursOn(t, LocalDate.of(2026, 2, 28)))
        assertTrue(TaskRecurrenceCalculator.occursOn(t, LocalDate.of(2026, 4, 30)))
        assertFalse(TaskRecurrenceCalculator.occursOn(t, LocalDate.of(2026, 3, 30)))
    }

    @Test
    fun `yearly recurrence on February 29 uses February 28 in common years`() {
        val t = task(LocalDate.of(2028, 2, 29), RecurrenceType.YEARLY)
        assertTrue(TaskRecurrenceCalculator.occursOn(t, LocalDate.of(2029, 2, 28)))
        assertTrue(TaskRecurrenceCalculator.occursOn(t, LocalDate.of(2032, 2, 29)))
    }

    @Test
    fun `custom weekdays recurrence and until date`() {
        val t = task(LocalDate.of(2026, 11, 2), RecurrenceType.WEEKDAYS, setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY), until = LocalDate.of(2026, 11, 12))
        val dates = TaskRecurrenceCalculator.occurrencesBetween(t, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))
        assertEquals(listOf(3, 5, 10, 12), dates.map { it.dayOfMonth })
    }

    @Test
    fun `exceptions remove single occurrences`() {
        val t = task(LocalDate.of(2026, 11, 2), RecurrenceType.DAILY)
        val exceptions = setOf(LocalDate.of(2026, 11, 3))
        assertFalse(TaskRecurrenceCalculator.occursOn(t, LocalDate.of(2026, 11, 3), exceptions))
        assertTrue(TaskRecurrenceCalculator.occursOn(t, LocalDate.of(2026, 11, 4), exceptions))
    }

    @Test
    fun `next occurrence skips completed ones`() {
        val t = task(LocalDate.of(2026, 11, 2), RecurrenceType.DAILY)
        val done = setOf(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 6))
        assertEquals(LocalDate.of(2026, 11, 7), TaskRecurrenceCalculator.nextOccurrenceOnOrAfter(t, LocalDate.of(2026, 11, 5)) { it in done })
    }

    @Test
    fun `next occurrence is null after the series ended`() {
        val t = task(LocalDate.of(2026, 11, 2), RecurrenceType.WEEKLY, until = LocalDate.of(2026, 11, 10))
        assertNull(TaskRecurrenceCalculator.nextOccurrenceOnOrAfter(t, LocalDate.of(2026, 11, 11)))
    }
}

class QuitCalculatorTest {

    private val start = Instant.parse("2026-10-01T08:00:00Z")
    private val habit = Habit(
        id = "q",
        name = "No smoking",
        type = HabitType.QUIT,
        startDate = LocalDate.of(2026, 10, 1),
        quitStartedAt = start,
        createdAt = start,
    )

    private fun relapse(at: Instant) = HabitActivity("r$at", "q", at.atZone(ZoneOffset.UTC).toLocalDate(), 1.0, 1.0, false, null, at)

    @Test
    fun `elapsed time is measured from the quit moment`() {
        val now = start.plus(Duration.ofDays(3).plusHours(5))
        val status = QuitCalculator.status(habit, emptyList(), now, ZoneOffset.UTC)
        assertEquals(3, status.elapsed.toDays())
        assertEquals(7, status.nextMilestoneDays)
        assertEquals(0, status.relapseCount)
    }

    @Test
    fun `a relapse restarts the counter but keeps the longest run`() {
        val relapseAt = start.plus(Duration.ofDays(10))
        val now = relapseAt.plus(Duration.ofDays(2))
        val status = QuitCalculator.status(habit, listOf(relapse(relapseAt)), now, ZoneOffset.UTC)
        assertEquals(2, status.elapsed.toDays())
        assertEquals(10, status.longest.toDays())
        assertEquals(1, status.relapseCount)
        assertEquals(listOf(1, 3, 7), QuitCalculator.reachedMilestones(status))
    }
}
