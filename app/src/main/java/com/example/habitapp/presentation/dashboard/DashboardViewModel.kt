package com.example.habitapp.presentation.dashboard

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.habitapp.domain.repository.UserPreferencesRepository
import com.example.habitapp.domain.util.DayPeriod
import com.example.habitapp.domain.util.TimeProvider
import com.example.habitapp.domain.util.minuteTicks
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

@Immutable
data class DashboardHeader(
    val date: LocalDate,
    val dayPeriod: DayPeriod,
    val displayName: String,
)

sealed interface DashboardUiState {
    data object Loading : DashboardUiState

    /** Habit and task sections are added to this state in Phase 4. */
    data class Ready(val header: DashboardHeader) : DashboardUiState

    data object Error : DashboardUiState
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    preferencesRepository: UserPreferencesRepository,
    timeProvider: TimeProvider,
) : ViewModel() {

    private val readyState: Flow<DashboardUiState> = combine(
        preferencesRepository.preferences,
        timeProvider.minuteTicks(),
    ) { preferences, now ->
        DashboardUiState.Ready(
            header = DashboardHeader(
                date = now.toLocalDate(),
                dayPeriod = DayPeriod.of(now.toLocalTime()),
                displayName = preferences.displayName,
            ),
        )
    }

    val uiState: StateFlow<DashboardUiState> = readyState
        .catch { error ->
            Log.e(TAG, "Failed to build dashboard state", error)
            emit(DashboardUiState.Error)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = DashboardUiState.Loading,
        )

    private companion object {
        const val TAG = "DashboardViewModel"
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
