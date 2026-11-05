package com.example.habitapp.presentation.habits

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.habitapp.domain.model.Habit
import com.example.habitapp.domain.model.HabitGroup
import com.example.habitapp.domain.repository.HabitRepository
import com.example.habitapp.domain.repository.UserPreferencesRepository
import com.example.habitapp.domain.util.HabitCalculator
import com.example.habitapp.domain.util.StreakInfo
import com.example.habitapp.domain.util.TimeProvider
import com.example.habitapp.domain.util.resolveFirstDayOfWeek
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

data class HabitListItem(
    val habit: Habit,
    val streak: StreakInfo,
    val completionRate: Float,
    val doneToday: Boolean,
)

data class HabitSection(
    /** Null for habits without a group. */
    val group: HabitGroup?,
    val items: List<HabitListItem>,
)

sealed interface HabitsUiState {
    data object Loading : HabitsUiState
    data object Error : HabitsUiState
    data class Ready(
        val sections: List<HabitSection>,
        val archived: List<HabitListItem>,
        val showArchived: Boolean,
        val totalActive: Int,
    ) : HabitsUiState
}

@HiltViewModel
class HabitsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: HabitRepository,
    preferences: UserPreferencesRepository,
    private val time: TimeProvider,
) : ViewModel() {

    private val showArchived = savedStateHandle.getStateFlow(KEY_ARCHIVED, false)

    val state: StateFlow<HabitsUiState> = combine(
        repository.observeHabits(),
        repository.observeGroups(),
        repository.observeAllActivities(),
        preferences.preferences,
        showArchived,
    ) { habits, groups, activities, prefs, archivedVisible ->
        val firstDay = resolveFirstDayOfWeek(prefs.firstDayOfWeek)
        val today = time.today()
        val byHabit = activities.groupBy { it.habitId }
        val items = habits.sortedBy { it.sortOrder }.map { habit ->
            val progress = HabitCalculator.progressByDay(habit, byHabit[habit.id].orEmpty())
            HabitListItem(
                habit = habit,
                streak = HabitCalculator.streak(habit, progress, today, firstDay),
                completionRate = HabitCalculator.completionRate(habit, progress, today, firstDay),
                doneToday = progress[today]?.completed == true,
            )
        }
        val active = items.filter { !it.habit.archived }
        val groupIds = groups.map { it.id }.toSet()
        val sections = buildList {
            val ungrouped = active.filter { it.habit.groupId == null || it.habit.groupId !in groupIds }
            if (ungrouped.isNotEmpty()) add(HabitSection(null, ungrouped))
            groups.forEach { group -> add(HabitSection(group, active.filter { it.habit.groupId == group.id })) }
        }
        HabitsUiState.Ready(
            sections = sections,
            archived = items.filter { it.habit.archived },
            showArchived = archivedVisible,
            totalActive = active.size,
        ) as HabitsUiState
    }
        .catch { error ->
            Log.e(TAG, "Failed to load habits", error)
            emit(HabitsUiState.Error)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabitsUiState.Loading)

    fun toggleArchived() {
        savedStateHandle[KEY_ARCHIVED] = !showArchived.value
    }

    /** Moves a habit one position up or down among the active habits. */
    fun move(habitId: String, delta: Int) {
        val ready = state.value as? HabitsUiState.Ready ?: return
        val ordered = ready.sections.flatMap { it.items }.map { it.habit.id }.toMutableList()
        val index = ordered.indexOf(habitId)
        val target = index + delta
        if (index < 0 || target !in ordered.indices) return
        ordered.add(target, ordered.removeAt(index))
        launch { repository.reorderHabits(ordered + ready.archived.map { it.habit.id }) }
    }

    fun setGroupCollapsed(group: HabitGroup) = launch { repository.setGroupCollapsed(group.id, !group.collapsed) }

    fun renameGroup(group: HabitGroup, name: String) = launch { repository.saveGroup(group.copy(name = name)) }

    fun deleteGroup(group: HabitGroup) = launch { repository.deleteGroup(group.id) }

    fun restore(habitId: String) = launch { repository.setArchived(habitId, false) }

    fun delete(habitId: String) = launch { repository.deleteHabit(habitId) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Log.e(TAG, "Habit action failed", error)
            }
        }
    }

    private companion object {
        const val TAG = "HabitsViewModel"
        const val KEY_ARCHIVED = "show_archived"
    }
}
