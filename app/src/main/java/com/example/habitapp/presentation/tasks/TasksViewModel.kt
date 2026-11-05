package com.example.habitapp.presentation.tasks

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.habitapp.domain.model.TaskList
import com.example.habitapp.domain.model.TaskOccurrence
import com.example.habitapp.domain.model.TaskPriority
import com.example.habitapp.domain.repository.TaskData
import com.example.habitapp.domain.repository.TaskRepository
import com.example.habitapp.domain.usecase.TaskQueries
import com.example.habitapp.domain.util.TimeProvider
import com.example.habitapp.domain.util.minuteTicks
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

enum class TaskFilter { TODAY, UPCOMING, OVERDUE, ALL, NO_DATE, COMPLETED }

enum class TaskSort { DUE_DATE, PRIORITY, CREATED }

data class TaskStats(val completedToday: Int, val completedThisWeek: Int, val completedTotal: Int, val open: Int)

data class TaskSection(val date: LocalDate?, val titleOverdue: Boolean, val items: List<TaskOccurrence>)

sealed interface TasksUiState {
    data object Loading : TasksUiState
    data object Error : TasksUiState
    data class Ready(
        val today: LocalDate,
        val lists: List<TaskList>,
        val filter: TaskFilter,
        val listId: String?,
        val minPriority: TaskPriority,
        val sort: TaskSort,
        val sections: List<TaskSection>,
        val stats: TaskStats,
        val hasAnyTask: Boolean,
    ) : TasksUiState
}

@HiltViewModel
class TasksViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: TaskRepository,
    private val time: TimeProvider,
) : ViewModel() {

    private val filter = savedStateHandle.getStateFlow(KEY_FILTER, TaskFilter.TODAY.name)
    private val listId = savedStateHandle.getStateFlow<String?>(KEY_LIST, null)
    private val priority = savedStateHandle.getStateFlow(KEY_PRIORITY, TaskPriority.NONE.level)
    private val sort = savedStateHandle.getStateFlow(KEY_SORT, TaskSort.DUE_DATE.name)

    private val options = combine(filter, listId, priority, sort) { f, l, p, s ->
        Options(TaskFilter.valueOf(f), l, TaskPriority.fromLevel(p), TaskSort.valueOf(s))
    }

    private data class Options(val filter: TaskFilter, val listId: String?, val minPriority: TaskPriority, val sort: TaskSort)

    val state: StateFlow<TasksUiState> = combine(
        repository.observeTaskData(),
        repository.observeLists(),
        options,
        time.minuteTicks().map { it.toLocalDate() }.distinctUntilChanged(),
    ) { data, lists, opts, today ->
        TasksUiState.Ready(
            today = today,
            lists = lists.filter { !it.archived },
            filter = opts.filter,
            listId = opts.listId,
            minPriority = opts.minPriority,
            sort = opts.sort,
            sections = buildSections(data, opts, today),
            stats = stats(data, today),
            hasAnyTask = data.tasks.isNotEmpty(),
        ) as TasksUiState
    }
        .catch { error ->
            Log.e(TAG, "Failed to load tasks", error)
            emit(TasksUiState.Error)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksUiState.Loading)

    private fun buildSections(data: TaskData, opts: Options, today: LocalDate): List<TaskSection> {
        fun List<TaskOccurrence>.filtered() = filter { occ ->
            (opts.listId == null || occ.task.listId == opts.listId) && occ.task.priority.level >= opts.minPriority.level
        }.sortedWith(comparator(opts.sort))

        return when (opts.filter) {
            TaskFilter.TODAY -> listOfNotNull(
                TaskSection(null, true, TaskQueries.overdue(data, today).filtered()).takeIf { it.items.isNotEmpty() },
                TaskSection(today, false, TaskQueries.occurrencesOn(data, today).filtered()),
            )
            TaskFilter.UPCOMING -> (1L..UPCOMING_DAYS).map { offset ->
                val date = today.plusDays(offset)
                TaskSection(date, false, TaskQueries.occurrencesOn(data, date).filter { !it.completed }.filtered())
            }.filter { it.items.isNotEmpty() }
            TaskFilter.OVERDUE -> listOf(TaskSection(null, true, TaskQueries.overdue(data, today).filtered()))
            TaskFilter.ALL -> listOf(TaskSection(null, false, TaskQueries.representative(data, today).filter { !it.completed }.filtered()))
            TaskFilter.NO_DATE -> listOf(
                TaskSection(null, false, data.tasks.filter { it.dueDate == null && it.completedAt == null }.map { TaskOccurrence(it, null, null) }.filtered()),
            )
            TaskFilter.COMPLETED -> listOf(
                TaskSection(
                    null,
                    false,
                    TaskQueries.completedHistory(data).filter { occ ->
                        (opts.listId == null || occ.task.listId == opts.listId) && occ.task.priority.level >= opts.minPriority.level
                    }.take(COMPLETED_LIMIT),
                ),
            )
        }
    }

    private fun comparator(sort: TaskSort): Comparator<TaskOccurrence> = when (sort) {
        TaskSort.DUE_DATE -> compareBy<TaskOccurrence> { it.completed }
            .thenBy { it.date == null }
            .thenBy { it.date }
            .thenBy { it.task.dueTime == null }
            .thenBy { it.task.dueTime }
            .thenByDescending { it.task.priority.level }
        TaskSort.PRIORITY -> compareBy<TaskOccurrence> { it.completed }
            .thenByDescending { it.task.priority.level }
            .thenBy { it.date == null }
            .thenBy { it.date }
        TaskSort.CREATED -> compareBy<TaskOccurrence> { it.completed }.thenByDescending { it.task.createdAt }
    }

    private fun stats(data: TaskData, today: LocalDate): TaskStats {
        val history = TaskQueries.completedHistory(data)
        val zone = ZoneId.systemDefault()
        val weekAgo = today.minusDays(6)
        val completedDates = history.mapNotNull { it.completedAt?.atZone(zone)?.toLocalDate() }
        return TaskStats(
            completedToday = completedDates.count { it == today },
            completedThisWeek = completedDates.count { !it.isBefore(weekAgo) },
            completedTotal = history.size,
            open = TaskQueries.representative(data, today).count { !it.completed },
        )
    }

    fun setFilter(filter: TaskFilter) {
        savedStateHandle[KEY_FILTER] = filter.name
    }

    fun setList(id: String?) {
        savedStateHandle[KEY_LIST] = id
    }

    fun setMinPriority(priority: TaskPriority) {
        savedStateHandle[KEY_PRIORITY] = priority.level
    }

    fun setSort(sort: TaskSort) {
        savedStateHandle[KEY_SORT] = sort.name
    }

    fun toggle(occurrence: TaskOccurrence) = launch {
        repository.setOccurrenceCompleted(occurrence.task.id, occurrence.date, !occurrence.completed)
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Log.e(TAG, "Task action failed", error)
            }
        }
    }

    private companion object {
        const val TAG = "TasksViewModel"
        const val KEY_FILTER = "filter"
        const val KEY_LIST = "list"
        const val KEY_PRIORITY = "priority"
        const val KEY_SORT = "sort"
        const val UPCOMING_DAYS = 14L
        const val COMPLETED_LIMIT = 300
    }
}
