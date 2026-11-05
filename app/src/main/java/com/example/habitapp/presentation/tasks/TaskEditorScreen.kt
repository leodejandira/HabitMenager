package com.example.habitapp.presentation.tasks

import android.Manifest
import android.os.Build
import android.text.format.DateFormat
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.habitapp.R
import com.example.habitapp.domain.model.EditScope
import com.example.habitapp.domain.model.RecurrenceType
import com.example.habitapp.domain.model.Task
import com.example.habitapp.domain.model.TaskList
import com.example.habitapp.domain.model.TaskPriority
import com.example.habitapp.domain.repository.TaskRepository
import com.example.habitapp.domain.repository.UserPreferencesRepository
import com.example.habitapp.domain.util.TimeProvider
import com.example.habitapp.domain.util.resolveFirstDayOfWeek
import com.example.habitapp.presentation.components.ColorPicker
import com.example.habitapp.presentation.components.ConfirmDialog
import com.example.habitapp.presentation.components.FullScreenError
import com.example.habitapp.presentation.components.FullScreenLoading
import com.example.habitapp.presentation.components.LocalDatePickerDialog
import com.example.habitapp.presentation.components.LocalTimePickerDialog
import com.example.habitapp.presentation.components.SectionHeader
import com.example.habitapp.presentation.components.WeekdayChips
import com.example.habitapp.presentation.components.priorityLabel
import com.example.habitapp.presentation.components.rememberDateFormatter
import com.example.habitapp.presentation.components.rememberTimeFormatter
import com.example.habitapp.presentation.navigation.TaskEditorRoute
import com.example.habitapp.presentation.theme.HabitTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

sealed interface TaskEditorState {
    data object Loading : TaskEditorState
    data object NotFound : TaskEditorState
    data class Editing(
        val task: Task,
        val isNew: Boolean,
        val originalRecurring: Boolean,
        val occurrenceDate: LocalDate?,
        val errorRes: Int? = null,
        val done: Boolean = false,
    ) : TaskEditorState
}

@HiltViewModel
class TaskEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: TaskRepository,
    preferences: UserPreferencesRepository,
    private val time: TimeProvider,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<TaskEditorRoute>()
    val state = MutableStateFlow<TaskEditorState>(TaskEditorState.Loading)

    val lists: StateFlow<List<TaskList>> = repository.observeLists().map { l -> l.filter { !it.archived } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val firstDayOfWeek: StateFlow<DayOfWeek> = preferences.preferences.map { resolveFirstDayOfWeek(it.firstDayOfWeek) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayOfWeek.MONDAY)

    init {
        viewModelScope.launch {
            val id = route.taskId
            if (id != null) {
                val task = repository.getTask(id)
                state.value = if (task == null) {
                    TaskEditorState.NotFound
                } else {
                    TaskEditorState.Editing(
                        task = task,
                        isNew = false,
                        originalRecurring = task.recurrence.isRecurring,
                        occurrenceDate = route.occurrenceDay?.let(LocalDate::ofEpochDay) ?: task.dueDate,
                    )
                }
            } else {
                val inbox = repository.observeLists().first().firstOrNull { it.isInbox }
                state.value = TaskEditorState.Editing(
                    task = Task(
                        id = UUID.randomUUID().toString(),
                        listId = inbox?.id.orEmpty(),
                        title = "",
                        dueDate = route.presetDay?.let(LocalDate::ofEpochDay),
                        createdAt = time.nowInstant(),
                    ),
                    isNew = true,
                    originalRecurring = false,
                    occurrenceDate = null,
                )
            }
        }
    }

    fun update(transform: (Task) -> Task) {
        val s = state.value as? TaskEditorState.Editing ?: return
        state.value = s.copy(task = transform(s.task), errorRes = null)
    }

    /** Validates; returns true when the caller must ask the user for an edit scope first. */
    fun needsScope(): Boolean {
        val s = state.value as? TaskEditorState.Editing ?: return false
        return !s.isNew && s.originalRecurring && s.occurrenceDate != null
    }

    fun validate(): Boolean {
        val s = state.value as? TaskEditorState.Editing ?: return false
        val error = when {
            s.task.title.isBlank() -> R.string.error_title_required
            s.task.listId.isBlank() -> R.string.error_list_required
            s.task.recurrence.isRecurring && s.task.dueDate == null -> R.string.error_recurring_needs_date
            s.task.recurrence.type == RecurrenceType.WEEKDAYS && s.task.recurrence.weekdays.isEmpty() -> R.string.error_weekdays_required
            s.task.recurrence.until != null && s.task.dueDate != null && s.task.recurrence.until.isBefore(s.task.dueDate) -> R.string.error_end_before_start
            else -> null
        }
        if (error != null) state.value = s.copy(errorRes = error)
        return error == null
    }

    fun save(scope: EditScope = EditScope.ALL) {
        val s = state.value as? TaskEditorState.Editing ?: return
        if (!validate()) return
        launch(s) {
            if (needsScope()) {
                repository.updateRecurringTask(s.task, s.occurrenceDate ?: s.task.dueDate ?: time.today(), scope)
            } else {
                repository.saveTask(s.task)
            }
        }
    }

    fun delete(scope: EditScope = EditScope.ALL) {
        val s = state.value as? TaskEditorState.Editing ?: return
        launch(s) { repository.deleteTask(s.task.id, s.occurrenceDate, scope) }
    }

    private fun launch(s: TaskEditorState.Editing, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                state.value = s.copy(done = true)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Log.e("TaskEditorViewModel", "Task save failed", error)
                state.value = s.copy(errorRes = R.string.error_save_failed)
            }
        }
    }
}

