package com.example.habitapp.presentation.dashboard

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.habitapp.domain.model.Routine
import com.example.habitapp.domain.model.TaskOccurrence
import com.example.habitapp.domain.repository.HabitRepository
import com.example.habitapp.domain.repository.RoutineRepository
import com.example.habitapp.domain.repository.TaskRepository
import com.example.habitapp.domain.repository.UserPreferencesRepository
import com.example.habitapp.domain.usecase.DayOverview
import com.example.habitapp.domain.usecase.ObserveDayOverviewUseCase
import com.example.habitapp.domain.util.DayPeriod
import com.example.habitapp.domain.util.TimeProvider
import com.example.habitapp.domain.util.minuteTicks
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

@Immutable
data class DashboardHeader(
    val date: LocalDate,
    val dayPeriod: DayPeriod,
    val displayName: String,
)

data class RoutineProgress(val routine: Routine, val completedSteps: Int, val totalSteps: Int)

sealed interface DashboardUiState {
    data object Loading : DashboardUiState

    data class Ready(
        val header: DashboardHeader,
        val today: LocalDate,
        val overview: DayOverview,
        val routines: List<RoutineProgress>,
        val hasAnyHabit: Boolean,
        val hiddenCompleted: Int,
        /** Unfiltered overview, used for progress numbers. */
        val fullOverview: DayOverview,
    ) : DashboardUiState

    data object Error : DashboardUiState
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    preferencesRepository: UserPreferencesRepository,
    private val habitRepository: HabitRepository,
    private val taskRepository: TaskRepository,
    routineRepository: RoutineRepository,
    observeDayOverview: ObserveDayOverviewUseCase,
    private val time: TimeProvider,
) : ViewModel() {

    /** Offset in days from today of the day being shown (survives process death). */
    private val dayOffset: StateFlow<Long> = savedStateHandle.getStateFlow(KEY_OFFSET, 0L)

    private val ticks = time.minuteTicks()

    private val selectedDate: Flow<LocalDate> = combine(ticks.map { it.toLocalDate() }.distinctUntilChanged(), dayOffset) { today, offset ->
        today.plusDays(offset)
    }.distinctUntilChanged()

    private val readyState: Flow<DashboardUiState> = combine(
        preferencesRepository.preferences,
        ticks,
        observeDayOverview(selectedDate),
        routineRepository.observeRoutines(),
        habitRepository.observeHabits(),
    ) { preferences, now, overview, routines, habits ->
        val habitItems = overview.habits.associateBy { it.habit.id }
        DashboardUiState.Ready(
            header = DashboardHeader(
                date = now.toLocalDate(),
                dayPeriod = DayPeriod.of(now.toLocalTime()),
                displayName = preferences.displayName,
            ),
            today = now.toLocalDate(),
            overview = overview.copy(
                habits = if (preferences.hideCompletedHabits) overview.habits.filter { !it.done } else overview.habits,
                overdueTasks = if (preferences.showOverdueOnToday) overview.overdueTasks else emptyList(),
            ),
            hiddenCompleted = if (preferences.hideCompletedHabits) overview.habits.count { it.done } else 0,
            fullOverview = overview,
            routines = routines.mapNotNull { routine ->
                val steps = routine.habitIds.mapNotNull { habitItems[it] }
                if (steps.isEmpty()) null else RoutineProgress(routine, steps.count { it.done }, steps.size)
            },
            hasAnyHabit = habits.any { !it.archived },
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

    fun shiftDay(delta: Long) {
        savedStateHandle[KEY_OFFSET] = (dayOffset.value + delta).coerceAtMost(MAX_FUTURE_DAYS)
    }

    fun goToToday() {
        savedStateHandle[KEY_OFFSET] = 0L
    }

    private fun currentDate(): LocalDate = time.today().plusDays(dayOffset.value)

    fun toggleHabit(habitId: String) = launch { habitRepository.toggleCompletion(habitId, currentDate()) }

    fun addProgress(habitId: String, amount: Double, note: String? = null) =
        launch { habitRepository.addProgress(habitId, currentDate(), amount, note) }

    fun toggleTask(occurrence: TaskOccurrence) =
        launch { taskRepository.setOccurrenceCompleted(occurrence.task.id, occurrence.date, !occurrence.completed) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Log.e(TAG, "Dashboard action failed", error)
            }
        }
    }

    private companion object {
        const val TAG = "DashboardViewModel"
        const val KEY_OFFSET = "day_offset"
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val MAX_FUTURE_DAYS = 0L
    }
}
