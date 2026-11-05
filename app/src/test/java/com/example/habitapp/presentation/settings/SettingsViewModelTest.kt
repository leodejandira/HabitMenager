package com.example.habitapp.presentation.settings

import app.cash.turbine.test
import com.example.habitapp.R
import com.example.habitapp.domain.model.ThemeMode
import com.example.habitapp.domain.model.UserPreferences
import com.example.habitapp.domain.repository.DataManagementRepository
import com.example.habitapp.domain.repository.DataSummary
import com.example.habitapp.domain.repository.InvalidBackupException
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
    private val dataManagement = FakeDataManagement()
    private val viewModel = SettingsViewModel(repository, dataManagement)

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
            assertEquals(R.string.settings_saved, ready.userMessage?.res)

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
            assertEquals(R.string.settings_error_save, ready.userMessage?.res)
            assertEquals(ThemeMode.SYSTEM, ready.preferences.themeMode)
        }
    }

    @Test
    fun `invalid backup reports the reason and nothing else changes`() = runTest {
        dataManagement.importError = InvalidBackupException(InvalidBackupException.Reason.INTEGRITY)
        viewModel.uiState.test {
            skipUntilReady()
            viewModel.onImport("content://backup.json")
            var ready = awaitItem() as SettingsUiState.Ready
            while (ready.userMessage == null) ready = awaitItem() as SettingsUiState.Ready
            assertEquals(R.string.backup_error_integrity, ready.userMessage?.res)
            assertEquals(0, dataManagement.restores)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `successful export reports counts`() = runTest {
        viewModel.uiState.test {
            skipUntilReady()
            viewModel.onExport("content://out.json")
            var ready = awaitItem() as SettingsUiState.Ready
            while (ready.userMessage == null) ready = awaitItem() as SettingsUiState.Ready
            assertEquals(R.string.backup_exported, ready.userMessage?.res)
            assertEquals(listOf<Any>(2, 3, 4), ready.userMessage?.args)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private class FakeDataManagement : DataManagementRepository {
        var importError: Exception? = null
        var restores = 0
        override suspend fun exportTo(uri: String) = DataSummary(2, 10, 3, 4)
        override suspend fun importFrom(uri: String): DataSummary {
            importError?.let { throw it }
            restores++
            return DataSummary(1, 1, 1, 1)
        }
        override suspend fun resetAll() = Unit
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<SettingsUiState>.skipUntilReady() {
        var state = awaitItem()
        while (state !is SettingsUiState.Ready) state = awaitItem()
    }
}
