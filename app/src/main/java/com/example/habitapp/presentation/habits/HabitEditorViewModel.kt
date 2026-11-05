package com.example.habitapp.presentation.habits

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.habitapp.R
import com.example.habitapp.domain.model.FrequencyType
import com.example.habitapp.domain.model.Habit
import com.example.habitapp.domain.model.HabitFrequency
import com.example.habitapp.domain.model.HabitGroup
import com.example.habitapp.domain.model.HabitReminder
import com.example.habitapp.domain.model.HabitSuggestions
import com.example.habitapp.domain.model.HabitType
import com.example.habitapp.domain.repository.HabitRepository
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
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

data class HabitForm(
    val id: String = UUID.randomUUID().toString(),
    val isNew: Boolean = true,
    val name: String = "",
    val description: String = "",
    val emoji: String = "✅",
    val colorKey: String = "evergreen",
    val type: HabitType = HabitType.BOOLEAN,
    val unit: String = "",
    val target: String = "1",
    val step: String = "1",
    val frequencyType: FrequencyType = FrequencyType.DAILY,
    val weekdays: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val timesPerPeriod: String = "3",
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val groupId: String? = null,
    val reminders: List<HabitReminder> = emptyList(),
    val sortOrder: Int = 0,
    val archived: Boolean = false,
    val createdAt: java.time.Instant,
    val quitStartedAt: java.time.Instant? = null,
)

sealed interface HabitEditorUiState {
    data object Loading : HabitEditorUiState
    data object NotFound : HabitEditorUiState
    data class Editing(
        val form: HabitForm,
        val groups: List<HabitGroup>,
        val firstDayOfWeek: DayOfWeek,
        val errorRes: Int? = null,
        val saved: Boolean = false,
    ) : HabitEditorUiState
}

@HiltViewModel
class HabitEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: HabitRepository,
    private val preferences: UserPreferencesRepository,
    private val time: TimeProvider,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<HabitEditorRoute>()
    private val _state = MutableStateFlow<HabitEditorUiState>(HabitEditorUiState.Loading)
    val state: StateFlow<HabitEditorUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { load() }
        viewModelScope.launch {
            repository.observeGroups().collect { groups ->
                _state.update { if (it is HabitEditorUiState.Editing) it.copy(groups = groups) else it }
            }
        }
    }

    private suspend fun load() {
        val firstDay = resolveFirstDayOfWeek(preferences.preferences.first().firstDayOfWeek)
        val groups = repository.observeGroups().first()
        val habitId = route.habitId
        val form = if (habitId != null) {
            val habit = repository.getHabit(habitId)
            if (habit == null) {
                _state.value = HabitEditorUiState.NotFound
                return
            }
            val reminders = repository.observeReminders(habitId).first()
            habit.toForm(reminders)
        } else {
            val base = HabitForm(startDate = time.today(), createdAt = time.nowInstant())
            route.suggestionId?.let(HabitSuggestions::byId)?.let { suggestion ->
                val pt = Locale.getDefault().language == "pt"
                base.copy(
                    name = suggestion.name(pt),
                    emoji = suggestion.emoji,
                    colorKey = suggestion.colorKey,
                    type = suggestion.type,
                    unit = suggestion.unit(pt),
                    target = formatNumber(suggestion.target),
                    step = formatNumber(suggestion.step),
                    frequencyType = suggestion.frequency.type,
                    weekdays = suggestion.frequency.weekdays,
                    timesPerPeriod = suggestion.frequency.timesPerPeriod.toString(),
                )
            } ?: base
        }
        _state.value = HabitEditorUiState.Editing(form, groups, firstDay)
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

    fun createGroup(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val count = (_state.value as? HabitEditorUiState.Editing)?.groups?.size ?: 0
            val group = HabitGroup(UUID.randomUUID().toString(), name.trim(), count, collapsed = false)
            runCatching { repository.saveGroup(group) }
                .onSuccess { update { it.copy(groupId = group.id) } }
                .onFailure { Log.e(TAG, "Failed to create group", it) }
        }
    }

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
                repository.saveHabit(form.toHabit(), form.reminders)
                _state.value = current.copy(saved = true)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save habit", e)
                _state.value = current.copy(errorRes = R.string.error_save_failed)
            }
        }
    }

    private fun validate(form: HabitForm): Int? {
        if (form.name.isBlank()) return R.string.error_name_required
        if (form.type.isMeasurable) {
            val target = form.target.toPositiveDouble() ?: return R.string.error_target_invalid
            if (target <= 0) return R.string.error_target_invalid
            if (form.step.toPositiveDouble() == null) return R.string.error_step_invalid
        }
        if (form.frequencyType == FrequencyType.WEEKDAYS && form.weekdays.isEmpty()) return R.string.error_weekdays_required
        if (form.frequencyType == FrequencyType.TIMES_PER_WEEK || form.frequencyType == FrequencyType.TIMES_PER_MONTH) {
            val times = form.timesPerPeriod.toIntOrNull() ?: return R.string.error_times_invalid
            val max = if (form.frequencyType == FrequencyType.TIMES_PER_WEEK) 7 else 31
            if (times !in 1..max) return R.string.error_times_invalid
        }
        if (form.endDate != null && form.endDate.isBefore(form.startDate)) return R.string.error_end_before_start
        return null
    }

    private fun HabitForm.toHabit() = Habit(
        id = id,
        name = name,
        description = description,
        emoji = emoji,
        colorKey = colorKey,
        type = type,
        unit = if (type == HabitType.NUMERIC || type == HabitType.COUNT) unit else "",
        target = if (type.isMeasurable) target.toPositiveDouble() ?: 1.0 else 1.0,
        step = if (type.isMeasurable) step.toPositiveDouble() ?: 1.0 else 1.0,
        frequency = HabitFrequency(
            type = if (type == HabitType.QUIT) FrequencyType.DAILY else frequencyType,
            weekdays = if (frequencyType == FrequencyType.WEEKDAYS) weekdays else DayOfWeek.entries.toSet(),
            timesPerPeriod = timesPerPeriod.toIntOrNull() ?: 1,
        ),
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
        type = type,
        unit = unit,
        target = formatNumber(target),
        step = formatNumber(step),
        frequencyType = frequency.type,
        weekdays = frequency.weekdays,
        timesPerPeriod = frequency.timesPerPeriod.toString(),
        startDate = startDate,
        endDate = endDate,
        groupId = groupId,
        reminders = reminders,
        sortOrder = sortOrder,
        archived = archived,
        createdAt = createdAt,
        quitStartedAt = quitStartedAt,
    )

    private companion object {
        const val TAG = "HabitEditorViewModel"
    }
}

fun String.toPositiveDouble(): Double? = replace(',', '.').trim().toDoubleOrNull()?.takeIf { it > 0 }

fun formatNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

/** Keeps defaults sensible when the user switches habit type. */
fun HabitForm.withType(newType: HabitType): HabitForm = when (newType) {
    HabitType.DURATION -> copy(type = newType, target = if (type == newType) target else "30", step = if (type == newType) step else "5", unit = "")
    HabitType.NUMERIC, HabitType.COUNT -> copy(
        type = newType,
        target = if (type.isMeasurable) target else "10",
        step = if (type.isMeasurable) step else "1",
    )
    else -> copy(type = newType)
}
