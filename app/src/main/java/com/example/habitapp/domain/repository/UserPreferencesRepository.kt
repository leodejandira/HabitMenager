package com.example.habitapp.domain.repository

import com.example.habitapp.domain.model.ThemeMode
import com.example.habitapp.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek

interface UserPreferencesRepository {
    val preferences: Flow<UserPreferences>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setUseDynamicColor(enabled: Boolean)

    /** Pass `null` to follow the device region's first day of the week. */
    suspend fun setFirstDayOfWeek(day: DayOfWeek?)

    /** The name is trimmed and truncated to [UserPreferences.MAX_DISPLAY_NAME_LENGTH]. */
    suspend fun setDisplayName(name: String)
}
