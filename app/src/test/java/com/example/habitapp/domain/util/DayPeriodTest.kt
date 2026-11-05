package com.example.habitapp.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class DayPeriodTest {

    @Test
    fun `boundaries map to the expected period`() {
        val expectations = mapOf(
            LocalTime.of(4, 59) to DayPeriod.NIGHT,
            LocalTime.of(5, 0) to DayPeriod.MORNING,
            LocalTime.of(11, 59) to DayPeriod.MORNING,
            LocalTime.of(12, 0) to DayPeriod.AFTERNOON,
            LocalTime.of(17, 59) to DayPeriod.AFTERNOON,
            LocalTime.of(18, 0) to DayPeriod.EVENING,
            LocalTime.of(21, 59) to DayPeriod.EVENING,
            LocalTime.of(22, 0) to DayPeriod.NIGHT,
            LocalTime.MIDNIGHT to DayPeriod.NIGHT,
        )
        expectations.forEach { (time, expected) ->
            assertEquals("Unexpected period for $time", expected, DayPeriod.of(time))
        }
    }
}
