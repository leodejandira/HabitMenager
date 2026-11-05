package com.example.habitapp.domain.util

import java.time.LocalTime

/** Part of the day used to pick the dashboard greeting. */
enum class DayPeriod {
    MORNING,
    AFTERNOON,
    EVENING,
    NIGHT;

    companion object {
        fun of(time: LocalTime): DayPeriod = when (time.hour) {
            in 5..11 -> MORNING
            in 12..17 -> AFTERNOON
            in 18..21 -> EVENING
            else -> NIGHT
        }
    }
}
