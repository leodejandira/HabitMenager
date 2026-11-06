package com.example.habitapp.presentation.routines

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.habitapp.R
import com.example.habitapp.domain.model.Habit
import com.example.habitapp.domain.model.HabitType
import com.example.habitapp.domain.model.Routine
import com.example.habitapp.domain.repository.HabitRepository
import com.example.habitapp.domain.repository.RoutineRepository
import com.example.habitapp.domain.usecase.HabitDayItem
import com.example.habitapp.domain.usecase.ObserveDayOverviewUseCase
import com.example.habitapp.domain.util.TimeProvider
import com.example.habitapp.presentation.components.ConfirmDialog
import com.example.habitapp.presentation.components.EmojiBadge
import com.example.habitapp.presentation.components.EmojiPickerDialog
import com.example.habitapp.presentation.components.EmptyState
import com.example.habitapp.presentation.components.FullScreenLoading
import com.example.habitapp.presentation.components.HabitCard
import com.example.habitapp.presentation.components.HabitCardActions
import com.example.habitapp.presentation.components.SectionHeader
import com.example.habitapp.presentation.habits.AmountDialog
import com.example.habitapp.presentation.navigation.RoutineEditorRoute
import com.example.habitapp.presentation.navigation.RoutineRunRoute
import com.example.habitapp.presentation.theme.HabitColor
import com.example.habitapp.presentation.theme.HabitTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

data class RoutineWithSteps(val routine: Routine, val steps: List<HabitDayItem>) {
    val completed: Int get() = steps.count { it.done }
}

@HiltViewModel
class RoutineViewModel @Inject constructor(
    private val routines: RoutineRepository,
    private val habits: HabitRepository,
    observeDayOverview: ObserveDayOverviewUseCase,
    private val time: TimeProvider,
) : ViewModel() {

    /** All routines with today's progress of their steps (steps not scheduled today are omitted). */
    val items: StateFlow<List<RoutineWithSteps>?> = combine(
        routines.observeRoutines(),
        observeDayOverview(flowOf(time.today())),
    ) { list, overview ->
        val byId = overview.habits.associateBy { it.habit.id }
        list.map { routine -> RoutineWithSteps(routine, routine.habitIds.mapNotNull { byId[it] }) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun toggle(habitId: String) = launch { habits.toggleCompletion(habitId, time.today()) }
    fun addProgress(habitId: String, amount: Double, note: String?) = launch { habits.addProgress(habitId, time.today(), amount, note) }
    fun delete(routineId: String) = launch { routines.deleteRoutine(routineId) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Log.e("RoutineViewModel", "Routine action failed", error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutinesScreen(
    onBack: () -> Unit,
    onNew: () -> Unit,
    onEdit: (String) -> Unit,
    onRun: (String) -> Unit,
    viewModel: RoutineViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<Routine?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.routines_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back)) } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNew) { Icon(painterResource(R.drawable.ic_add), stringResource(R.string.routine_new)) }
        },
    ) { padding ->
        val list = items
        when {
            list == null -> FullScreenLoading(Modifier.padding(padding))
            list.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(
                    iconRes = R.drawable.ic_routine,
                    title = stringResource(R.string.routines_empty_title),
                    message = stringResource(R.string.routines_empty_message),
                    action = { Button(onClick = onNew) { Text(stringResource(R.string.routine_new)) } },
                )
            }
            else -> LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 96.dp)) {
                items(list, key = { it.routine.id }) { item ->
                    Card(
                        onClick = { onRun(item.routine.id) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = HabitTheme.spacing.screenHorizontal, vertical = HabitTheme.spacing.xs),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
                        Row(Modifier.padding(HabitTheme.spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                            EmojiBadge(item.routine.emoji, HabitColor.INDIGO.resolve(), 44)
                            Column(Modifier.weight(1f).padding(horizontal = HabitTheme.spacing.md)) {
                                Text(item.routine.name, style = MaterialTheme.typography.titleMedium)
                                val details = buildList {
                                    add(stringResource(R.string.routine_steps_progress, item.completed, item.steps.size))
                                    item.routine.estimatedMinutes?.let { add(stringResource(R.string.routine_minutes, it)) }
                                }
                                Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                                LinearProgressIndicator(
                                    progress = { if (item.steps.isEmpty()) 0f else item.completed.toFloat() / item.steps.size },
                                    modifier = Modifier.fillMaxWidth().padding(top = HabitTheme.spacing.sm),
                                )
                            }
                            IconButton(onClick = { onEdit(item.routine.id) }) { Icon(painterResource(R.drawable.ic_edit), stringResource(R.string.action_edit)) }
                            IconButton(onClick = { deleting = item.routine }) { Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.action_delete)) }
                        }
                    }
                }
            }
        }
    }
    deleting?.let { routine ->
        ConfirmDialog(
            title = stringResource(R.string.routine_delete_title),
            message = stringResource(R.string.routine_delete_message),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { viewModel.delete(routine.id) },
            onDismiss = { deleting = null },
        )
    }
}

