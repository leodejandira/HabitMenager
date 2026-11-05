package com.example.habitapp.domain.util

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class TimeProviderTest {

    private class FixedTimeProvider(
        private val instant: Instant,
        private val zoneId: ZoneId,
    ) : TimeProvider {
        override fun zone(): ZoneId = zoneId
        override fun nowInstant(): Instant = instant
    }

    @Test
    fun `today uses the provider zone, not UTC`() {
        // 02:30 UTC on 5 Nov is still 4 Nov in São Paulo (UTC-3).
        val instant = Instant.parse("2026-11-05T02:30:00Z")
        val saoPaulo = FixedTimeProvider(instant, ZoneId.of("America/Sao_Paulo"))
        val tokyo = FixedTimeProvider(instant, ZoneId.of("Asia/Tokyo"))

        assertEquals(LocalDate.of(2026, 11, 4), saoPaulo.today())
        assertEquals(LocalDate.of(2026, 11, 5), tokyo.today())
    }

    @Test
    fun `explicit week start wins over locale`() {
        assertEquals(DayOfWeek.SATURDAY, resolveFirstDayOfWeek(DayOfWeek.SATURDAY, Locale.US))
    }

    @Test
    fun `null week start follows the locale`() {
        assertEquals(DayOfWeek.SUNDAY, resolveFirstDayOfWeek(null, Locale.US))
        assertEquals(DayOfWeek.MONDAY, resolveFirstDayOfWeek(null, Locale.GERMANY))
    }

    @Test
    fun `minute ticker emits the current time first`() = runTest {
        val instant = Instant.parse("2026-11-05T12:00:30Z")
        val provider = FixedTimeProvider(instant, ZoneId.of("UTC"))

        val first = provider.minuteTicks().first()

        assertEquals(instant, first.toInstant())
    }

    @Test
    fun `minute ticker keeps emitting on virtual time`() = runTest {
        val provider = FixedTimeProvider(Instant.parse("2026-11-05T12:00:00Z"), ZoneId.of("UTC"))

        val ticks = provider.minuteTicks().take(3).toList()

        assertEquals(3, ticks.size)
    }
}
