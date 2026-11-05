package com.example.habitapp.presentation.settings

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.habitapp.R
import com.example.habitapp.domain.model.ThemeMode
import com.example.habitapp.domain.model.UserPreferences
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

sealed interface SettingsUiState {
    data object Loading : SettingsUiState

    data class Ready(
        val preferences: UserPreferences,
        @param:StringRes val userMessage: Int? = null,
    ) : SettingsUiState

    data object Error : SettingsUiState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val userMessage = MutableStateFlow<Int?>(null)

    private val readyState: Flow<SettingsUiState> = combine(
        preferencesRepository.preferences,
        userMessage,
    ) { preferences, message -> SettingsUiState.Ready(preferences, message) }

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

    fun onDisplayNameSaved(name: String) = update(successMessage = R.string.settings_saved) {
        preferencesRepository.setDisplayName(name)
    }

    fun onUserMessageShown() {
        userMessage.value = null
    }

    private fun update(@StringRes successMessage: Int? = null, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                if (successMessage != null) userMessage.value = successMessage
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Log.e(TAG, "Failed to save setting", error)
                userMessage.value = R.string.settings_error_save
            }
        }
    }

    private companion object {
        const val TAG = "SettingsViewModel"
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
