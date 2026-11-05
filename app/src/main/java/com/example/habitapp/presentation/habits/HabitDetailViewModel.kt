package com.example.habitapp.presentation.habits

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.habitapp.domain.model.DayProgress
import com.example.habitapp.domain.model.Habit
import com.example.habitapp.domain.model.HabitActivity
import com.example.habitapp.domain.model.HabitReminder
import com.example.habitapp.domain.model.HabitType
import com.example.habitapp.domain.model.JournalEntry
import com.example.habitapp.domain.repository.HabitRepository
import com.example.habitapp.domain.repository.JournalRepository
import com.example.habitapp.domain.repository.UserPreferencesRepository
import com.example.habitapp.domain.util.HabitCalculator
import com.example.habitapp.domain.util.HabitStats
import com.example.habitapp.domain.util.QuitCalculator
import com.example.habitapp.domain.util.QuitStatus
import com.example.habitapp.domain.util.TimeProvider
import com.example.habitapp.domain.util.minuteTicks
import com.example.habitapp.domain.util.resolveFirstDayOfWeek
import com.example.habitapp.presentation.navigation.HabitDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

sealed interface HabitDetailUiState {
    data object Loading : HabitDetailUiState
    data object NotFound : HabitDetailUiState
    data object Error : HabitDetailUiState
    data class Ready(
        val habit: Habit,
        val stats: HabitStats,
        val progress: Map<LocalDate, DayProgress>,
        val today: LocalDate,
        val todayProgress: DayProgress,
        val activities: List<HabitActivity>,
        val reminders: List<HabitReminder>,
        val notes: List<JournalEntry>,
        val quit: QuitStatus?,
        val firstDayOfWeek: DayOfWeek,
    ) : HabitDetailUiState
}

sealed interface HabitDetailEvent {
    data object Deleted : HabitDetailEvent
    data class Duplicated(val newId: String) : HabitDetailEvent
}

@HiltViewModel
class HabitDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: HabitRepository,
    journalRepository: JournalRepository,
    preferences: UserPreferencesRepository,
    private val time: TimeProvider,
) : ViewModel() {

    val habitId: String = savedStateHandle.toRoute<HabitDetailRoute>().habitId

    private val _events = MutableSharedFlow<HabitDetailEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<HabitDetailEvent> = _events.asSharedFlow()

    val state: StateFlow<HabitDetailUiState> = combine(
        repository.observeHabit(habitId),
        repository.observeActivitiesForHabit(habitId),
        combine(repository.observeReminders(habitId), journalRepository.observeForHabit(habitId)) { r, n -> r to n },
        preferences.preferences,
        time.minuteTicks(),
    ) { habit, activities, remindersAndNotes, prefs, now ->
        if (habit == null) return@combine HabitDetailUiState.NotFound
        val firstDay = resolveFirstDayOfWeek(prefs.firstDayOfWeek)
        val today = now.toLocalDate()
        val progress = HabitCalculator.progressByDay(habit, activities)
        HabitDetailUiState.Ready(
            habit = habit,
            stats = HabitCalculator.stats(habit, activities, today, firstDay),
            progress = progress,
            today = today,
            todayProgress = progress[today] ?: HabitCalculator.emptyProgress(habit, today),
            activities = activities,
            reminders = remindersAndNotes.first,
            notes = remindersAndNotes.second,
            quit = if (habit.type == HabitType.QUIT) QuitCalculator.status(habit, activities, now.toInstant(), time.zone()) else null,
            firstDayOfWeek = firstDay,
        )
    }
        .catch { error ->
            Log.e(TAG, "Failed to load habit", error)
            emit(HabitDetailUiState.Error)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabitDetailUiState.Loading)

    fun toggleToday() = launch { repository.toggleCompletion(habitId, time.today()) }

    fun addEntry(date: LocalDate, amount: Double, note: String?) = launch { repository.addProgress(habitId, date, amount, note) }

    fun completeDay(date: LocalDate) = launch { repository.completeDay(habitId, date) }

    fun skipDay(date: LocalDate) = launch { repository.skipDay(habitId, date) }

    fun clearDay(date: LocalDate) = launch { repository.clearDay(habitId, date) }

    fun updateEntry(activity: HabitActivity, date: LocalDate, value: Double, note: String?) =
        launch { repository.updateActivity(activity.id, date, value, note) }

    fun deleteEntry(activity: HabitActivity) = launch { repository.deleteActivity(activity.id) }

    fun recordRelapse(at: Instant, note: String?) = launch { repository.recordRelapse(habitId, at, note) }

    fun setArchived(archived: Boolean) = launch { repository.setArchived(habitId, archived) }

    fun duplicate() = launch {
        repository.duplicateHabit(habitId)?.let { _events.emit(HabitDetailEvent.Duplicated(it)) }
    }

    fun delete() = launch {
        repository.deleteHabit(habitId)
        _events.emit(HabitDetailEvent.Deleted)
    }

    fun now(): Instant = time.nowInstant()

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Log.e(TAG, "Habit detail action failed", error)
            }
        }
    }

    private companion object {
        const val TAG = "HabitDetailViewModel"
    }
}
