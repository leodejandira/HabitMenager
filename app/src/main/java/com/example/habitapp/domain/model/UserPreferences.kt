package com.example.habitapp.domain.model

import java.time.DayOfWeek

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class CalendarView { MONTH, WEEK, DAY }

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
) {
    companion object {
        const val MAX_DISPLAY_NAME_LENGTH = 40
    }
}
