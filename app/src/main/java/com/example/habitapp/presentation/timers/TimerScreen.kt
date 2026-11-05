package com.example.habitapp.presentation.timers

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.habitapp.R
import com.example.habitapp.domain.model.Habit
import com.example.habitapp.domain.model.TimerMode
import com.example.habitapp.domain.model.TimerSession
import com.example.habitapp.domain.repository.HabitRepository
import com.example.habitapp.domain.repository.TimerRepository
import com.example.habitapp.domain.util.TimeProvider
import com.example.habitapp.presentation.components.EmojiBadge
import com.example.habitapp.presentation.components.ProgressRing
import com.example.habitapp.presentation.components.formatClock
import com.example.habitapp.presentation.navigation.TimerRoute
import com.example.habitapp.presentation.theme.HabitColor
import com.example.habitapp.presentation.theme.HabitTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Timer state lives in Room (start instant + accumulated time), so it survives rotation,
 * navigation, backgrounding and process death. The UI only re-renders once per second.
 */
@HiltViewModel
class TimerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    habitRepository: HabitRepository,
    private val timers: TimerRepository,
    private val time: TimeProvider,
) : ViewModel() {
    val habitId: String = savedStateHandle.toRoute<TimerRoute>().habitId

    val habit: StateFlow<Habit?> = habitRepository.observeHabit(habitId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val session: StateFlow<TimerSession?> = timers.observeActiveTimer()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _savedMinutes = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val savedMinutes: SharedFlow<Int> = _savedMinutes.asSharedFlow()

    fun now(): Instant = time.nowInstant()

    fun start(mode: TimerMode, minutes: Int) = launch {
        timers.start(habitId, mode, if (mode == TimerMode.COUNTDOWN) minutes * 60_000L else null)
    }

    fun pause() = launch { timers.pause() }
    fun resume() = launch { timers.resume() }
    fun reset() = launch { timers.reset() }
    fun discard() = launch { timers.discard() }
    fun save() = launch { _savedMinutes.emit(timers.finishAndSave()) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Log.e("TimerViewModel", "Timer action failed", error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(onBack: () -> Unit, viewModel: TimerViewModel = hiltViewModel()) {
    val habit by viewModel.habit.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val savedText = stringResource(R.string.timer_saved)
    val nothingText = stringResource(R.string.timer_nothing_saved)
    LaunchedEffect(viewModel) {
        viewModel.savedMinutes.collect { minutes ->
            snackbar.showSnackbar(if (minutes > 0) savedText.format(minutes) else nothingText)
        }
    }
    // Re-read "now" every second while the screen is visible.
    var now by remember { mutableLongStateOf(viewModel.now().toEpochMilli()) }
    LaunchedEffect(session?.isRunning) {
        while (true) {
            now = viewModel.now().toEpochMilli()
            delay(1_000)
        }
    }
    val active = session?.takeIf { it.habitId == viewModel.habitId }
    val otherActive = session != null && active == null
    val accent = HabitColor.fromKey(habit?.colorKey).resolve()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.timer_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(HabitTheme.spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HabitTheme.spacing.lg),
        ) {
            habit?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EmojiBadge(it.emoji, accent, 40)
                    Text(it.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = HabitTheme.spacing.md))
                }
            }
            if (active == null) {
                if (otherActive) {
                    Text(stringResource(R.string.timer_other_running), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                TimerSetup(
                    defaultMinutes = habit?.target?.toInt()?.coerceIn(1, 600) ?: 25,
                    onStart = viewModel::start,
                )
            } else {
                val instant = Instant.ofEpochMilli(now)
                val elapsed = active.elapsedMillis(instant)
                val remaining = active.remainingMillis(instant)
                val finished = remaining == 0L
                val fraction = active.countdownMillis?.let { (elapsed.toFloat() / it).coerceIn(0f, 1f) }
                    ?: ((elapsed % 3_600_000L) / 3_600_000f)
                ProgressRing(fraction = fraction, color = accent, size = 240.dp, stroke = 14.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(formatClock(remaining ?: elapsed), fontSize = 44.sp, style = MaterialTheme.typography.displaySmall)
                        Text(
                            stringResource(
                                when {
                                    finished -> R.string.timer_finished
                                    active.isRunning -> R.string.timer_running
                                    else -> R.string.timer_paused
                                },
                            ),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(HabitTheme.spacing.sm)) {
                    if (active.isRunning && !finished) {
                        FilledTonalButton(onClick = viewModel::pause) { Text(stringResource(R.string.timer_pause)) }
                    } else if (!finished) {
                        FilledTonalButton(onClick = viewModel::resume) { Text(stringResource(R.string.timer_resume)) }
                    }
                    OutlinedButton(onClick = viewModel::reset) { Text(stringResource(R.string.timer_reset)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(HabitTheme.spacing.sm)) {
                    Button(onClick = viewModel::save) { Text(stringResource(R.string.timer_save)) }
                    OutlinedButton(onClick = viewModel::discard) { Text(stringResource(R.string.timer_discard)) }
                }
                Text(
                    stringResource(R.string.timer_background_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimerSetup(defaultMinutes: Int, onStart: (TimerMode, Int) -> Unit) {
    var mode by rememberSaveable { mutableStateOf(TimerMode.STOPWATCH) }
    var minutes by rememberSaveable { mutableStateOf(defaultMinutes.toString()) }
    SingleChoiceSegmentedButtonRow {
        TimerMode.entries.forEachIndexed { index, m ->
            SegmentedButton(
                selected = mode == m,
                onClick = { mode = m },
                shape = SegmentedButtonDefaults.itemShape(index, TimerMode.entries.size),
            ) {
                Text(stringResource(if (m == TimerMode.STOPWATCH) R.string.timer_stopwatch else R.string.timer_countdown))
            }
        }
    }
    if (mode == TimerMode.COUNTDOWN) {
        OutlinedTextField(
            value = minutes,
            onValueChange = { minutes = it.filter(Char::isDigit).take(3) },
            label = { Text(stringResource(R.string.unit_minutes)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
    Spacer(Modifier.height(HabitTheme.spacing.md))
    val valid = mode == TimerMode.STOPWATCH || (minutes.toIntOrNull() ?: 0) > 0
    Button(onClick = { onStart(mode, minutes.toIntOrNull() ?: 0) }, enabled = valid) {
        Icon(painterResource(R.drawable.ic_play), null)
        Text(stringResource(R.string.timer_start), modifier = Modifier.padding(start = HabitTheme.spacing.sm))
    }
}
