package com.example.habitapp.presentation.habits

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.habitapp.R
import com.example.habitapp.domain.model.GoalPeriod
import com.example.habitapp.domain.model.GoalType
import com.example.habitapp.domain.model.HabitActivity
import com.example.habitapp.domain.model.HabitType
import com.example.habitapp.domain.util.QuitCalculator
import com.example.habitapp.domain.util.QuitStatus
import com.example.habitapp.presentation.components.BarChart
import com.example.habitapp.presentation.components.BarEntry
import com.example.habitapp.presentation.components.ConfirmDialog
import com.example.habitapp.presentation.components.FullScreenError
import com.example.habitapp.presentation.components.FullScreenLoading
import com.example.habitapp.presentation.components.LocalDatePickerDialog
import com.example.habitapp.presentation.components.RichText
import com.example.habitapp.presentation.components.currentLocale
import com.example.habitapp.presentation.components.elapsedText
import com.example.habitapp.presentation.components.formatAmount
import com.example.habitapp.presentation.components.habitValueText
import com.example.habitapp.presentation.components.narrowDayName
import com.example.habitapp.presentation.components.rememberDateFormatter
import com.example.habitapp.presentation.components.rememberTimeFormatter
import com.example.habitapp.presentation.components.shortDayName
import com.example.habitapp.presentation.components.streakText
import com.example.habitapp.presentation.home.BigButton
import com.example.habitapp.presentation.home.TileIconButton
import com.example.habitapp.presentation.theme.HabitColor
import com.example.habitapp.presentation.theme.OnPastel
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

class HabitDetailNavigation(
    val onBack: () -> Unit,
    val onEdit: (String) -> Unit,
    val onOpenHabit: (String) -> Unit,
    val onTimer: (String) -> Unit,
    val onReport: (String) -> Unit,
    val onNewNote: (habitId: String, day: LocalDate) -> Unit,
    val onOpenNote: (entryId: String) -> Unit,
)

private enum class DetailTab { TRACK, STATS, LOG }

@Composable
fun HabitDetailScreen(navigation: HabitDetailNavigation, viewModel: HabitDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(DetailTab.TRACK) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                HabitDetailEvent.Deleted -> navigation.onBack()
                is HabitDetailEvent.Duplicated -> navigation.onOpenHabit(event.newId)
            }
        }
    }

    val ready = state as? HabitDetailUiState.Ready
    val color = ready?.let { HabitColor.fromKey(it.habit.colorKey).color } ?: MaterialTheme.colorScheme.surfaceContainerHigh
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxWidth().background(color).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(OnPastel.copy(alpha = 0.08f)).clickable(onClick = navigation.onBack),
                    contentAlignment = Alignment.Center,
                ) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back), tint = OnPastel) }
                Text(
                    ready?.habit?.name.orEmpty(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = OnPastel,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(OnPastel.copy(alpha = 0.08f))
                        .clickable { ready?.let { navigation.onEdit(it.habit.id) } },
                    contentAlignment = Alignment.Center,
                ) { Icon(painterResource(R.drawable.ic_edit), stringResource(R.string.action_edit), tint = OnPastel) }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DetailTab.entries.forEach { t ->
                    val selected = tab == t
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) OnPastel else OnPastel.copy(alpha = 0.08f))
                            .clickable { tab = t }
                            .padding(vertical = 10.dp)
                            .testTag("detail_tab_${t.name}"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(
                                when (t) {
                                    DetailTab.TRACK -> R.string.detail_track
                                    DetailTab.STATS -> R.string.detail_stats
                                    DetailTab.LOG -> R.string.detail_log
                                },
                            ),
                            color = if (selected) color else OnPastel,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        when (val s = state) {
            HabitDetailUiState.Loading -> FullScreenLoading()
            HabitDetailUiState.NotFound -> FullScreenError(stringResource(R.string.error_not_found))
            HabitDetailUiState.Error -> FullScreenError(stringResource(R.string.error_loading))
            is HabitDetailUiState.Ready -> when (tab) {
                DetailTab.TRACK -> TrackTab(s, viewModel, navigation, onDelete = { confirmDelete = true })
                DetailTab.STATS -> StatsTab(s)
                DetailTab.LOG -> LogTab(s, viewModel, navigation)
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.habit_delete_title),
            message = stringResource(R.string.habit_delete_message),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = viewModel::delete,
            onDismiss = { confirmDelete = false },
        )
    }
}

