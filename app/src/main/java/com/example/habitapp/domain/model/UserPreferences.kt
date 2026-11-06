package com.example.habitapp.domain.model

import java.time.DayOfWeek

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class CalendarView { MONTH, WEEK, DAY }

/** How habits are drawn on the home screen. */
enum class HabitDisplay { COMPACT, WEEKLY, MONTHLY, YEARLY }

enum class FontSize(val scale: Float) { SMALL(0.9f), DEFAULT(1f), LARGE(1.15f) }

/**
 * App-wide user preferences persisted with DataStore.
 *
 * @property firstDayOfWeek `null` means "follow the device region"; resolve it with
 * [com.example.habitapp.domain.util.resolveFirstDayOfWeek].
 */
data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = false,
    val firstDayOfWeek: DayOfWeek? = null,
    val displayName: String = "",
    val defaultCalendarView: CalendarView = CalendarView.MONTH,
    /** Hide habits already completed from the Today list (they still count toward progress). */
    val hideCompletedHabits: Boolean = false,
    val showOverdueOnToday: Boolean = true,
    val habitDisplay: HabitDisplay = HabitDisplay.COMPACT,
    val fontSize: FontSize = FontSize.DEFAULT,
    /** True when the timer opens in countdown mode instead of stopwatch. */
    val timerDefaultCountdown: Boolean = false,
) {
    companion object {
        const val MAX_DISPLAY_NAME_LENGTH = 40
    }
}
