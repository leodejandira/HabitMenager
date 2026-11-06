package com.example.habitapp.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.habitapp.domain.model.ThemeMode
import com.example.habitapp.domain.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface MainUiState {
    data object Loading : MainUiState

    data class Ready(val themeMode: ThemeMode, val useDynamicColor: Boolean, val fontScale: Float = 1f) : MainUiState
}

/** Holds the app-wide appearance settings the activity needs before drawing content. */
@HiltViewModel
class MainViewModel @Inject constructor(
    preferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val readyState: Flow<MainUiState> = preferencesRepository.preferences.map { preferences ->
        MainUiState.Ready(
            themeMode = preferences.themeMode,
            useDynamicColor = preferences.useDynamicColor,
            fontScale = preferences.fontSize.scale,
        )
    }

    val uiState: StateFlow<MainUiState> = readyState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = MainUiState.Loading,
    )
}
