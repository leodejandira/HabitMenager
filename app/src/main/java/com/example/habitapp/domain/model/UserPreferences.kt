package com.example.habitapp.domain.model

import java.time.DayOfWeek

enum class ThemeMode { SYSTEM, LIGHT, DARK }

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
) {
    companion object {
        const val MAX_DISPLAY_NAME_LENGTH = 40
    }
}
