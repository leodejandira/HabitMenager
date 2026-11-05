package com.example.habitapp.presentation.journal

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.habitapp.R
import com.example.habitapp.domain.model.Habit
import com.example.habitapp.domain.model.JournalEntry
import com.example.habitapp.domain.model.JournalFilter
import com.example.habitapp.domain.repository.HabitRepository
import com.example.habitapp.domain.repository.JournalQuery
import com.example.habitapp.domain.repository.JournalRepository
import com.example.habitapp.domain.util.TimeProvider
import com.example.habitapp.presentation.navigation.JournalEditorRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

enum class JournalRange { ALL_TIME, LAST_7_DAYS, LAST_30_DAYS, THIS_YEAR }

sealed interface JournalUiState {
    data object Loading : JournalUiState
    data object Error : JournalUiState
    data class Ready(
        val entries: List<JournalEntry>,
        val habits: Map<String, Habit>,
        val query: String,
        val filter: JournalFilter,
        val range: JournalRange,
        val habitId: String?,
    ) : JournalUiState
}

@HiltViewModel
class JournalViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: JournalRepository,
    habitRepository: HabitRepository,
    private val time: TimeProvider,
) : ViewModel() {

    val query = savedStateHandle.getStateFlow(KEY_QUERY, "")
    private val filter = savedStateHandle.getStateFlow(KEY_FILTER, JournalFilter.ALL.name)
    private val range = savedStateHandle.getStateFlow(KEY_RANGE, JournalRange.ALL_TIME.name)
    private val habitId = savedStateHandle.getStateFlow<String?>(KEY_HABIT, null)

    private val journalQuery = combine(query, filter, range, habitId) { q, f, r, h ->
        val today = time.today()
        val from = when (JournalRange.valueOf(r)) {
            JournalRange.ALL_TIME -> null
            JournalRange.LAST_7_DAYS -> today.minusDays(6)
            JournalRange.LAST_30_DAYS -> today.minusDays(29)
            JournalRange.THIS_YEAR -> today.withDayOfYear(1)
        }
        JournalQuery(text = q, filter = JournalFilter.valueOf(f), habitId = h, from = from, to = null)
    }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val state: StateFlow<JournalUiState> = combine(
        journalQuery.debounce(150).flatMapLatest { q -> repository.search(q).map { q to it } },
        habitRepository.observeHabits(),
    ) { (q, entries), habits ->
        JournalUiState.Ready(
            entries = entries,
            habits = habits.associateBy { it.id },
            query = q.text,
            filter = q.filter,
            range = JournalRange.valueOf(range.value),
            habitId = q.habitId,
        ) as JournalUiState
    }
        .catch { error ->
            Log.e(TAG, "Journal search failed", error)
            emit(JournalUiState.Error)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JournalUiState.Loading)

    fun setQuery(value: String) { savedStateHandle[KEY_QUERY] = value.take(100) }
    fun setFilter(value: JournalFilter) { savedStateHandle[KEY_FILTER] = value.name }
    fun setRange(value: JournalRange) { savedStateHandle[KEY_RANGE] = value.name }
    fun setHabit(id: String?) { savedStateHandle[KEY_HABIT] = id }

    fun toggleBookmark(entry: JournalEntry) {
        viewModelScope.launch {
            runCatching { repository.setBookmarked(entry.id, !entry.bookmarked) }.onFailure { Log.e(TAG, "bookmark failed", it) }
        }
    }

    private companion object {
        const val TAG = "JournalViewModel"
        const val KEY_QUERY = "query"
        const val KEY_FILTER = "filter"
        const val KEY_RANGE = "range"
        const val KEY_HABIT = "habit"
    }
}

sealed interface JournalEditorState {
    data object Loading : JournalEditorState
    data object NotFound : JournalEditorState
    data class Editing(
        val entry: JournalEntry,
        val isNew: Boolean,
        val errorRes: Int? = null,
        val done: Boolean = false,
    ) : JournalEditorState
}

@HiltViewModel
class JournalEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: JournalRepository,
    habitRepository: HabitRepository,
    private val time: TimeProvider,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<JournalEditorRoute>()
    val state = MutableStateFlow<JournalEditorState>(JournalEditorState.Loading)

    val habits: StateFlow<List<Habit>> = habitRepository.observeHabits().map { list -> list.filter { !it.archived } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val id = route.entryId
            state.value = if (id != null) {
                repository.get(id)?.let { JournalEditorState.Editing(it, isNew = false) } ?: JournalEditorState.NotFound
            } else {
                val now = time.nowInstant()
                JournalEditorState.Editing(
                    JournalEntry(
                        id = UUID.randomUUID().toString(),
                        date = route.day?.let(LocalDate::ofEpochDay) ?: time.today(),
                        title = "",
                        content = "",
                        mood = null,
                        bookmarked = false,
                        habitId = route.habitId,
                        createdAt = now,
                        updatedAt = now,
                    ),
                    isNew = true,
                )
            }
        }
    }

    fun update(transform: (JournalEntry) -> JournalEntry) {
        val s = state.value as? JournalEditorState.Editing ?: return
        state.value = s.copy(entry = transform(s.entry), errorRes = null)
    }

    fun save() {
        val s = state.value as? JournalEditorState.Editing ?: return
        if (s.entry.title.isBlank() && s.entry.content.isBlank()) {
            state.value = s.copy(errorRes = R.string.journal_error_empty)
            return
        }
        run(s) { repository.save(s.entry) }
    }

    fun delete() {
        val s = state.value as? JournalEditorState.Editing ?: return
        run(s) { repository.delete(s.entry.id) }
    }

    private fun run(s: JournalEditorState.Editing, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                state.value = s.copy(done = true)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Log.e("JournalEditorViewModel", "Journal save failed", error)
                state.value = s.copy(errorRes = R.string.error_save_failed)
            }
        }
    }
}
