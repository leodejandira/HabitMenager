package com.example.habitapp.data.preferences

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.habitapp.domain.model.CalendarView
import com.example.habitapp.domain.model.ThemeMode
import com.example.habitapp.domain.model.UserPreferences
import com.example.habitapp.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.DayOfWeek
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreUserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : UserPreferencesRepository {

    override val preferences: Flow<UserPreferences> = dataStore.data
        .catch { error ->
            // A read failure must not crash the UI: fall back to defaults and log it.
            if (error is IOException) {
                Log.e(TAG, "Failed to read preferences, using defaults", error)
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { it.toUserPreferences() }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    override suspend fun setUseDynamicColor(enabled: Boolean) {
        dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    override suspend fun setFirstDayOfWeek(day: DayOfWeek?) {
        dataStore.edit { prefs ->
            if (day == null) prefs.remove(Keys.FIRST_DAY_OF_WEEK) else prefs[Keys.FIRST_DAY_OF_WEEK] = day.value
        }
    }

    override suspend fun setDisplayName(name: String) {
        val sanitized = name.trim().take(UserPreferences.MAX_DISPLAY_NAME_LENGTH)
        dataStore.edit { it[Keys.DISPLAY_NAME] = sanitized }
    }

    override suspend fun setDefaultCalendarView(view: CalendarView) {
        dataStore.edit { it[Keys.CALENDAR_VIEW] = view.name }
    }

    override suspend fun setHideCompletedHabits(hide: Boolean) {
        dataStore.edit { it[Keys.HIDE_COMPLETED] = hide }
    }

    override suspend fun setShowOverdueOnToday(show: Boolean) {
        dataStore.edit { it[Keys.SHOW_OVERDUE] = show }
    }

    private fun Preferences.toUserPreferences(): UserPreferences = UserPreferences(
        themeMode = this[Keys.THEME_MODE]
            ?.let { stored -> ThemeMode.entries.firstOrNull { it.name == stored } }
            ?: ThemeMode.SYSTEM,
        useDynamicColor = this[Keys.DYNAMIC_COLOR] ?: false,
        firstDayOfWeek = this[Keys.FIRST_DAY_OF_WEEK]
            ?.takeIf { it in 1..7 }
            ?.let(DayOfWeek::of),
        displayName = this[Keys.DISPLAY_NAME].orEmpty(),
        defaultCalendarView = this[Keys.CALENDAR_VIEW]
            ?.let { stored -> CalendarView.entries.firstOrNull { it.name == stored } }
            ?: CalendarView.MONTH,
        hideCompletedHabits = this[Keys.HIDE_COMPLETED] ?: false,
        showOverdueOnToday = this[Keys.SHOW_OVERDUE] ?: true,
    )

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val FIRST_DAY_OF_WEEK = intPreferencesKey("first_day_of_week")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val CALENDAR_VIEW = stringPreferencesKey("default_calendar_view")
        val HIDE_COMPLETED = booleanPreferencesKey("hide_completed_habits")
        val SHOW_OVERDUE = booleanPreferencesKey("show_overdue_today")
    }

    private companion object {
        const val TAG = "UserPreferencesRepo"
    }
}
