package com.example.habitapp.presentation.habits

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.habitapp.R
import com.example.habitapp.domain.model.FrequencyType
import com.example.habitapp.domain.model.GoalPeriod
import com.example.habitapp.domain.model.GoalType
import com.example.habitapp.domain.model.Habit
import com.example.habitapp.domain.model.HabitFrequency
import com.example.habitapp.domain.model.HabitReminder
import com.example.habitapp.domain.model.HabitSuggestions
import com.example.habitapp.domain.model.HabitType
import com.example.habitapp.domain.model.TaskList
import com.example.habitapp.domain.repository.HabitRepository
import com.example.habitapp.domain.repository.TaskRepository
import com.example.habitapp.domain.repository.UserPreferencesRepository
import com.example.habitapp.domain.util.TimeProvider
import com.example.habitapp.domain.util.resolveFirstDayOfWeek
import com.example.habitapp.presentation.navigation.HabitEditorRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/** Units offered in the goal row; [MINUTES] maps to a duration habit, [TIMES] to a count. */
object HabitUnits {
    const val TIMES = ""
    const val MINUTES = "min"
    val presets = listOf(TIMES, MINUTES, "ml", "litros", "copos", "km", "páginas", "passos", "kg", "R$")
}

data class HabitForm(
    val id: String = UUID.randomUUID().toString(),
    val isNew: Boolean = true,
    val name: String = "",
    val description: String = "",
    val emoji: String = "✅",
    val colorKey: String = "periwinkle",
    /** COUNT (any measurable unit) or QUIT. */
    val quit: Boolean = false,
    val unit: String = HabitUnits.TIMES,
    val target: Double = 1.0,
    val step: Double = 1.0,
    val goalPeriod: GoalPeriod = GoalPeriod.DAY,
    val goalType: GoalType = GoalType.AT_LEAST,
    val specificDays: Boolean = false,
    val weekdays: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val listId: String? = null,
    val priority: Int = 0,
    val timerMinutes: Int? = null,
    val reminders: List<HabitReminder> = emptyList(),
    val sortOrder: Int = 0,
    val archived: Boolean = false,
    val createdAt: Instant,
    val quitStartedAt: Instant? = null,
    val groupId: String? = null,
    /** Original type when editing a legacy yes/no habit, kept so history still reads correctly. */
    val legacyBoolean: Boolean = false,
) {
    val type: HabitType
        get() = when {
            quit -> HabitType.QUIT
            legacyBoolean && unit == HabitUnits.TIMES -> HabitType.BOOLEAN
            unit == HabitUnits.MINUTES -> HabitType.DURATION
            unit == HabitUnits.TIMES -> HabitType.COUNT
            else -> HabitType.NUMERIC
        }
}

sealed interface HabitEditorUiState {
    data object Loading : HabitEditorUiState
    data object NotFound : HabitEditorUiState
    data class Editing(
        val form: HabitForm,
        val lists: List<TaskList>,
        val firstDayOfWeek: DayOfWeek,
        val errorRes: Int? = null,
        val saved: Boolean = false,
    ) : HabitEditorUiState
}