@Composable
fun recurrenceLabel(type: RecurrenceType): String = stringResource(
    when (type) {
        RecurrenceType.NONE -> R.string.recur_none
        RecurrenceType.DAILY -> R.string.recur_daily
        RecurrenceType.WEEKLY -> R.string.recur_weekly
        RecurrenceType.BIWEEKLY -> R.string.recur_biweekly
        RecurrenceType.MONTHLY -> R.string.recur_monthly
        RecurrenceType.YEARLY -> R.string.recur_yearly
        RecurrenceType.WEEKDAYS -> R.string.recur_weekdays
    },
)

private val ReminderOptions = listOf<Int?>(null, 0, 10, 30, 60, 1440)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskEditorScreen(onBack: () -> Unit, viewModel: TaskEditorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    val firstDay by viewModel.firstDayOfWeek.collectAsStateWithLifecycle()
    var scopeFor by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val editing = state as? TaskEditorState.Editing
    LaunchedEffect(editing?.done) { if (editing?.done == true) onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (editing?.isNew != false) R.string.task_new else R.string.task_edit)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.action_close)) } },
                actions = {
                    if (editing != null && !editing.isNew) {
                        IconButton(onClick = {
                            if (viewModel.needsScope()) scopeFor = ACTION_DELETE else confirmDelete = true
                        }) { Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.action_delete)) }
                    }
                    TextButton(
                        onClick = {
                            if (viewModel.validate()) {
                                if (viewModel.needsScope()) scopeFor = ACTION_SAVE else viewModel.save()
                            }
                        },
                        modifier = Modifier.testTag("task_save"),
                    ) { Text(stringResource(R.string.action_save)) }
                },
            )
        },
    ) { padding ->
        when (val s = state) {
            TaskEditorState.Loading -> FullScreenLoading(Modifier.padding(padding))
            TaskEditorState.NotFound -> FullScreenError(stringResource(R.string.error_not_found), Modifier.padding(padding))
            is TaskEditorState.Editing -> TaskForm(s, lists, firstDay, Modifier.padding(padding), viewModel)
        }
    }

    scopeFor?.let { action ->
        ScopeDialog(
            deleting = action == ACTION_DELETE,
            onSelected = { scope -> if (action == ACTION_DELETE) viewModel.delete(scope) else viewModel.save(scope) },
            onDismiss = { scopeFor = null },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.task_delete_title),
            message = stringResource(R.string.task_delete_message),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { viewModel.delete() },
            onDismiss = { confirmDelete = false },
        )
    }
}

