package com.example.habitapp.presentation.timers

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.example.habitapp.domain.model.HabitType
import com.example.habitapp.domain.model.TimerMode
import com.example.habitapp.domain.model.TimerSession
import com.example.habitapp.domain.repository.HabitRepository
import com.example.habitapp.domain.repository.TimerRepository
import com.example.habitapp.domain.repository.UserPreferencesRepository
import com.example.habitapp.domain.util.TimeProvider
import com.example.habitapp.presentation.components.ProgressRing
import com.example.habitapp.presentation.components.formatClock
import com.example.habitapp.presentation.components.rememberDateFormatter
import com.example.habitapp.presentation.components.rememberTimeFormatter
import com.example.habitapp.presentation.home.BigButton
import com.example.habitapp.presentation.home.TileIconButton
import com.example.habitapp.presentation.navigation.TimerRoute
import com.example.habitapp.presentation.theme.HabitColor
import com.example.habitapp.presentation.theme.OnPastel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

object TimerTestTags {
    const val START = "timer_start"
    const val HABIT = "timer_habit"
    const val LAP = "timer_lap"
    const val SAVE = "timer_save"
}

/**
 * Global timer. Timer state lives in Room (start instant + accumulated time), so it survives rotation,
 * navigation, backgrounding and process death. Laps are kept in the saved state of the screen.
 */
