package com.example.habitapp.presentation.calendar

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.habitapp.domain.model.JournalEntry
import com.example.habitapp.domain.model.TaskOccurrence
import com.example.habitapp.domain.repository.HabitRepository
import com.example.habitapp.domain.repository.JournalRepository
import com.example.habitapp.domain.repository.TaskRepository
import com.example.habitapp.domain.repository.UserPreferencesRepository
import com.example.habitapp.domain.usecase.BuildDayOverviewUseCase
import com.example.habitapp.domain.usecase.DayOverview
import com.example.habitapp.domain.usecase.TaskQueries
import com.example.habitapp.domain.util.HabitCalculator
import com.example.habitapp.domain.util.TimeProvider
import com.example.habitapp.domain.util.resolveFirstDayOfWeek
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

enum class CalendarMode { MONTH, WEEK, DAY }

data class DaySummary(
    val date: LocalDate,
    /** Share of scheduled habits completed, null when nothing was scheduled. */
    val habitFraction: Float?,
    /** Colors (keys) of habits completed that day, at most three. */
    val completedColors: List<String>,
    val taskCount: Int,
    val openTaskCount: Int,
    val hasOverdue: Boolean,
    val hasJournal: Boolean,
)

sealed interface CalendarUiState {
    data object Loading : CalendarUiState
    data object Error : CalendarUiState
    data class Ready(
        val mode: CalendarMode,
        val today: LocalDate,
        val selected: LocalDate,
        val firstDayOfWeek: DayOfWeek,
        /** Days shown in the grid (whole weeks covering the month, or one week). */
        val gridDays: List<DaySummary>,
        val weekOverviews: List<DayOverview>,
        val selectedOverview: DayOverview,
        val journalForSelected: List<JournalEntry>,
    ) : CalendarUiState
}

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    habitRepository: HabitRepository,
    private val taskRepository: TaskRepository,
    private val journalRepository: JournalRepository,
    private val habitRepo: HabitRepository,
    preferences: UserPreferencesRepository,
    private val buildOverview: BuildDayOverviewUseCase,
    private val time: TimeProvider,
) : ViewModel() {

    private val selectedDay = savedStateHandle.getStateFlow(KEY_SELECTED, time.today().toEpochDay())
    private val mode = savedStateHandle.getStateFlow(KEY_MODE, CalendarMode.MONTH.name)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val journalDays = selectedDay.flatMapLatest { day ->
        val date = LocalDate.ofEpochDay(day)
        journalRepository.observeDaysWithEntries(date.minusDays(45), date.plusDays(45))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val journalSelected = selectedDay.flatMapLatest { journalRepository.observeForDay(LocalDate.ofEpochDay(it)) }

    val state: StateFlow<CalendarUiState> = combine(
        combine(selectedDay, mode) { d, m -> LocalDate.ofEpochDay(d) to CalendarMode.valueOf(m) },
        combine(habitRepository.observeHabits(), habitRepository.observeAllActivities()) { h, a -> h to a },
        taskRepository.observeTaskData(),
        combine(journalDays, journalSelected) { days, entries -> days to entries },
        preferences.preferences,
    ) { (selected, calendarMode), (habits, activities), tasks, (journalDayset, selectedEntries), prefs ->
        val firstDay = resolveFirstDayOfWeek(prefs.firstDayOfWeek)
        val today = time.today()
        val days = when (calendarMode) {
            CalendarMode.MONTH -> {
                val start = selected.withDayOfMonth(1).with(TemporalAdjusters.previousOrSame(firstDay))
                val end = selected.withDayOfMonth(selected.lengthOfMonth()).with(TemporalAdjusters.nextOrSame(firstDay.minus(1)))
                generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
            }
            else -> {
                val start = selected.with(TemporalAdjusters.previousOrSame(firstDay))
                (0L until 7L).map { start.plusDays(it) }
            }
        }
        val byHabit = activities.groupBy { it.habitId }
        val progressByHabit = habits.filter { !it.archived }.associate { it.id to HabitCalculator.progressByDay(it, byHabit[it.id].orEmpty()) }
        val activeHabits = habits.filter { !it.archived }
        val summaries = days.map { date ->
            val counted = activeHabits.filter {
                HabitCalculator.countsTowardDailyProgress(it, progressByHabit[it.id].orEmpty(), date, firstDay)
            }
            val completed = counted.filter { progressByHabit[it.id]?.get(date)?.completed == true }
            val occurrences = TaskQueries.occurrencesOn(tasks, date)
            DaySummary(
                date = date,
                habitFraction = if (counted.isEmpty() || date.isAfter(today)) null else completed.size.toFloat() / counted.size,
                completedColors = completed.take(3).map { it.colorKey },
                taskCount = occurrences.size,
                openTaskCount = occurrences.count { !it.completed },
                hasOverdue = date.isBefore(today) && occurrences.any { !it.completed && !it.task.recurrence.isRecurring },
                hasJournal = date in journalDayset,
            )
        }
        CalendarUiState.Ready(
            mode = calendarMode,
            today = today,
            selected = selected,
            firstDayOfWeek = firstDay,
            gridDays = summaries,
            weekOverviews = if (calendarMode == CalendarMode.WEEK) days.map { buildOverview(it, habits, activities, tasks, firstDay) } else emptyList(),
            selectedOverview = buildOverview(selected, habits, activities, tasks, firstDay),
            journalForSelected = selectedEntries,
        ) as CalendarUiState
    }
        .catch { error ->
            Log.e(TAG, "Failed to build calendar", error)
            emit(CalendarUiState.Error)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState.Loading)

    fun select(date: LocalDate) {
        savedStateHandle[KEY_SELECTED] = date.toEpochDay()
    }

    fun setMode(newMode: CalendarMode) {
        savedStateHandle[KEY_MODE] = newMode.name
    }

    fun shift(direction: Int) {
        val current = LocalDate.ofEpochDay(selectedDay.value)
        val next = when (CalendarMode.valueOf(mode.value)) {
            CalendarMode.MONTH -> current.plusMonths(direction.toLong())
            CalendarMode.WEEK -> current.plusWeeks(direction.toLong())
            CalendarMode.DAY -> current.plusDays(direction.toLong())
        }
        select(next)
    }

    fun goToToday() = select(time.today())

    fun toggleHabit(habitId: String) = launch { habitRepo.toggleCompletion(habitId, LocalDate.ofEpochDay(selectedDay.value)) }

    fun addProgress(habitId: String, amount: Double, note: String?) =
        launch { habitRepo.addProgress(habitId, LocalDate.ofEpochDay(selectedDay.value), amount, note) }

    fun toggleTask(occurrence: TaskOccurrence) =
        launch { taskRepository.setOccurrenceCompleted(occurrence.task.id, occurrence.date, !occurrence.completed) }

    /** Drag-and-drop: moves a task occurrence to another day and persists immediately. */
    fun moveTask(occurrence: TaskOccurrence, to: LocalDate) =
        launch { taskRepository.reschedule(occurrence.task.id, occurrence.date, to) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Log.e(TAG, "Calendar action failed", error)
            }
        }
    }

    private companion object {
        const val TAG = "CalendarViewModel"
        const val KEY_SELECTED = "selected_day"
        const val KEY_MODE = "mode"
    }
}