@HiltViewModel
class HabitEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: HabitRepository,
    private val taskRepository: TaskRepository,
    private val preferences: UserPreferencesRepository,
    private val time: TimeProvider,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<HabitEditorRoute>()
    private val _state = MutableStateFlow<HabitEditorUiState>(HabitEditorUiState.Loading)
    val state: StateFlow<HabitEditorUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                load()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Log.e(TAG, "Failed to load habit", error)
                _state.value = HabitEditorUiState.NotFound
            }
        }
    }

    private suspend fun load() {
        val firstDay = resolveFirstDayOfWeek(preferences.preferences.first().firstDayOfWeek)
        val lists = taskRepository.observeLists().first().filter { !it.archived }
        val habitId = route.habitId
        val form = if (habitId != null) {
            val habit = repository.getHabit(habitId) ?: run {
                _state.value = HabitEditorUiState.NotFound
                return
            }
            habit.toForm(repository.observeReminders(habitId).first())
        } else {
            val base = HabitForm(startDate = time.today(), createdAt = time.nowInstant(), quit = route.trackingType == HabitType.QUIT.name)
            route.suggestionId?.let(HabitSuggestions::byId)?.let { s ->
                val pt = Locale.getDefault().language == "pt"
                base.copy(
                    name = s.name(pt),
                    emoji = s.emoji,
                    colorKey = s.colorKey,
                    quit = s.type == HabitType.QUIT,
                    unit = when (s.type) {
                        HabitType.DURATION -> HabitUnits.MINUTES
                        HabitType.NUMERIC -> s.unit(pt)
                        else -> s.unit(pt).ifBlank { HabitUnits.TIMES }
                    },
                    target = s.target,
                    step = s.step,
                    goalPeriod = s.goalPeriod,
                    goalType = s.goalType,
                )
            } ?: base
        }
        _state.value = HabitEditorUiState.Editing(form, lists, firstDay)
    }

    fun update(transform: (HabitForm) -> HabitForm) {
        _state.update { if (it is HabitEditorUiState.Editing) it.copy(form = transform(it.form), errorRes = null) else it }
    }

    fun addReminder(minuteOfDay: Int) = update {
        it.copy(reminders = it.reminders + HabitReminder(UUID.randomUUID().toString(), it.id, minuteOfDay, emptySet()))
    }

    fun updateReminder(reminder: HabitReminder) = update { form ->
        form.copy(reminders = form.reminders.map { if (it.id == reminder.id) reminder else it })
    }

    fun removeReminder(id: String) = update { form -> form.copy(reminders = form.reminders.filterNot { it.id == id }) }

    fun save() {
        val current = _state.value as? HabitEditorUiState.Editing ?: return
        val form = current.form
        val error = validate(form)
        if (error != null) {
            _state.value = current.copy(errorRes = error)
            return
        }
        viewModelScope.launch {
            try {
                repository.saveHabit(form.toHabit(), if (form.quit) emptyList() else form.reminders)
                _state.value = current.copy(saved = true)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save habit", e)
                _state.value = current.copy(errorRes = R.string.error_save_failed)
            }
        }
    }

    private fun validate(form: HabitForm): Int? = when {
        form.name.isBlank() -> R.string.error_name_required
        !form.quit && form.target <= 0 -> R.string.error_target_invalid
        !form.quit && form.step <= 0 -> R.string.error_step_invalid
        form.specificDays && form.weekdays.isEmpty() -> R.string.error_weekdays_required
        form.endDate != null && form.endDate.isBefore(form.startDate) -> R.string.error_end_before_start
        else -> null
    }

    private fun HabitForm.toHabit() = Habit(
        id = id,
        name = name,
        description = description,
        emoji = emoji,
        colorKey = colorKey,
        type = type,
        unit = if (type == HabitType.NUMERIC || type == HabitType.COUNT) unit else "",
        target = if (quit) 1.0 else target,
        step = if (quit) 1.0 else step,
        frequency = HabitFrequency(
            type = if (specificDays && goalPeriod == GoalPeriod.DAY) FrequencyType.WEEKDAYS else FrequencyType.DAILY,
            weekdays = if (specificDays && goalPeriod == GoalPeriod.DAY) weekdays else DayOfWeek.entries.toSet(),
        ),
        goalPeriod = if (quit) GoalPeriod.DAY else goalPeriod,
        goalType = if (quit) GoalType.AT_LEAST else goalType,
        listId = listId,
        priority = priority,
        timerMinutes = timerMinutes,
        startDate = startDate,
        endDate = endDate,
        groupId = groupId,
        sortOrder = sortOrder,
        archived = archived,
        quitStartedAt = quitStartedAt,
        createdAt = createdAt,
    )

    private fun Habit.toForm(reminders: List<HabitReminder>) = HabitForm(
        id = id,
        isNew = false,
        name = name,
        description = description,
        emoji = emoji,
        colorKey = colorKey,
        quit = type == HabitType.QUIT,
        unit = when (type) {
            HabitType.DURATION -> HabitUnits.MINUTES
            HabitType.BOOLEAN -> HabitUnits.TIMES
            else -> unit
        },
        target = target,
        step = step,
        goalPeriod = goalPeriod,
        goalType = goalType,
        specificDays = frequency.type == FrequencyType.WEEKDAYS,
        weekdays = frequency.weekdays,
        startDate = startDate,
        endDate = endDate,
        listId = listId,
        priority = priority,
        timerMinutes = timerMinutes,
        reminders = reminders,
        sortOrder = sortOrder,
        archived = archived,
        createdAt = createdAt,
        quitStartedAt = quitStartedAt,
        groupId = groupId,
        legacyBoolean = type == HabitType.BOOLEAN,
    )

    private companion object {
        const val TAG = "HabitEditorViewModel"
    }
}

fun String.toPositiveDouble(): Double? = replace(',', '.').trim().toDoubleOrNull()?.takeIf { it > 0 }

fun formatNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
