package com.example.habitapp.domain.util

import com.example.habitapp.domain.model.RecurrenceType
import com.example.habitapp.domain.model.Task
import java.time.LocalDate
import java.time.Month
import java.time.temporal.ChronoUnit

/** Expands recurring tasks into occurrence dates. */
object TaskRecurrenceCalculator {

    /** Upper bound when searching forward for the next occurrence. */
    private const val SEARCH_LIMIT_DAYS = 800L

    fun occursOn(task: Task, date: LocalDate, exceptions: Set<LocalDate> = emptySet()): Boolean {
        val anchor = task.dueDate ?: return false
        if (!task.recurrence.isRecurring) return date == anchor && date !in exceptions
        if (date.isBefore(anchor)) return false
        task.recurrence.until?.let { if (date.isAfter(it)) return false }
        if (date in exceptions) return false
        return when (task.recurrence.type) {
            RecurrenceType.NONE -> date == anchor
            RecurrenceType.DAILY -> true
            RecurrenceType.WEEKLY -> date.dayOfWeek == anchor.dayOfWeek
            RecurrenceType.BIWEEKLY ->
                date.dayOfWeek == anchor.dayOfWeek && (ChronoUnit.DAYS.between(anchor, date) / 7) % 2 == 0L
            RecurrenceType.MONTHLY -> date.dayOfMonth == minOf(anchor.dayOfMonth, date.lengthOfMonth())
            RecurrenceType.YEARLY -> date.month == anchor.month && date.dayOfMonth == yearlyDay(anchor, date)
            RecurrenceType.WEEKDAYS -> {
                val days = task.recurrence.weekdays.ifEmpty { setOf(anchor.dayOfWeek) }
                date.dayOfWeek in days
            }
        }
    }

    /** Feb 29 anchors fall on Feb 28 in non-leap years. */
    private fun yearlyDay(anchor: LocalDate, date: LocalDate): Int =
        if (anchor.month == Month.FEBRUARY && anchor.dayOfMonth == 29 && !date.isLeapYear) 28 else anchor.dayOfMonth

    fun occurrencesBetween(
        task: Task,
        from: LocalDate,
        to: LocalDate,
        exceptions: Set<LocalDate> = emptySet(),
    ): List<LocalDate> {
        val anchor = task.dueDate ?: return emptyList()
        if (!task.recurrence.isRecurring) {
            return if (!anchor.isBefore(from) && !anchor.isAfter(to) && anchor !in exceptions) listOf(anchor) else emptyList()
        }
        val result = mutableListOf<LocalDate>()
        var date = maxOf(from, anchor)
        val end = task.recurrence.until?.let { minOf(it, to) } ?: to
        while (!date.isAfter(end)) {
            if (occursOn(task, date, exceptions)) result += date
            date = date.plusDays(1)
        }
        return result
    }

    fun nextOccurrenceOnOrAfter(
        task: Task,
        date: LocalDate,
        exceptions: Set<LocalDate> = emptySet(),
        isCompleted: (LocalDate) -> Boolean = { false },
    ): LocalDate? {
        val anchor = task.dueDate ?: return null
        var day = maxOf(date, anchor)
        val limit = day.plusDays(SEARCH_LIMIT_DAYS)
        while (!day.isAfter(limit)) {
            task.recurrence.until?.let { if (day.isAfter(it)) return null }
            if (occursOn(task, day, exceptions) && !isCompleted(day)) return day
            day = day.plusDays(1)
        }
        return null
    }
}
