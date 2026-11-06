package com.example.habitapp.domain.model

import java.time.Instant
import java.time.LocalDate

data class JournalEntry(
    val id: String,
    val date: LocalDate,
    val title: String,
    val content: String,
    val mood: Int?,
    val bookmarked: Boolean,
    val habitId: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

enum class JournalFilter { ALL, DAILY, HABIT_NOTES, BOOKMARKED }

enum class TimerMode { STOPWATCH, COUNTDOWN }

data class TimerSession(
    val id: String,
    val habitId: String,
    val mode: TimerMode,
    val countdownMillis: Long?,
    val runningSince: Instant?,
    val accumulatedMillis: Long,
    val startedAt: Instant,
    val finishedAt: Instant? = null,
) {
    val isRunning: Boolean get() = runningSince != null

    /** Elapsed time computed from stored instants, so it is correct after process death. */
    fun elapsedMillis(now: Instant): Long {
        val running = runningSince?.let { (now.toEpochMilli() - it.toEpochMilli()).coerceAtLeast(0) } ?: 0L
        return accumulatedMillis + running
    }

    fun remainingMillis(now: Instant): Long? =
        countdownMillis?.let { (it - elapsedMillis(now)).coerceAtLeast(0) }
}
