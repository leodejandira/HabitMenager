package com.example.habitapp.presentation.habits

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.habitapp.R
import com.example.habitapp.domain.model.HabitActivity
import com.example.habitapp.domain.model.HabitType
import com.example.habitapp.domain.util.QuitCalculator
import com.example.habitapp.domain.util.QuitStatus
import com.example.habitapp.presentation.components.BarChart
import com.example.habitapp.presentation.components.BarEntry
import com.example.habitapp.presentation.components.CalendarHeatmap
import com.example.habitapp.presentation.components.CheckButton
import com.example.habitapp.presentation.components.ConfirmDialog
import com.example.habitapp.presentation.components.EmojiBadge
import com.example.habitapp.presentation.components.FullScreenError
import com.example.habitapp.presentation.components.FullScreenLoading
import com.example.habitapp.presentation.components.LocalDatePickerDialog
import com.example.habitapp.presentation.components.RichText
import com.example.habitapp.presentation.components.SectionHeader
import com.example.habitapp.presentation.components.StatTile
import com.example.habitapp.presentation.components.currentLocale
import com.example.habitapp.presentation.components.elapsedText
import com.example.habitapp.presentation.components.formatAmount
import com.example.habitapp.presentation.components.habitProgressText
import com.example.habitapp.presentation.components.habitValueText
import com.example.habitapp.presentation.components.rememberDateFormatter
import com.example.habitapp.presentation.components.rememberTimeFormatter
import com.example.habitapp.presentation.components.shortDayName
import com.example.habitapp.presentation.components.streakText
import com.example.habitapp.presentation.theme.HabitColor
import com.example.habitapp.presentation.theme.HabitTheme
import java.time.LocalDate
import java.time.LocalTime