@HiltViewModel
class TimerViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    habitRepository: HabitRepository,
    private val timers: TimerRepository,
    private val preferences: UserPreferencesRepository,
    private val time: TimeProvider,
) : ViewModel() {
    private val routeHabit: String? = savedStateHandle.toRoute<TimerRoute>().habitId

    val selectedHabitId: StateFlow<String?> = savedStateHandle.getStateFlow(KEY_HABIT, routeHabit)

    val habits: StateFlow<List<Habit>> = habitRepository.observeHabits()
        .map { list -> list.filter { !it.archived && it.type != HabitType.QUIT }.sortedBy { it.sortOrder } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val session: StateFlow<TimerSession?> = timers.observeActiveTimer()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val recent: StateFlow<List<TimerSession>> = timers.observeRecentSessions(30)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val laps: StateFlow<LongArray> = savedStateHandle.getStateFlow(KEY_LAPS, LongArray(0))

    val defaultCountdown: StateFlow<Boolean?> = preferences.preferences.map { it.timerDefaultCountdown }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _savedMinutes = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val savedMinutes: SharedFlow<Int> = _savedMinutes.asSharedFlow()

    fun now(): Instant = time.nowInstant()

    fun zone(): ZoneId = time.zone()

    fun selectHabit(id: String) {
        savedStateHandle[KEY_HABIT] = id
    }

    fun start(mode: TimerMode, minutes: Int) = launch {
        val habitId = selectedHabitId.value ?: habits.value.firstOrNull()?.id ?: return@launch
        savedStateHandle[KEY_LAPS] = LongArray(0)
        timers.start(habitId, mode, if (mode == TimerMode.COUNTDOWN) minutes * 60_000L else null)
        preferences.setTimerDefaultCountdown(mode == TimerMode.COUNTDOWN)
    }

    fun lap() {
        val active = session.value ?: return
        savedStateHandle[KEY_LAPS] = laps.value + active.elapsedMillis(time.nowInstant())
    }

    fun pause() = launch { timers.pause() }
    fun resume() = launch { timers.resume() }
    fun reset() = launch {
        savedStateHandle[KEY_LAPS] = LongArray(0)
        timers.reset()
    }
    fun discard() = launch {
        savedStateHandle[KEY_LAPS] = LongArray(0)
        timers.discard()
    }
    fun save() = launch {
        savedStateHandle[KEY_LAPS] = LongArray(0)
        _savedMinutes.emit(timers.finishAndSave())
    }

    /** Waits for the stored preference so the first frame shows the right mode. */
    suspend fun initialCountdown(): Boolean = preferences.preferences.first().timerDefaultCountdown

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

    private companion object {
        const val KEY_HABIT = "habit"
        const val KEY_LAPS = "laps"
    }
}

@Composable
fun TimerScreen(onBack: () -> Unit, viewModel: TimerViewModel = hiltViewModel()) {
    val habits by viewModel.habits.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedHabitId.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val laps by viewModel.laps.collectAsStateWithLifecycle()
    val defaultCountdown by viewModel.defaultCountdown.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val savedText = stringResource(R.string.timer_saved)
    val nothingText = stringResource(R.string.timer_nothing_saved)
    LaunchedEffect(viewModel) {
        viewModel.savedMinutes.collect { minutes ->
            snackbar.showSnackbar(if (minutes > 0) savedText.format(minutes) else nothingText)
        }
    }
    var now by remember { mutableLongStateOf(viewModel.now().toEpochMilli()) }
    LaunchedEffect(session?.isRunning) {
        while (true) {
            now = viewModel.now().toEpochMilli()
            delay(if (session?.isRunning == true) 200 else 1_000)
        }
    }
    val active = session
    val habit = habits.firstOrNull { it.id == (active?.habitId ?: selectedId) } ?: if (active == null) habits.firstOrNull() else null
    val accent = HabitColor.fromKey(habit?.colorKey).color
    val dateFormatter = rememberDateFormatter()
    val timeFormatter = rememberTimeFormatter()

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            item {
                Box(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp)) {
                    TileIconButton(R.drawable.ic_arrow_back, stringResource(R.string.navigate_back), onBack)
                    Text(
                        stringResource(R.string.timer_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
            item {
                HabitSelector(habits, habit, enabled = active == null, onSelect = viewModel::selectHabit)
                Spacer(Modifier.height(16.dp))
            }
            if (habits.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.timer_no_habits),
                        modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (active == null) {
                item {
                    defaultCountdown?.let { countdown ->
                        TimerSetup(
                            initialCountdown = countdown,
                            defaultMinutes = habit?.timerMinutes ?: habit?.target?.toInt()?.takeIf { habit.type == HabitType.DURATION }?.coerceIn(1, 600) ?: 25,
                            onStart = viewModel::start,
                        )
                    }
                }
            } else {
                item {
                    val instant = Instant.ofEpochMilli(now)
                    val elapsed = active.elapsedMillis(instant)
                    val remaining = active.remainingMillis(instant)
                    val finished = remaining == 0L
                    val fraction = active.countdownMillis?.let { (elapsed.toFloat() / it).coerceIn(0f, 1f) }
                        ?: ((elapsed % 60_000L) / 60_000f)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ProgressRing(fraction = fraction, color = accent, size = 250.dp, stroke = 12.dp) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(formatClock(remaining ?: elapsed), fontSize = 46.sp, fontWeight = FontWeight.Light)
                                Text(
                                    stringResource(
                                        when {
                                            finished -> R.string.timer_finished
                                            active.isRunning -> R.string.timer_running
                                            else -> R.string.timer_paused
                                        },
                                    ),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            RoundAction(R.drawable.ic_reset, stringResource(R.string.timer_reset), false, viewModel::reset)
                            when {
                                finished -> RoundAction(R.drawable.ic_check, stringResource(R.string.timer_save), true, viewModel::save, big = true)
                                active.isRunning -> RoundAction(R.drawable.ic_pause, stringResource(R.string.timer_pause), true, viewModel::pause, big = true)
                                else -> RoundAction(R.drawable.ic_play, stringResource(R.string.timer_resume), true, viewModel::resume, big = true)
                            }
                            if (active.mode == TimerMode.STOPWATCH) {
                                RoundAction(R.drawable.ic_lap, stringResource(R.string.timer_lap), false, viewModel::lap, Modifier.testTag(TimerTestTags.LAP))
                            } else {
                                RoundAction(R.drawable.ic_close, stringResource(R.string.timer_discard), false, viewModel::discard)
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (active.mode == TimerMode.STOPWATCH) {
                                BigButton(stringResource(R.string.timer_discard), R.drawable.ic_close, primary = false, modifier = Modifier.weight(1f), onClick = viewModel::discard)
                            }
                            BigButton(
                                stringResource(R.string.timer_save),
                                R.drawable.ic_save,
                                primary = true,
                                modifier = Modifier.weight(1f).testTag(TimerTestTags.SAVE),
                                onClick = viewModel::save,
                            )
                        }
                        Text(
                            stringResource(R.string.timer_background_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                if (laps.isNotEmpty()) {
                    item { SectionLabel(stringResource(R.string.timer_laps)) }
                    val lapList = laps.toList()
                    items(lapList.indices.reversed().toList()) { i ->
                        val split = lapList[i] - (if (i > 0) lapList[i - 1] else 0L)
                        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                            Text(stringResource(R.string.timer_lap_n, i + 1), modifier = Modifier.weight(1f))
                            Text("+" + formatClock(split), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(16.dp))
                            Text(formatClock(lapList[i]), fontWeight = FontWeight.Bold)
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 24.dp))
                    }
                }
            }
            if (recent.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.timer_sessions)) }
                items(recent, key = { it.id }) { s ->
                    val h = habits.firstOrNull { it.id == s.habitId }
                    val end = s.finishedAt ?: s.startedAt
                    val zoned = end.atZone(viewModel.zone())
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(HabitColor.fromKey(h?.colorKey).color))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(h?.name ?: "—", fontWeight = FontWeight.SemiBold)
                            Text(
                                "${zoned.toLocalDate().format(dateFormatter)} · ${zoned.toLocalTime().format(timeFormatter)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(formatClock(s.accumulatedMillis), fontWeight = FontWeight.Bold)
                    }
                }
            }
            item { Spacer(Modifier.navigationBarsPadding().height(24.dp)) }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun HabitSelector(habits: List<Habit>, selected: Habit?, enabled: Boolean, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.testTag(TimerTestTags.HABIT).clip(RoundedCornerShape(50))
                .background(HabitColor.fromKey(selected?.colorKey).color)
                .clickable(enabled = enabled && habits.isNotEmpty()) { open = true }
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(selected?.name ?: stringResource(R.string.timer_choose_habit), color = OnPastel, fontWeight = FontWeight.Bold)
            if (enabled) {
                Spacer(Modifier.width(6.dp))
                Icon(painterResource(R.drawable.ic_expand), null, tint = OnPastel, modifier = Modifier.size(20.dp))
            }
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            habits.forEach { h ->
                DropdownMenuItem(
                    text = { Text(h.name) },
                    leadingIcon = { Box(Modifier.size(14.dp).clip(CircleShape).background(HabitColor.fromKey(h.colorKey).color)) },
                    onClick = {
                        onSelect(h.id)
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun RoundAction(icon: Int, label: String, primary: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, big: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            Modifier.size(if (big) 76.dp else 56.dp).clip(CircleShape)
                .background(if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(icon),
                label,
                tint = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(if (big) 36.dp else 26.dp),
            )
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun TimerSetup(initialCountdown: Boolean, defaultMinutes: Int, onStart: (TimerMode, Int) -> Unit) {
    var mode by rememberSaveable { mutableStateOf(if (initialCountdown) TimerMode.COUNTDOWN else TimerMode.STOPWATCH) }
    var minutes by rememberSaveable(defaultMinutes) { mutableStateOf(defaultMinutes) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(4.dp),
        ) {
            TimerMode.entries.forEach { m ->
                val selected = mode == m
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { mode = m }.padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(if (m == TimerMode.STOPWATCH) R.string.timer_stopwatch else R.string.timer_countdown),
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        if (mode == TimerMode.COUNTDOWN) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TileIconButton(R.drawable.ic_remove, stringResource(R.string.action_decrease), { minutes = (minutes - 5).coerceAtLeast(1) })
                Text(
                    formatClock(minutes * 60_000L),
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Light,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                TileIconButton(R.drawable.ic_add, stringResource(R.string.action_increase), { minutes = (minutes + 5).coerceAtMost(600) })
            }
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 10, 15, 25, 45, 60).forEach { preset ->
                    Box(
                        Modifier.clip(RoundedCornerShape(8.dp))
                            .background(if (preset == minutes) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { minutes = preset }.padding(horizontal = 10.dp, vertical = 6.dp),
                    ) { Text("$preset", style = MaterialTheme.typography.labelLarge) }
                }
            }
        } else {
            Text(formatClock(0), fontSize = 52.sp, fontWeight = FontWeight.Light)
        }
        Spacer(Modifier.height(28.dp))
        BigButton(
            stringResource(R.string.timer_start),
            R.drawable.ic_play,
            primary = true,
            modifier = Modifier.fillMaxWidth().testTag(TimerTestTags.START),
            onClick = { onStart(mode, minutes) },
        )
    }
}
