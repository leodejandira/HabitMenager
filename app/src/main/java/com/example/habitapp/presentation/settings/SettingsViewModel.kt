package com.example.habitapp.presentation.settings

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.habitapp.R
import com.example.habitapp.domain.model.CalendarView
import com.example.habitapp.domain.model.ThemeMode
import com.example.habitapp.domain.model.UserPreferences
import com.example.habitapp.domain.repository.DataManagementRepository
import com.example.habitapp.domain.repository.InvalidBackupException
import com.example.habitapp.domain.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/** A one-off message for the snackbar, with optional format arguments. */
data class UserMessage(@param:StringRes val res: Int, val args: List<Any> = emptyList())

sealed interface SettingsUiState {
    data object Loading : SettingsUiState

    data class Ready(
        val preferences: UserPreferences,
        val userMessage: UserMessage? = null,
        val busy: Boolean = false,
    ) : SettingsUiState

    data object Error : SettingsUiState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val dataManagement: DataManagementRepository,
) : ViewModel() {

    private val userMessage = MutableStateFlow<UserMessage?>(null)
    private val busy = MutableStateFlow(false)

    private val readyState: Flow<SettingsUiState> = combine(
        preferencesRepository.preferences,
        userMessage,
        busy,
    ) { preferences, message, isBusy -> SettingsUiState.Ready(preferences, message, isBusy) }

    val uiState: StateFlow<SettingsUiState> = readyState
        .catch { error ->
            Log.e(TAG, "Failed to load settings", error)
            emit(SettingsUiState.Error)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = SettingsUiState.Loading,
        )

    fun onThemeModeSelected(mode: ThemeMode) = update { preferencesRepository.setThemeMode(mode) }

    fun onDynamicColorToggled(enabled: Boolean) = update { preferencesRepository.setUseDynamicColor(enabled) }

    fun onFirstDayOfWeekSelected(day: DayOfWeek?) = update { preferencesRepository.setFirstDayOfWeek(day) }

    fun onDisplayNameSaved(name: String) = update(successMessage = UserMessage(R.string.settings_saved)) {
        preferencesRepository.setDisplayName(name)
    }

    fun onCalendarViewSelected(view: CalendarView) = update { preferencesRepository.setDefaultCalendarView(view) }

    fun onHideCompletedToggled(hide: Boolean) = update { preferencesRepository.setHideCompletedHabits(hide) }

    fun onShowOverdueToggled(show: Boolean) = update { preferencesRepository.setShowOverdueOnToday(show) }

    fun onExport(uri: String) = runDataOperation {
        val summary = dataManagement.exportTo(uri)
        UserMessage(R.string.backup_exported, listOf(summary.habits, summary.tasks, summary.journalEntries))
    }

    fun onImport(uri: String) = runDataOperation {
        val summary = dataManagement.importFrom(uri)
        UserMessage(R.string.backup_imported, listOf(summary.habits, summary.tasks, summary.journalEntries))
    }

    fun onReset() = runDataOperation {
        dataManagement.resetAll()
        UserMessage(R.string.reset_done)
    }

    fun onUserMessageShown() {
        userMessage.value = null
    }

    private fun runDataOperation(block: suspend () -> UserMessage) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            try {
                userMessage.value = block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (invalid: InvalidBackupException) {
                Log.w(TAG, "Invalid backup: ${invalid.message}")
                userMessage.value = UserMessage(
                    when (invalid.reason) {
                        InvalidBackupException.Reason.FORMAT -> R.string.backup_error_format
                        InvalidBackupException.Reason.VERSION -> R.string.backup_error_version
                        InvalidBackupException.Reason.INTEGRITY -> R.string.backup_error_integrity
                    },
                )
            } catch (error: Exception) {
                Log.e(TAG, "Data operation failed", error)
                userMessage.value = UserMessage(R.string.backup_error_io)
            } finally {
                busy.value = false
            }
        }
    }

    private fun update(successMessage: UserMessage? = null, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                if (successMessage != null) userMessage.value = successMessage
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Log.e(TAG, "Failed to save setting", error)
                userMessage.value = UserMessage(R.string.settings_error_save)
            }
        }
    }

    private companion object {
        const val TAG = "SettingsViewModel"
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