private const val ACTION_SAVE = "save"
private const val ACTION_DELETE = "delete"

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskForm(
    state: TaskEditorState.Editing,
    lists: List<TaskList>,
    firstDay: DayOfWeek,
    modifier: Modifier,
    viewModel: TaskEditorViewModel,
) {
    val task = state.task
    val spacing = HabitTheme.spacing
    val context = LocalContext.current
    val dateFormatter = rememberDateFormatter()
    val timeFormatter = rememberTimeFormatter()
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var pickUntil by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Column(
        modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = spacing.xxl),
    ) {
        state.errorRes?.let {
            Text(stringResource(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm))
        }
        if (!state.isNew && state.originalRecurring && state.occurrenceDate != null) {
            Text(
                stringResource(R.string.task_editing_occurrence, state.occurrenceDate.format(dateFormatter)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
            )
        }
        OutlinedTextField(
            value = task.title,
            onValueChange = { v -> viewModel.update { it.copy(title = v.take(200)) } },
            label = { Text(stringResource(R.string.task_title)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm).testTag("task_title_field"),
        )
        OutlinedTextField(
            value = task.description,
            onValueChange = { v -> viewModel.update { it.copy(description = v.take(2000)) } },
            label = { Text(stringResource(R.string.task_description)) },
            minLines = 2,
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal),
        )

        SectionHeader(stringResource(R.string.task_list))
        FlowRow(Modifier.padding(horizontal = spacing.screenHorizontal), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            lists.forEach { list ->
                FilterChip(selected = task.listId == list.id, onClick = { viewModel.update { it.copy(listId = list.id) } }, label = { Text(list.name) })
            }
        }

        SectionHeader(stringResource(R.string.task_priority))
        FlowRow(Modifier.padding(horizontal = spacing.screenHorizontal), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            TaskPriority.entries.forEach { p ->
                FilterChip(selected = task.priority == p, onClick = { viewModel.update { it.copy(priority = p) } }, label = { Text(priorityLabel(p)) })
            }
        }

        SectionHeader(stringResource(R.string.task_due))
        Row(Modifier.padding(horizontal = spacing.screenHorizontal), horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { pickDate = true }) {
                Text(task.dueDate?.format(dateFormatter) ?: stringResource(R.string.task_no_date))
            }
            if (task.dueDate != null) {
                OutlinedButton(onClick = { pickTime = true }) {
                    Text(task.dueTime?.format(timeFormatter) ?: stringResource(R.string.task_no_time))
                }
                IconButton(onClick = { viewModel.update { it.copy(dueDate = null, dueTime = null, reminderMinutesBefore = null) } }) {
                    Icon(painterResource(R.drawable.ic_close), stringResource(R.string.task_clear_date))
                }
            }
        }

        if (task.dueDate != null) {
            SectionHeader(stringResource(R.string.task_repeat))
            FlowRow(Modifier.padding(horizontal = spacing.screenHorizontal), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                RecurrenceType.entries.forEach { type ->
                    FilterChip(
                        selected = task.recurrence.type == type,
                        onClick = {
                            viewModel.update {
                                it.copy(
                                    recurrence = it.recurrence.copy(
                                        type = type,
                                        weekdays = if (type == RecurrenceType.WEEKDAYS && it.recurrence.weekdays.isEmpty()) {
                                            setOfNotNull(it.dueDate?.dayOfWeek)
                                        } else {
                                            it.recurrence.weekdays
                                        },
                                    ),
                                )
                            }
                        },
                        label = { Text(recurrenceLabel(type)) },
                    )
                }
            }
            if (task.recurrence.type == RecurrenceType.WEEKDAYS) {
                WeekdayChips(
                    selected = task.recurrence.weekdays,
                    firstDayOfWeek = firstDay,
                    onChange = { days -> viewModel.update { it.copy(recurrence = it.recurrence.copy(weekdays = days)) } },
                    modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                )
            }
            if (task.recurrence.isRecurring) {
                Row(Modifier.padding(horizontal = spacing.screenHorizontal, vertical = spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { pickUntil = true }) {
                        Text(task.recurrence.until?.let { stringResource(R.string.task_repeat_until, it.format(dateFormatter)) } ?: stringResource(R.string.task_repeat_forever))
                    }
                    if (task.recurrence.until != null) {
                        IconButton(onClick = { viewModel.update { it.copy(recurrence = it.recurrence.copy(until = null)) } }) {
                            Icon(painterResource(R.drawable.ic_close), stringResource(R.string.task_repeat_forever))
                        }
                    }
                }
            }

            SectionHeader(stringResource(R.string.task_reminder))
            FlowRow(Modifier.padding(horizontal = spacing.screenHorizontal), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                ReminderOptions.forEach { minutes ->
                    FilterChip(
                        selected = task.reminderMinutesBefore == minutes,
                        onClick = {
                            if (minutes != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            viewModel.update { it.copy(reminderMinutesBefore = minutes) }
                        },
                        label = { Text(reminderLabel(minutes)) },
                    )
                }
            }
            if (task.reminderMinutesBefore != null && task.dueTime == null) {
                Text(
                    stringResource(R.string.task_reminder_default_time),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                )
            }
        }

        SectionHeader(stringResource(R.string.habit_color))
        Row(Modifier.padding(horizontal = spacing.screenHorizontal), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = task.colorKey == null, onClick = { viewModel.update { it.copy(colorKey = null) } }, label = { Text(stringResource(R.string.color_none)) })
        }
        ColorPicker(
            selectedKey = task.colorKey.orEmpty(),
            onSelected = { key -> viewModel.update { it.copy(colorKey = key) } },
            modifier = Modifier.padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm),
        )
    }

    if (pickDate) {
        LocalDatePickerDialog(task.dueDate ?: LocalDate.now(), { d -> viewModel.update { it.copy(dueDate = d) } }, { pickDate = false })
    }
    if (pickTime) {
        LocalTimePickerDialog(
            initial = task.dueTime ?: LocalTime.of(9, 0),
            is24Hour = DateFormat.is24HourFormat(context),
            onConfirm = { t -> viewModel.update { it.copy(dueTime = t) } },
            onDismiss = { pickTime = false },
        )
    }
    if (pickUntil) {
        LocalDatePickerDialog(
            task.recurrence.until ?: (task.dueDate ?: LocalDate.now()).plusMonths(3),
            { d -> viewModel.update { it.copy(recurrence = it.recurrence.copy(until = d)) } },
            { pickUntil = false },
        )
    }
}