// ---------------------------------------------------------------------------
// Track
// ---------------------------------------------------------------------------

@Composable
private fun TrackTab(s: HabitDetailUiState.Ready, viewModel: HabitDetailViewModel, navigation: HabitDetailNavigation, onDelete: () -> Unit) {
    val habit = s.habit
    var month by rememberSaveable { mutableStateOf(YearMonth.from(s.selectedDate).toString()) }
    var entryDialog by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var relapse by remember { mutableStateOf(false) }
    val ym = YearMonth.parse(month)

    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
        item(key = "calendar") {
            MonthCalendar(
                month = ym,
                selected = s.selectedDate,
                today = s.today,
                firstDay = s.firstDayOfWeek,
                colorKey = habit.colorKey,
                fraction = { date ->
                    val p = s.progress[date]
                    when {
                        date.isBefore(habit.startDate) || date.isAfter(s.today) -> null
                        habit.type == HabitType.QUIT -> if (p != null && p.total > 0) 0.3f else 1f
                        p == null -> if (habit.goalType == GoalType.AT_MOST && habit.goalPeriod == GoalPeriod.DAY) 1f else 0f
                        habit.goalType == GoalType.AT_MOST -> if (p.completed) 1f else 0.3f
                        else -> p.fraction
                    }
                },
                onSelect = viewModel::selectDate,
                onMonth = { month = it.toString() },
            )
        }
        if (habit.type == HabitType.QUIT && s.quit != null) {
            item(key = "quit") { QuitPanel(s.quit, habit.colorKey, onRelapse = { relapse = true }) }
        } else {
            item(key = "counter") {
                val selectedDayTotal = s.progress[s.selectedDate]?.total ?: 0.0
                val periodTotal = s.selectedPeriod.total
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RoundAction(R.drawable.ic_remove, stringResource(R.string.action_decrement), enabled = selectedDayTotal > 0) {
                            viewModel.addEntry(s.selectedDate, -habit.step, null)
                        }
                        Column(Modifier.width(170.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(formatAmount(periodTotal), fontSize = 56.sp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("detail_counter"))
                            Text(unitLabel(habitUnitKey(habit), periodTotal), style = MaterialTheme.typography.titleMedium)
                        }
                        RoundAction(R.drawable.ic_add, stringResource(R.string.action_increment), enabled = !s.selectedDate.isAfter(s.today), modifier = Modifier.testTag("detail_plus")) {
                            if (habit.type == HabitType.BOOLEAN) viewModel.toggleDay(s.selectedDate) else viewModel.addEntry(s.selectedDate, habit.step, null)
                        }
                    }
                    Text(
                        stringResource(
                            if (habit.goalType == GoalType.AT_LEAST) R.string.goal_line_at_least else R.string.goal_line_at_most,
                            formatAmount(habit.target),
                            unitLabel(habitUnitKey(habit), habit.target),
                            periodLabel(habit.goalPeriod).lowercase(currentLocale()),
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (s.selectedPeriod.met && periodTotal > 0) {
                        Text(stringResource(R.string.goal_reached), color = HabitColor.fromKey(habit.colorKey).color, fontWeight = FontWeight.Bold)
                    }
                    if (s.progress[s.selectedDate]?.skipped == true) {
                        Text(stringResource(R.string.habit_skipped), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item(key = "actions") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (habit.type != HabitType.QUIT) {
                    ActionTile(R.drawable.ic_edit, stringResource(R.string.detail_enter), Modifier.weight(1f)) { entryDialog = true }
                    ActionTile(R.drawable.ic_timer, stringResource(R.string.timer_title), Modifier.weight(1f)) { navigation.onTimer(habit.id) }
                }
                ActionTile(R.drawable.ic_note, stringResource(R.string.detail_note), Modifier.weight(1f)) { navigation.onNewNote(habit.id, s.selectedDate) }
                Box(Modifier.weight(1f)) {
                    ActionTile(R.drawable.ic_more, stringResource(R.string.detail_more), Modifier.fillMaxWidth()) { moreMenu = true }
                    DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                        if (habit.type != HabitType.QUIT) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.habit_skip_day)) }, onClick = { moreMenu = false; viewModel.skipDay(s.selectedDate) })
                            DropdownMenuItem(text = { Text(stringResource(R.string.habit_clear_day)) }, onClick = { moreMenu = false; viewModel.clearDay(s.selectedDate) })
                        }
                        DropdownMenuItem(text = { Text(stringResource(R.string.habit_report)) }, onClick = { moreMenu = false; navigation.onReport(habit.id) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_duplicate)) }, onClick = { moreMenu = false; viewModel.duplicate() })
                        DropdownMenuItem(
                            text = { Text(stringResource(if (habit.archived) R.string.action_restore else R.string.action_archive)) },
                            onClick = { moreMenu = false; viewModel.setArchived(!habit.archived) },
                        )
                        DropdownMenuItem(text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }, onClick = { moreMenu = false; onDelete() })
                    }
                }
            }
        }
        if (habit.description.isNotBlank()) {
            item(key = "description") {
                Text(habit.description, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
            }
        }
        val dayEntries = s.activities.filter { it.date == s.selectedDate }
        if (dayEntries.isNotEmpty()) {
            item(key = "dayEntriesTitle") {
                Text(
                    stringResource(R.string.detail_entries_of_day),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            items(dayEntries, key = { "day_${it.id}" }) { a -> ActivityRow(a, habit.type, habit, viewModel) }
        }
    }
    if (entryDialog) {
        EntryDialog(
            habitType = habit.type,
            unit = unitLabel(habitUnitKey(habit), 2.0),
            initialDate = s.selectedDate,
            initialValue = if (habit.type == HabitType.BOOLEAN) 1.0 else habit.step,
            title = stringResource(R.string.habit_log_entry),
            onConfirm = { date, value, note ->
                if (habit.type == HabitType.BOOLEAN) {
                    viewModel.completeDay(date)
                    if (!note.isNullOrBlank()) viewModel.addEntry(date, 0.0, note)
                } else {
                    viewModel.addEntry(date, value, note)
                }
            },
            onDismiss = { entryDialog = false },
        )
    }
    if (relapse) {
        RelapseDialog(onConfirm = { note -> viewModel.recordRelapse(viewModel.now(), note) }, onDismiss = { relapse = false })
    }
}

/** Unit key used by [unitLabel] for an existing habit. */
fun habitUnitKey(habit: com.example.habitapp.domain.model.Habit): String = when (habit.type) {
    HabitType.DURATION -> HabitUnits.MINUTES
    HabitType.BOOLEAN -> HabitUnits.TIMES
    HabitType.COUNT -> habit.unit.ifBlank { HabitUnits.TIMES }
    else -> habit.unit
}

@Composable
private fun MonthCalendar(
    month: YearMonth,
    selected: LocalDate,
    today: LocalDate,
    firstDay: java.time.DayOfWeek,
    colorKey: String,
    fraction: (LocalDate) -> Float?,
    onSelect: (LocalDate) -> Unit,
    onMonth: (YearMonth) -> Unit,
) {
    val locale = currentLocale()
    val color = HabitColor.fromKey(colorKey).color
    Column(Modifier.padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)).replaceFirstChar { it.titlecase(locale) },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            TileIconButton(R.drawable.ic_chevron_left, stringResource(R.string.calendar_previous), { onMonth(month.minusMonths(1)) })
            Spacer(Modifier.width(6.dp))
            TileIconButton(R.drawable.ic_chevron_right, stringResource(R.string.calendar_next), { onMonth(month.plusMonths(1)) })
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            (0 until 7).forEach { i ->
                Text(
                    narrowDayName(firstDay.plus(i.toLong()), locale),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val first = month.atDay(1)
        val start = first.with(TemporalAdjusters.previousOrSame(firstDay))
        val cells = ((first.toEpochDay() - start.toEpochDay()).toInt() + month.lengthOfMonth() + 6) / 7 * 7
        (0 until cells / 7).forEach { w ->
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (0 until 7).forEach { d ->
                    val date = start.plusDays((w * 7 + d).toLong())
                    if (date.month != month.month) {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val f = fraction(date)
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    when {
                                        f == null -> MaterialTheme.colorScheme.surfaceContainerLow
                                        f <= 0f -> MaterialTheme.colorScheme.surfaceContainerHighest
                                        else -> color.copy(alpha = 0.3f + 0.7f * f)
                                    },
                                )
                                .then(if (date == selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(8.dp)) else Modifier)
                                .clickable { onSelect(date) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                date.dayOfMonth.toString(),
                                color = if (f != null && f > 0.5f) OnPastel else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (date == today) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundAction(icon: Int, description: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (enabled) 1f else 0.4f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(painterResource(icon), description, Modifier.size(28.dp)) }
}

@Composable
private fun ActionTile(icon: Int, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painterResource(icon), null)
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuitPanel(status: QuitStatus, colorKey: String, onRelapse: () -> Unit) {
    val color = HabitColor.fromKey(colorKey).color
    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.quit_time_free), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(elapsedText(status.elapsed), fontSize = 40.sp, fontWeight = FontWeight.Bold)
        val days = status.elapsed.toDays()
        Text(
            stringResource(
                when {
                    days < 1 -> R.string.quit_msg_start
                    days < 7 -> R.string.quit_msg_days
                    days < 30 -> R.string.quit_msg_weeks
                    else -> R.string.quit_msg_months
                },
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        status.nextMilestoneDays?.let { next ->
            val progress = (status.elapsed.toMinutes() / (next * 1440f)).coerceIn(0f, 1f)
            Text(stringResource(R.string.quit_next_milestone) + ": " + stringResource(R.string.quit_days_value, next), style = MaterialTheme.typography.bodyMedium)
            Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).height(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                Box(Modifier.fillMaxWidth(progress).height(10.dp).clip(CircleShape).background(color))
            }
        }
        val reached = QuitCalculator.reachedMilestones(status)
        if (reached.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                reached.forEach { d ->
                    Text(
                        "🏅 " + stringResource(R.string.quit_days_value, d),
                        color = OnPastel,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(color).padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
        OutlinedButton(onClick = onRelapse, modifier = Modifier.padding(top = 12.dp)) { Text(stringResource(R.string.quit_record_relapse)) }
    }
}

// ---------------------------------------------------------------------------
// Stats
// ---------------------------------------------------------------------------

private enum class StatsRange { ALL, YEAR, MONTH, WEEK }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatsTab(s: HabitDetailUiState.Ready) {
    val habit = s.habit
    val locale = currentLocale()
    val dateFormatter = rememberDateFormatter()
    var range by rememberSaveable { mutableStateOf(StatsRange.MONTH) }
    var showAllStreaks by rememberSaveable { mutableStateOf(false) }
    val color = HabitColor.fromKey(habit.colorKey).color
    fun value(date: LocalDate): Float {
        val p = s.progress[date] ?: return 0f
        return if (habit.type == HabitType.QUIT) p.total.toFloat() else p.total.toFloat()
    }
    val (from, bars) = when (range) {
        StatsRange.WEEK -> {
            val start = s.today.with(TemporalAdjusters.previousOrSame(s.firstDayOfWeek))
            start to (0L until 7L).map { start.plusDays(it) }.map { BarEntry(shortDayName(it, locale).take(3), value(it)) }
        }
        StatsRange.MONTH -> {
            val start = s.today.withDayOfMonth(1)
            start to (0 until s.today.lengthOfMonth()).map { start.plusDays(it.toLong()) }
                .map { BarEntry(if (it.dayOfMonth % 5 == 1) it.dayOfMonth.toString() else "", value(it)) }
        }
        StatsRange.YEAR -> {
            val start = s.today.withDayOfYear(1)
            start to (1..12).map { m ->
                val ym = YearMonth.of(s.today.year, m)
                BarEntry(
                    ym.month.getDisplayName(java.time.format.TextStyle.NARROW, locale),
                    (1..ym.lengthOfMonth()).sumOf { value(ym.atDay(it)).toDouble() }.toFloat(),
                )
            }
        }
        StatsRange.ALL -> {
            val startYm = YearMonth.from(habit.startDate)
            val months = generateSequence(startYm) { it.plusMonths(1) }.takeWhile { !it.isAfter(YearMonth.from(s.today)) }.toList().takeLast(24)
            habit.startDate to months.map { ym ->
                BarEntry(
                    ym.month.getDisplayName(java.time.format.TextStyle.NARROW, locale),
                    (1..ym.lengthOfMonth()).sumOf { value(ym.atDay(it)).toDouble() }.toFloat(),
                )
            }
        }
    }
    val inRange = s.progress.values.filter { !it.date.isBefore(from) && !it.date.isAfter(s.today) && it.entryCount > 0 }
    val days = (s.today.toEpochDay() - maxOf(from, habit.startDate).toEpochDay() + 1).coerceAtLeast(1)

    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
        item(key = "ranges") {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatsRange.entries.forEach { r ->
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                            .background(if (range == r) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable { range = r }.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(
                                when (r) {
                                    StatsRange.ALL -> R.string.range_all
                                    StatsRange.YEAR -> R.string.period_year
                                    StatsRange.MONTH -> R.string.period_month
                                    StatsRange.WEEK -> R.string.period_week
                                },
                            ),
                            color = if (range == r) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
        item(key = "chart") {
            BarChart(
                entries = bars,
                color = color,
                targetLine = if (range == StatsRange.WEEK || range == StatsRange.MONTH) habit.dailyTarget.toFloat().takeIf { habit.goalPeriod == GoalPeriod.DAY && habit.type != HabitType.QUIT } else null,
                modifier = Modifier.padding(horizontal = 16.dp),
                chartHeight = 170.dp,
                description = stringResource(R.string.detail_stats),
            )
        }
        item(key = "cards") {
            FlowRow(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 2,
            ) {
                val unitKey = habitUnitKey(habit)
                StatCard(stringResource(R.string.stat_total), "${formatAmount(inRange.sumOf { it.total })} ${unitLabel(unitKey, 2.0)}", Modifier.weight(1f))
                StatCard(stringResource(R.string.stat_daily_average), "${formatAmount(inRange.sumOf { it.total } / days)} ${unitLabel(unitKey, 2.0)}", Modifier.weight(1f))
                if (habit.type != HabitType.QUIT) {
                    StatCard(stringResource(R.string.stat_current_streak), streakText(s.stats.streak), Modifier.weight(1f))
                    StatCard(stringResource(R.string.stat_longest_streak), streakText(s.stats.streak, s.stats.streak.longest), Modifier.weight(1f))
                    StatCard(stringResource(R.string.stat_completion_rate), "${(s.stats.completionRate * 100).toInt()}%", Modifier.weight(1f))
                    StatCard(stringResource(R.string.stat_total_completions), s.stats.totalCompletions.toString(), Modifier.weight(1f))
                    StatCard(stringResource(R.string.stat_best_day), habitValueText(habit, s.stats.bestDayValue), Modifier.weight(1f))
                    StatCard(stringResource(R.string.stat_activities), s.stats.activityCount.toString(), Modifier.weight(1f))
                } else if (s.quit != null) {
                    StatCard(stringResource(R.string.quit_longest), elapsedText(s.quit.longest), Modifier.weight(1f))
                    StatCard(stringResource(R.string.quit_resets), s.quit.relapseCount.toString(), Modifier.weight(1f))
                }
            }
        }
        if (habit.type != HabitType.QUIT) {
            item(key = "recordsTitle") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.habit_records), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (s.streakRuns.size > 3) {
                        TextButton(onClick = { showAllStreaks = !showAllStreaks }) {
                            Text(stringResource(if (showAllStreaks) R.string.show_less else R.string.view_all_streaks))
                        }
                    }
                }
            }
            if (s.streakRuns.isEmpty()) {
                item(key = "noRecords") {
                    Text(stringResource(R.string.habit_no_records), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
            items(if (showAllStreaks) s.streakRuns else s.streakRuns.take(3), key = { "${it.start}_${it.end}" }) { run ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("🔥 " + streakText(s.stats.streak, run.length), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("${run.start.format(dateFormatter)} – ${run.end.format(dateFormatter)}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(14.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

// ---------------------------------------------------------------------------
// Activity log
// ---------------------------------------------------------------------------

@Composable
private fun LogTab(s: HabitDetailUiState.Ready, viewModel: HabitDetailViewModel, navigation: HabitDetailNavigation) {
    val habit = s.habit
    val context = LocalContext.current
    val dateFormatter = rememberDateFormatter()
    var limit by rememberSaveable { mutableIntStateOf(60) }
    var addEntry by remember { mutableStateOf(false) }
    val exportTitle = stringResource(R.string.log_export)
    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
        item(key = "buttons") {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (habit.type != HabitType.QUIT) {
                    BigButton(stringResource(R.string.habit_log_entry), R.drawable.ic_add, primary = true, onClick = { addEntry = true }, modifier = Modifier.weight(1f))
                }
                BigButton(exportTitle, R.drawable.ic_share, primary = false, modifier = Modifier.weight(1f), onClick = {
                    val csv = buildString {
                        appendLine("date,time,value,note")
                        s.activities.forEach { a ->
                            val time = a.loggedAt.atZone(java.time.ZoneId.systemDefault()).toLocalTime().withNano(0)
                            appendLine("${a.date},$time,${if (a.skipped) "skipped" else formatAmount(a.value)},\"${a.note.orEmpty().replace("\"", "\"\"")}\"")
                        }
                    }
                    val send = Intent(Intent.ACTION_SEND).setType("text/csv")
                        .putExtra(Intent.EXTRA_SUBJECT, "${habit.name}.csv")
                        .putExtra(Intent.EXTRA_TEXT, csv)
                    context.startActivity(Intent.createChooser(send, exportTitle))
                })
            }
        }
        if (s.activities.isEmpty()) {
            item(key = "empty") {
                Text(stringResource(R.string.habit_history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            }
        }
        val grouped = s.activities.take(limit).groupBy { it.date }
        grouped.forEach { (date, entries) ->
            item(key = "date_$date") {
                Text(
                    date.format(dateFormatter),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                )
            }
            items(entries, key = { it.id }) { a -> ActivityRow(a, habit.type, habit, viewModel) }
        }
        if (s.activities.size > limit) {
            item(key = "more") { TextButton(onClick = { limit += 100 }, modifier = Modifier.padding(12.dp)) { Text(stringResource(R.string.show_more)) } }
        }
        if (s.notes.isNotEmpty()) {
            item(key = "notesTitle") {
                Text(stringResource(R.string.habit_notes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
            }
            items(s.notes, key = { "note_${it.id}" }) { note ->
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow).clickable { navigation.onOpenNote(note.id) }.padding(14.dp),
                ) {
                    Text(note.date.format(dateFormatter), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (note.title.isNotBlank()) Text(note.title, fontWeight = FontWeight.Bold)
                    Text(RichText.plain(note.content), maxLines = 3)
                }
            }
        }
    }
    if (addEntry) {
        EntryDialog(
            habitType = habit.type,
            unit = unitLabel(habitUnitKey(habit), 2.0),
            initialDate = s.today,
            initialValue = if (habit.type == HabitType.BOOLEAN) 1.0 else habit.step,
            title = stringResource(R.string.habit_log_entry),
            onConfirm = { date, value, note ->
                if (habit.type == HabitType.BOOLEAN) viewModel.completeDay(date) else viewModel.addEntry(date, value, note)
            },
            onDismiss = { addEntry = false },
        )
    }
}

@Composable
private fun ActivityRow(activity: HabitActivity, type: HabitType, habit: com.example.habitapp.domain.model.Habit, viewModel: HabitDetailViewModel) {
    val timeFormatter = rememberTimeFormatter()
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp).clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(enabled = !activity.skipped && type != HabitType.QUIT) { editing = true }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                when {
                    activity.skipped -> stringResource(R.string.habit_skipped)
                    type == HabitType.QUIT -> stringResource(R.string.quit_relapse)
                    else -> (if (activity.value > 0) "+" else "") + "${formatAmount(activity.value)} ${unitLabel(habitUnitKey(habit), activity.value)}"
                },
                fontWeight = FontWeight.Bold,
            )
            Text(
                activity.loggedAt.atZone(java.time.ZoneId.systemDefault()).toLocalTime().format(timeFormatter) + (activity.note?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
        Box(
            Modifier.size(36.dp).clip(CircleShape).clickable { deleting = true },
            contentAlignment = Alignment.Center,
        ) { Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.action_delete), Modifier.size(20.dp)) }
    }
    if (editing) {
        EntryDialog(
            habitType = type,
            unit = unitLabel(habitUnitKey(habit), 2.0),
            initialDate = activity.date,
            initialValue = activity.value,
            initialNote = activity.note.orEmpty(),
            title = stringResource(R.string.habit_edit_entry),
            onConfirm = { date, value, note -> viewModel.updateEntry(activity, date, value, note) },
            onDismiss = { editing = false },
        )
    }
    if (deleting) {
        ConfirmDialog(
            title = stringResource(R.string.entry_delete_title),
            message = stringResource(R.string.entry_delete_message),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { viewModel.deleteEntry(activity) },
            onDismiss = { deleting = false },
        )
    }
}

@Composable
private fun RelapseDialog(onConfirm: (String?) -> Unit, onDismiss: () -> Unit) {
    var note by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quit_record_relapse)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.quit_relapse_message))
                OutlinedTextField(value = note, onValueChange = { note = it.take(500) }, label = { Text(stringResource(R.string.note_optional)) })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(note.ifBlank { null })
                onDismiss()
            }) { Text(stringResource(R.string.quit_restart)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Logs or edits an entry for any date (past corrections included). */
@Composable
fun EntryDialog(
    habitType: HabitType,
    unit: String,
    initialDate: LocalDate,
    initialValue: Double,
    title: String,
    onConfirm: (LocalDate, Double, String?) -> Unit,
    onDismiss: () -> Unit,
    initialNote: String = "",
) {
    val dateFormatter = rememberDateFormatter()
    var date by remember { mutableStateOf(initialDate) }
    var value by rememberSaveable { mutableStateOf(formatAmount(initialValue)) }
    var note by rememberSaveable { mutableStateOf(initialNote) }
    var pickDate by remember { mutableStateOf(false) }
    val parsed = value.replace(',', '.').trim().toDoubleOrNull()
    val needsValue = habitType != HabitType.BOOLEAN
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickDate = true }) { Text(date.format(dateFormatter)) }
                if (needsValue) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it.take(10) },
                        label = { Text(stringResource(R.string.amount_label, unit)) },
                        singleLine = true,
                        isError = parsed == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.testTag("amount_field"),
                    )
                }
                OutlinedTextField(value = note, onValueChange = { note = it.take(500) }, label = { Text(stringResource(R.string.note_optional)) })
            }
        },
        confirmButton = {
            TextButton(
                enabled = !needsValue || parsed != null,
                onClick = {
                    onConfirm(date, if (needsValue) parsed ?: 0.0 else initialValue, note.ifBlank { null })
                    onDismiss()
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
    if (pickDate) {
        LocalDatePickerDialog(date, { date = it }, { pickDate = false })
    }
}
