package com.example.habitapp.testing

import com.example.habitapp.domain.model.CalendarView
import com.example.habitapp.domain.model.ThemeMode
import com.example.habitapp.domain.model.UserPreferences
import com.example.habitapp.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.io.IOException
import java.time.DayOfWeek

/** In-memory test double. Set [failWrites] to simulate a disk error on save. */
class FakeUserPreferencesRepository(
    initial: UserPreferences = UserPreferences(),
) : UserPreferencesRepository {

    private val state = MutableStateFlow(initial)
    var failWrites: Boolean = false

    override val preferences: Flow<UserPreferences> = state

    override suspend fun setThemeMode(mode: ThemeMode) = write { it.copy(themeMode = mode) }

    override suspend fun setUseDynamicColor(enabled: Boolean) = write { it.copy(useDynamicColor = enabled) }

    override suspend fun setFirstDayOfWeek(day: DayOfWeek?) = write { it.copy(firstDayOfWeek = day) }

    override suspend fun setDisplayName(name: String) =
        write { it.copy(displayName = name.trim().take(UserPreferences.MAX_DISPLAY_NAME_LENGTH)) }

    override suspend fun setDefaultCalendarView(view: CalendarView) = write { it.copy(defaultCalendarView = view) }

    override suspend fun setHideCompletedHabits(hide: Boolean) = write { it.copy(hideCompletedHabits = hide) }

    override suspend fun setShowOverdueOnToday(show: Boolean) = write { it.copy(showOverdueOnToday = show) }

    private fun write(transform: (UserPreferences) -> UserPreferences) {
        if (failWrites) throw IOException("Simulated write failure")
        state.update(transform)
    }
}