@Composable
private fun reminderLabel(minutes: Int?): String = when (minutes) {
    null -> stringResource(R.string.reminder_none)
    0 -> stringResource(R.string.reminder_at_time)
    1440 -> stringResource(R.string.reminder_day_before)
    60 -> stringResource(R.string.reminder_hour_before)
    else -> stringResource(R.string.reminder_minutes_before, minutes)
}

@Composable
fun ScopeDialog(deleting: Boolean, onSelected: (EditScope) -> Unit, onDismiss: () -> Unit) {
    var selected by remember { mutableStateOf(EditScope.THIS_OCCURRENCE) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (deleting) R.string.scope_delete_title else R.string.scope_edit_title)) },
        text = {
            Column {
                EditScope.entries.forEach { scope ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = selected == scope, role = Role.RadioButton, onClick = { selected = scope })
                            .padding(vertical = HabitTheme.spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == scope, onClick = null)
                        Text(
                            stringResource(
                                when (scope) {
                                    EditScope.THIS_OCCURRENCE -> R.string.scope_this
                                    EditScope.THIS_AND_FUTURE -> R.string.scope_future
                                    EditScope.ALL -> R.string.scope_all
                                },
                            ),
                            modifier = Modifier.padding(start = HabitTheme.spacing.md),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSelected(selected)
                onDismiss()
            }) { Text(stringResource(if (deleting) R.string.action_delete else R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
