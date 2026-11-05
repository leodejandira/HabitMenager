package com.example.habitapp.domain.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject

/**
 * Single source of "now" for the whole app.
 *
 * Time strategy (applies to every phase):
 * - Moments in time (activity logged, task completed, timer started) are stored as
 *   UTC [Instant]s (epoch millis) together with the [ZoneId] that was active, so history
 *   never shifts when the user travels.
 * - Calendar concepts (a habit's day, a task's due date) are stored as [LocalDate]
 *   values, never as midnight timestamps, which avoids UTC/local off-by-one errors.
 * - The zone is read on every call instead of being cached, so a time-zone change
 *   while the process is alive is picked up immediately.
 */
interface TimeProvider {
    fun zone(): ZoneId

    fun nowInstant(): Instant

    fun now(): ZonedDateTime = ZonedDateTime.ofInstant(nowInstant(), zone())

    fun today(): LocalDate = now().toLocalDate()
}

class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun zone(): ZoneId = ZoneId.systemDefault()

    override fun nowInstant(): Instant = Instant.now()
}

/** Emits the current time immediately and then at the start of every minute. */
fun TimeProvider.minuteTicks(): Flow<ZonedDateTime> = flow {
    while (true) {
        val current = now()
        emit(current)
        val elapsedInMinuteMillis = current.second * 1_000L + current.nano / 1_000_000L
        delay((60_000L - elapsedInMinuteMillis).coerceAtLeast(1L))
    }
}

fun resolveFirstDayOfWeek(preference: DayOfWeek?, locale: Locale = Locale.getDefault()): DayOfWeek =
    preference ?: WeekFields.of(locale).firstDayOfWeek
