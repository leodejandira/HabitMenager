package com.example.habitapp.presentation.settings

import app.cash.turbine.test
import com.example.habitapp.R
import com.example.habitapp.domain.model.ThemeMode
import com.example.habitapp.domain.model.UserPreferences
import com.example.habitapp.testing.FakeUserPreferencesRepository
import com.example.habitapp.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek

class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeUserPreferencesRepository()
    private val viewModel = SettingsViewModel(repository)

    @Test
    fun `starts loading then emits stored preferences`() = runTest {
        viewModel.uiState.test {
            val state = awaitItem()
            // With an unconfined dispatcher the Ready state can arrive immediately.
            val ready = if (state is SettingsUiState.Loading) awaitItem() else state
            assertEquals(SettingsUiState.Ready(UserPreferences()), ready)
        }
    }

    @Test
    fun `selecting a theme updates state`() = runTest {
        viewModel.uiState.test {
            skipUntilReady()
            viewModel.onThemeModeSelected(ThemeMode.DARK)
            val ready = awaitItem() as SettingsUiState.Ready
            assertEquals(ThemeMode.DARK, ready.preferences.themeMode)
        }
    }

    @Test
    fun `selecting week start updates state`() = runTest {
        viewModel.uiState.test {
            skipUntilReady()
            viewModel.onFirstDayOfWeekSelected(DayOfWeek.MONDAY)
            val ready = awaitItem() as SettingsUiState.Ready
            assertEquals(DayOfWeek.MONDAY, ready.preferences.firstDayOfWeek)
        }
    }

    @Test
    fun `saving a name shows a confirmation that can be dismissed`() = runTest {
        viewModel.uiState.test {
            skipUntilReady()
            viewModel.onDisplayNameSaved("  Ana ")

            // Name update and confirmation message may arrive as separate emissions.
            var ready = awaitItem() as SettingsUiState.Ready
            while (ready.preferences.displayName != "Ana" || ready.userMessage == null) {
                ready = awaitItem() as SettingsUiState.Ready
            }
            assertEquals("Ana", ready.preferences.displayName)
            assertEquals(R.string.settings_saved, ready.userMessage)

            viewModel.onUserMessageShown()
            assertNull((awaitItem() as SettingsUiState.Ready).userMessage)
        }
    }

    @Test
    fun `failed write surfaces an error message and keeps old value`() = runTest {
        repository.failWrites = true
        viewModel.uiState.test {
            skipUntilReady()
            viewModel.onThemeModeSelected(ThemeMode.LIGHT)

            val ready = awaitItem() as SettingsUiState.Ready
            assertEquals(R.string.settings_error_save, ready.userMessage)
            assertEquals(ThemeMode.SYSTEM, ready.preferences.themeMode)
        }
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<SettingsUiState>.skipUntilReady() {
        var state = awaitItem()
        while (state !is SettingsUiState.Ready) state = awaitItem()
    }
}