@HiltViewModel
class RoutineRunViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    routines: RoutineRepository,
    private val habits: HabitRepository,
    observeDayOverview: ObserveDayOverviewUseCase,
    private val time: TimeProvider,
) : ViewModel() {
    private val routineId = savedStateHandle.toRoute<RoutineRunRoute>().routineId

    val item: StateFlow<RoutineWithSteps?> = combine(
        routines.observeRoutines().map { list -> list.firstOrNull { it.id == routineId } },
        observeDayOverview(flowOf(time.today())),
    ) { routine, overview ->
        routine?.let { r ->
            val byId = overview.habits.associateBy { it.habit.id }
            RoutineWithSteps(r, r.habitIds.mapNotNull { byId[it] })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun toggle(habitId: String) = viewModelScope.launch {
        runCatching { habits.toggleCompletion(habitId, time.today()) }.onFailure { Log.e("RoutineRun", "toggle failed", it) }
    }

    fun addProgress(habitId: String, amount: Double, note: String?) = viewModelScope.launch {
        runCatching { habits.addProgress(habitId, time.today(), amount, note) }.onFailure { Log.e("RoutineRun", "progress failed", it) }
    }

    fun completeStep(habitId: String) = viewModelScope.launch {
        runCatching { habits.completeDay(habitId, time.today()) }.onFailure { Log.e("RoutineRun", "complete failed", it) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineRunScreen(onBack: () -> Unit, onOpenHabit: (String) -> Unit, onTimer: (String) -> Unit, viewModel: RoutineRunViewModel = hiltViewModel()) {
    val item by viewModel.item.collectAsStateWithLifecycle()
    var amountFor by remember { mutableStateOf<HabitDayItem?>(null) }
    val actions = remember(viewModel) {
        HabitCardActions(
            onOpen = onOpenHabit,
            onToggle = { viewModel.toggle(it) },
            onIncrement = { id, amount -> viewModel.addProgress(id, amount, null) },
            onCustomAmount = { amountFor = it },
            onTimer = onTimer,
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(item?.routine?.let { "${it.emoji} ${it.name}" }.orEmpty()) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back)) } },
            )
        },
    ) { padding ->
        val current = item
        if (current == null) {
            FullScreenLoading(Modifier.padding(padding))
            return@Scaffold
        }
        val nextStep = current.steps.firstOrNull { !it.done && it.habit.type != HabitType.QUIT }
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 32.dp)) {
            item(key = "summary") {
                Column(Modifier.padding(HabitTheme.spacing.screenHorizontal)) {
                    Text(
                        stringResource(R.string.routine_steps_progress, current.completed, current.steps.size),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    current.routine.estimatedMinutes?.let {
                        Text(stringResource(R.string.routine_estimated, it), style = MaterialTheme.typography.bodySmall)
                    }
                    LinearProgressIndicator(
                        progress = { if (current.steps.isEmpty()) 0f else current.completed.toFloat() / current.steps.size },
                        modifier = Modifier.fillMaxWidth().padding(vertical = HabitTheme.spacing.sm),
                    )
                    if (nextStep != null) {
                        Button(onClick = { viewModel.completeStep(nextStep.habit.id) }) {
                            Text(stringResource(R.string.routine_complete_next, nextStep.habit.name))
                        }
                    } else if (current.steps.isNotEmpty()) {
                        Text(stringResource(R.string.routine_all_done), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    if (current.steps.size < current.routine.habitIds.size) {
                        Text(
                            stringResource(R.string.routine_some_not_scheduled),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(current.steps, key = { it.habit.id }) { step ->
                HabitCard(
                    item = step,
                    actions = actions,
                    modifier = Modifier.padding(horizontal = HabitTheme.spacing.screenHorizontal, vertical = HabitTheme.spacing.xs),
                )
            }
        }
    }
    amountFor?.let { step ->
        AmountDialog(step.habit, { amount, note -> viewModel.addProgress(step.habit.id, amount, note) }, { amountFor = null })
    }
}

@HiltViewModel
class RoutineEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val routines: RoutineRepository,
    habits: HabitRepository,
) : ViewModel() {
    private val routineId = savedStateHandle.toRoute<RoutineEditorRoute>().routineId

    val allHabits: StateFlow<List<Habit>> = habits.observeHabits().map { list -> list.filter { !it.archived }.sortedBy { it.sortOrder } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val draft = MutableStateFlow<Routine?>(null)
    val saved = MutableStateFlow(false)
    val error = MutableStateFlow<Int?>(null)

    init {
        viewModelScope.launch {
            val existing = routineId?.let { id -> routines.observeRoutines().first().firstOrNull { it.id == id } }
            draft.value = existing ?: Routine(UUID.randomUUID().toString(), "", "🌅", null, 0, emptyList())
        }
    }

    fun update(transform: (Routine) -> Routine) {
        draft.value = draft.value?.let(transform)
        error.value = null
    }

    fun save() {
        val routine = draft.value ?: return
        when {
            routine.name.isBlank() -> error.value = R.string.error_name_required
            routine.habitIds.isEmpty() -> error.value = R.string.routine_error_no_steps
            else -> viewModelScope.launch {
                runCatching { routines.saveRoutine(routine) }
                    .onSuccess { saved.value = true }
                    .onFailure {
                        Log.e("RoutineEditor", "save failed", it)
                        error.value = R.string.error_save_failed
                    }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineEditorScreen(onBack: () -> Unit, viewModel: RoutineEditorViewModel = hiltViewModel()) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val habits by viewModel.allHabits.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var emojiPicker by remember { mutableStateOf(false) }
    LaunchedEffect(saved) { if (saved) onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.routine_edit)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.action_close)) } },
                actions = { TextButton(onClick = viewModel::save) { Text(stringResource(R.string.action_save)) } },
            )
        },
    ) { padding ->
        val routine = draft
        if (routine == null) {
            FullScreenLoading(Modifier.padding(padding))
            return@Scaffold
        }
        val byId = habits.associateBy { it.id }
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 32.dp)) {
            item(key = "fields") {
                Column(Modifier.padding(horizontal = HabitTheme.spacing.screenHorizontal), verticalArrangement = Arrangement.spacedBy(HabitTheme.spacing.sm)) {
                    error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.clickable { emojiPicker = true }) { EmojiBadge(routine.emoji, HabitColor.INDIGO.resolve(), 52) }
                        OutlinedTextField(
                            value = routine.name,
                            onValueChange = { v -> viewModel.update { it.copy(name = v.take(50)) } },
                            label = { Text(stringResource(R.string.habit_name)) },
                            placeholder = { Text(stringResource(R.string.routine_name_hint)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f).padding(start = HabitTheme.spacing.md),
                        )
                    }
                    OutlinedTextField(
                        value = routine.estimatedMinutes?.toString().orEmpty(),
                        onValueChange = { v -> viewModel.update { it.copy(estimatedMinutes = v.filter(Char::isDigit).take(3).toIntOrNull()) } },
                        label = { Text(stringResource(R.string.routine_estimated_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }
            item(key = "stepsHeader") { SectionHeader(stringResource(R.string.routine_steps)) }
            items(routine.habitIds, key = { "step_$it" }) { habitId ->
                val habit = byId[habitId] ?: return@items
                val index = routine.habitIds.indexOf(habitId)
                ListItem(
                    leadingContent = { Text("${index + 1}. ${habit.emoji}") },
                    headlineContent = { Text(habit.name) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { viewModel.update { it.copy(habitIds = it.habitIds.move(index, -1)) } }, enabled = index > 0) {
                                Icon(painterResource(R.drawable.ic_collapse), stringResource(R.string.move_up))
                            }
                            IconButton(
                                onClick = { viewModel.update { it.copy(habitIds = it.habitIds.move(index, 1)) } },
                                enabled = index < routine.habitIds.lastIndex,
                            ) { Icon(painterResource(R.drawable.ic_expand), stringResource(R.string.move_down)) }
                            IconButton(onClick = { viewModel.update { it.copy(habitIds = it.habitIds - habitId) } }) {
                                Icon(painterResource(R.drawable.ic_close), stringResource(R.string.action_remove))
                            }
                        }
                    },
                )
            }
            item(key = "addHeader") { SectionHeader(stringResource(R.string.routine_add_steps)) }
            val available = habits.filter { it.id !in routine.habitIds }
            if (habits.isEmpty()) {
                item(key = "noHabits") {
                    Text(stringResource(R.string.routine_no_habits), modifier = Modifier.padding(horizontal = HabitTheme.spacing.screenHorizontal))
                }
            }
            items(available, key = { "add_" + it.id }) { habit ->
                ListItem(
                    leadingContent = { Checkbox(checked = false, onCheckedChange = { viewModel.update { it.copy(habitIds = it.habitIds + habit.id) } }) },
                    headlineContent = { Text("${habit.emoji} ${habit.name}") },
                    modifier = Modifier.clickable { viewModel.update { it.copy(habitIds = it.habitIds + habit.id) } },
                )
            }
        }
    }
    if (emojiPicker) {
        EmojiPickerDialog(onSelected = { e -> viewModel.update { it.copy(emoji = e) } }, onDismiss = { emojiPicker = false })
    }
}

private fun List<String>.move(index: Int, delta: Int): List<String> {
    val target = index + delta
    if (index !in indices || target !in indices) return this
    return toMutableList().apply { add(target, removeAt(index)) }
}