class HabitDetailNavigation(
    val onBack: () -> Unit,
    val onEdit: (String) -> Unit,
    val onOpenHabit: (String) -> Unit,
    val onTimer: (String) -> Unit,
    val onReport: (String) -> Unit,
    val onNewNote: (habitId: String) -> Unit,
    val onOpenNote: (entryId: String) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitDetailScreen(navigation: HabitDetailNavigation, viewModel: HabitDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ready?.habit?.name.orEmpty(), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = navigation.onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
                    }
                },
                actions = {
                    if (ready != null) {
                        IconButton(onClick = { navigation.onEdit(ready.habit.id) }) {
                            Icon(painterResource(R.drawable.ic_edit), stringResource(R.string.action_edit))
                        }
                        Box {
                            IconButton(onClick = { menu = true }) {
                                Icon(painterResource(R.drawable.ic_more), stringResource(R.string.more_options))
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.habit_report)) },
                                    onClick = { menu = false; navigation.onReport(ready.habit.id) },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_duplicate)) },
                                    onClick = { menu = false; viewModel.duplicate() },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(if (ready.habit.archived) R.string.action_restore else R.string.action_archive)) },
                                    onClick = { menu = false; viewModel.setArchived(!ready.habit.archived) },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_delete)) },
                                    onClick = { menu = false; confirmDelete = true },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        when (val s = state) {
            HabitDetailUiState.Loading -> FullScreenLoading(Modifier.padding(padding))
            HabitDetailUiState.NotFound -> FullScreenError(stringResource(R.string.error_not_found), Modifier.padding(padding))
            HabitDetailUiState.Error -> FullScreenError(stringResource(R.string.error_loading), Modifier.padding(padding))
            is HabitDetailUiState.Ready -> HabitDetailContent(s, padding, viewModel, navigation)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HabitDetailContent(
    state: HabitDetailUiState.Ready,
    padding: PaddingValues,
    viewModel: HabitDetailViewModel,
    navigation: HabitDetailNavigation,
) {
    val spacing = HabitTheme.spacing
    val habit = state.habit
    val accent = HabitColor.fromKey(habit.colorKey).resolve()
    val dateFormatter = rememberDateFormatter()
    val timeFormatter = rememberTimeFormatter()
    val locale = currentLocale()
    var addEntry by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<HabitActivity?>(null) }
    var deleting by remember { mutableStateOf<HabitActivity?>(null) }
    var relapse by remember { mutableStateOf(false) }
    var historyLimit by rememberSaveable { mutableIntStateOf(30) }

    LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 32.dp)) {
        item(key = "header") {
            Row(Modifier.padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                EmojiBadge(habit.emoji, accent, 64)
                Spacer(Modifier.width(spacing.lg))
                Column(Modifier.weight(1f)) {
                    Text(habit.name, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        listOf(habitTypeLabel(habit.type), frequencyLabel(habit.frequency.type)).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (habit.archived) {
                        Text(stringResource(R.string.habit_archived_label), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            if (habit.description.isNotBlank()) {
                Text(
                    habit.description,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = spacing.screenHorizontal, vertical = spacing.xs),
                )
            }
        }

        if (state.quit != null) {
            item(key = "quit") { QuitCard(state.quit, accent, onRelapse = { relapse = true }) }
        } else {
            item(key = "today") {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm),
                    colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.12f)),
                ) {
                    Row(Modifier.padding(spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.date_today), style = MaterialTheme.typography.labelLarge)
                            Text(
                                if (habit.type == HabitType.BOOLEAN) {
                                    stringResource(if (state.todayProgress.completed) R.string.habit_done else R.string.habit_not_done)
                                } else {
                                    habitProgressText(habit, state.todayProgress.total, state.todayProgress.target)
                                },
                                style = MaterialTheme.typography.titleLarge,
                            )
                        }
                        if (habit.type == HabitType.BOOLEAN) {
                            CheckButton(state.todayProgress.completed, accent, viewModel::toggleToday, habit.name, size = 48)
                        } else {
                            if (habit.type == HabitType.DURATION) {
                                OutlinedButton(onClick = { navigation.onTimer(habit.id) }) { Text(stringResource(R.string.timer_title)) }
                                Spacer(Modifier.width(spacing.sm))
                            }
                            Button(onClick = { viewModel.addEntry(state.today, habit.step, null) }) {
                                Text("+" + habitValueText(habit, habit.step))
                            }
                        }
                    }
                    FlowRow(
                        Modifier.padding(start = spacing.lg, end = spacing.lg, bottom = spacing.md),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        AssistChip(onClick = { addEntry = true }, label = { Text(stringResource(R.string.habit_log_entry)) })
                        if (habit.type != HabitType.BOOLEAN && !state.todayProgress.completed) {
                            AssistChip(onClick = { viewModel.completeDay(state.today) }, label = { Text(stringResource(R.string.habit_complete_day)) })
                        }
                        if (!state.todayProgress.completed && !state.todayProgress.skipped) {
                            AssistChip(onClick = { viewModel.skipDay(state.today) }, label = { Text(stringResource(R.string.habit_skip_today)) })
                        }
                        if (state.todayProgress.entryCount > 0 || state.todayProgress.skipped) {
                            AssistChip(onClick = { viewModel.clearDay(state.today) }, label = { Text(stringResource(R.string.habit_clear_today)) })
                        }
                    }
                }
            }
            item(key = "stats") {
                val stats = state.stats
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    FlowRow(Modifier.padding(spacing.lg), horizontalArrangement = Arrangement.spacedBy(spacing.xl), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                        StatTile(streakText(stats.streak), stringResource(R.string.stat_current_streak))
                        StatTile(streakText(stats.streak, stats.streak.longest), stringResource(R.string.stat_longest_streak))
                        StatTile("${(stats.completionRate * 100).toInt()}%", stringResource(R.string.stat_completion_rate))
                        StatTile(stats.totalCompletions.toString(), stringResource(R.string.stat_total_completions))
                        if (habit.type.isMeasurable) {
                            StatTile(habitValueText(habit, stats.totalValue), stringResource(R.string.stat_total))
                            StatTile(habitValueText(habit, stats.averageValue), stringResource(R.string.stat_average))
                            StatTile(habitValueText(habit, stats.bestDayValue), stringResource(R.string.stat_best_day))
                        }
                    }
                }
            }
            item(key = "chart") {
                SectionHeader(stringResource(R.string.chart_last_14_days))
                val entries = (13 downTo 0).map { offset ->
                    val date = state.today.minusDays(offset.toLong())
                    val p = state.progress[date]
                    val value = if (habit.type.isMeasurable) (p?.total ?: 0.0).toFloat() else if (p?.completed == true) 1f else 0f
                    BarEntry(shortDayName(date, locale).take(2), value)
                }
                BarChart(
                    entries = entries,
                    color = accent,
                    targetLine = if (habit.type.isMeasurable) habit.target.toFloat() else null,
                    maxValue = if (habit.type.isMeasurable) null else 1f,
                    modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                    description = stringResource(R.string.chart_last_14_days),
                )
            }
            item(key = "heatmap") {
                SectionHeader(stringResource(R.string.chart_heatmap))
                CalendarHeatmap(
                    endDate = state.today,
                    weeks = 18,
                    firstDayOfWeek = state.firstDayOfWeek,
                    color = accent,
                    intensity = { date ->
                        if (date.isBefore(habit.startDate)) null else state.progress[date]?.fraction ?: 0f
                    },
                    modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                    description = stringResource(R.string.chart_heatmap),
                )
            }
        }

        if (state.reminders.isNotEmpty()) {
            item(key = "reminders") {
                SectionHeader(stringResource(R.string.habit_reminders))
                Text(
                    state.reminders.joinToString("  ·  ") { LocalTime.of(it.minuteOfDay / 60, it.minuteOfDay % 60).format(timeFormatter) },
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                )
            }
        }

        item(key = "notesHeader") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SectionHeader(stringResource(R.string.habit_notes)) }
                TextButton(onClick = { navigation.onNewNote(habit.id) }, modifier = Modifier.padding(end = spacing.sm)) {
                    Text(stringResource(R.string.habit_add_note))
                }
            }
        }
        items(state.notes, key = { "note_" + it.id }) { note ->
            ListItem(
                headlineContent = { Text(note.title.ifBlank { note.date.format(dateFormatter) }, maxLines = 1) },
                supportingContent = { Text(RichText.plain(note.content), maxLines = 2) },
                overlineContent = { Text(note.date.format(dateFormatter)) },
                modifier = Modifier.clickable { navigation.onOpenNote(note.id) },
            )
        }

        item(key = "historyHeader") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    SectionHeader(stringResource(if (habit.type == HabitType.QUIT) R.string.quit_history else R.string.habit_history))
                }
                if (habit.type != HabitType.QUIT) {
                    TextButton(onClick = { addEntry = true }, modifier = Modifier.padding(end = spacing.sm)) {
                        Text(stringResource(R.string.habit_log_entry))
                    }
                }
            }
        }
        if (state.activities.isEmpty()) {
            item(key = "historyEmpty") {
                Text(
                    stringResource(R.string.habit_history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                )
            }
        }
        items(state.activities.take(historyLimit), key = { it.id }) { activity ->
            ListItem(
                headlineContent = {
                    Text(
                        when {
                            activity.skipped -> stringResource(R.string.habit_skipped)
                            habit.type == HabitType.QUIT -> stringResource(R.string.quit_relapse)
                            habit.type == HabitType.BOOLEAN -> stringResource(if (activity.value > 0) R.string.habit_done else R.string.habit_undone)
                            else -> (if (activity.value > 0) "+" else "") + habitValueText(habit, activity.value)
                        },
                    )
                },
                overlineContent = {
                    Text(
                        activity.date.format(dateFormatter) + " · " +
                            activity.loggedAt.atZone(java.time.ZoneId.systemDefault()).toLocalTime().format(timeFormatter),
                    )
                },
                supportingContent = activity.note?.let { { Text(it) } },
                trailingContent = {
                    Row {
                        if (!activity.skipped && habit.type != HabitType.QUIT) {
                            IconButton(onClick = { editing = activity }) {
                                Icon(painterResource(R.drawable.ic_edit), stringResource(R.string.action_edit))
                            }
                        }
                        IconButton(onClick = { deleting = activity }) {
                            Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.action_delete))
                        }
                    }
                },
            )
        }
        if (state.activities.size > historyLimit) {
            item(key = "more") {
                TextButton(onClick = { historyLimit += 50 }, modifier = Modifier.padding(horizontal = spacing.md)) {
                    Text(stringResource(R.string.show_more))
                }
            }
        }
    }

    if (addEntry) {
        EntryDialog(
            habitType = habit.type,
            unit = if (habit.type == HabitType.DURATION) stringResource(R.string.unit_minutes) else habit.unit,
            initialDate = state.today,
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
            onDismiss = { addEntry = false },
        )
    }
    editing?.let { activity ->
        EntryDialog(
            habitType = habit.type,
            unit = if (habit.type == HabitType.DURATION) stringResource(R.string.unit_minutes) else habit.unit,
            initialDate = activity.date,
            initialValue = activity.value,
            initialNote = activity.note.orEmpty(),
            title = stringResource(R.string.habit_edit_entry),
            onConfirm = { date, value, note -> viewModel.updateEntry(activity, date, value, note) },
            onDismiss = { editing = null },
        )
    }
    deleting?.let { activity ->
        ConfirmDialog(
            title = stringResource(R.string.entry_delete_title),
            message = stringResource(R.string.entry_delete_message),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { viewModel.deleteEntry(activity) },
            onDismiss = { deleting = null },
        )
    }
    if (relapse) {
        RelapseDialog(onConfirm = { note -> viewModel.recordRelapse(viewModel.now(), note) }, onDismiss = { relapse = false })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuitCard(status: QuitStatus, accent: androidx.compose.ui.graphics.Color, onRelapse: () -> Unit) {
    val spacing = HabitTheme.spacing
    val days = status.elapsed.toDays()
    Card(
        Modifier.fillMaxWidth().padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.12f)),
    ) {
        Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Text(stringResource(R.string.quit_time_free), style = MaterialTheme.typography.labelLarge)
            Text(elapsedText(status.elapsed), style = MaterialTheme.typography.headlineMedium)
            Text(quitEncouragement(days), style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.xl)) {
                StatTile(elapsedText(status.longest), stringResource(R.string.quit_longest))
                StatTile(status.relapseCount.toString(), stringResource(R.string.quit_resets))
                status.nextMilestoneDays?.let {
                    StatTile(stringResource(R.string.quit_days_value, it), stringResource(R.string.quit_next_milestone))
                }
            }
            val reached = QuitCalculator.reachedMilestones(status)
            if (reached.isNotEmpty()) {
                Text(
                    stringResource(R.string.quit_milestones, reached.joinToString(" · ") { "🏅 $it" }),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedButton(onClick = onRelapse) { Text(stringResource(R.string.quit_record_relapse)) }
        }
    }
}

@Composable
private fun quitEncouragement(days: Long): String = stringResource(
    when {
        days < 1 -> R.string.quit_msg_start
        days < 7 -> R.string.quit_msg_days
        days < 30 -> R.string.quit_msg_weeks
        else -> R.string.quit_msg_months
    },
)

@Composable
private fun RelapseDialog(onConfirm: (String?) -> Unit, onDismiss: () -> Unit) {
    var note by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quit_record_relapse)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(HabitTheme.spacing.sm)) {
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
            Column(verticalArrangement = Arrangement.spacedBy(HabitTheme.spacing.sm)) {
                OutlinedButton(onClick = { pickDate = true }) { Text(date.format(dateFormatter)) }
                if (needsValue) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it.take(10) },
                        label = { Text(stringResource(R.string.amount_label, unit)) },
                        singleLine = true,
                        isError = parsed == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
