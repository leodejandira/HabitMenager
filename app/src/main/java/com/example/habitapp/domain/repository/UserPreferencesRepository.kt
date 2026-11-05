package com.example.habitapp.domain.repository

import com.example.habitapp.domain.model.CalendarView
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

    suspend fun setDefaultCalendarView(view: CalendarView)

    suspend fun setHideCompletedHabits(hide: Boolean)

    suspend fun setShowOverdueOnToday(show: Boolean)
}

/** Backup, restore and reset of all user data. [uri] is a content URI from the system file picker. */
interface DataManagementRepository {
    suspend fun exportTo(uri: String): DataSummary
    suspend fun importFrom(uri: String): DataSummary
    suspend fun resetAll()
}

data class DataSummary(val habits: Int, val activities: Int, val tasks: Int, val journalEntries: Int)

/** Thrown when an import file is not a valid backup; existing data is left untouched. */
class InvalidBackupException(val reason: Reason, detail: String? = null) : Exception(detail) {
    enum class Reason { FORMAT, VERSION, INTEGRITY }
}
