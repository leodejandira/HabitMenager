package com.example.habitapp.presentation.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.habitapp.R
import com.example.habitapp.domain.model.TaskOccurrence
import com.example.habitapp.domain.usecase.DayOverview
import com.example.habitapp.domain.usecase.HabitDayItem
import com.example.habitapp.presentation.components.FullScreenError
import com.example.habitapp.presentation.components.FullScreenLoading
import com.example.habitapp.presentation.components.HabitCard
import com.example.habitapp.presentation.components.HabitCardActions
import com.example.habitapp.presentation.components.RichText
import com.example.habitapp.presentation.components.SectionHeader
import com.example.habitapp.presentation.components.TaskRow
import com.example.habitapp.presentation.components.currentLocale
import com.example.habitapp.presentation.components.narrowDayName
import com.example.habitapp.presentation.components.priorityColor
import com.example.habitapp.presentation.components.rememberDateFormatter
import com.example.habitapp.presentation.habits.AmountDialog
import com.example.habitapp.presentation.theme.HabitColor
import com.example.habitapp.presentation.theme.HabitTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle

class CalendarNavigation(
    val onOpenHabit: (String) -> Unit,
    val onTimer: (String) -> Unit,
    val onOpenTask: (String, LocalDate?) -> Unit,
    val onNewTask: (LocalDate) -> Unit,
    val onOpenJournal: (String) -> Unit,
    val onNewJournal: (LocalDate) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(navigation: CalendarNavigation, viewModel: CalendarViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var amountFor by remember { mutableStateOf<HabitDayItem?>(null) }
    val locale = currentLocale()
    Scaffold(
        modifier = Modifier.testTag(CalendarTestTags.SCREEN),
        topBar = {
            TopAppBar(
                title = {
                    val ready = state as? CalendarUiState.Ready
                    val title = ready?.let {
                        when (it.mode) {
                            CalendarMode.MONTH -> it.selected.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)).replaceFirstChar { c -> c.titlecase(locale) }
                            CalendarMode.WEEK -> stringResource(
                                R.string.calendar_week_of,
                                it.gridDays.first().date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)),
                            )
                            CalendarMode.DAY -> it.selected.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))
                        }
                    } ?: stringResource(R.string.nav_calendar)
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                actions = {
                    IconButton(onClick = { viewModel.shift(-1) }) { Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.calendar_previous)) }
                    IconButton(onClick = viewModel::goToToday) { Icon(painterResource(R.drawable.ic_today_jump), stringResource(R.string.calendar_jump_today)) }
                    IconButton(onClick = { viewModel.shift(1) }) { Icon(painterResource(R.drawable.ic_chevron_right), stringResource(R.string.calendar_next)) }
                },
            )
        },
    ) { padding ->
        when (val s = state) {
            CalendarUiState.Loading -> FullScreenLoading(Modifier.padding(padding))
            CalendarUiState.Error -> FullScreenError(stringResource(R.string.error_loading), Modifier.padding(padding))
            is CalendarUiState.Ready -> {
                val actions = remember(viewModel, navigation) {
                    HabitCardActions(
                        onOpen = navigation.onOpenHabit,
                        onToggle = viewModel::toggleHabit,
                        onIncrement = { id, amount -> viewModel.addProgress(id, amount, null) },
                        onCustomAmount = { amountFor = it },
                        onTimer = navigation.onTimer,
                    )
                }
                LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 32.dp)) {
                    item(key = "mode") {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = HabitTheme.spacing.screenHorizontal, vertical = HabitTheme.spacing.sm)) {
                            CalendarMode.entries.forEachIndexed { index, m ->
                                SegmentedButton(
                                    selected = s.mode == m,
                                    onClick = { viewModel.setMode(m) },
                                    shape = SegmentedButtonDefaults.itemShape(index, CalendarMode.entries.size),
                                ) {
                                    Text(
                                        stringResource(
                                            when (m) {
                                                CalendarMode.MONTH -> R.string.calendar_month
                                                CalendarMode.WEEK -> R.string.calendar_week
                                                CalendarMode.DAY -> R.string.calendar_day
                                            },
                                        ),
                                    )
                                }
                            }
                        }
                    }
                    when (s.mode) {
                        CalendarMode.MONTH -> item(key = "grid") { MonthGrid(s, onSelect = viewModel::select) }
                        CalendarMode.WEEK -> item(key = "week") {
                            WeekBoard(
                                state = s,
                                onSelect = viewModel::select,
                                onMoveTask = viewModel::moveTask,
                                onOpenTask = { navigation.onOpenTask(it.task.id, it.date) },
                            )
                        }
                        CalendarMode.DAY -> Unit
                    }
                    dayDetails(s, actions, viewModel, navigation)
                }
            }
        }
    }
    amountFor?.let { item ->
        AmountDialog(item.habit, { amount, note -> viewModel.addProgress(item.habit.id, amount, note) }, { amountFor = null })
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.dayDetails(
    s: CalendarUiState.Ready,
    actions: HabitCardActions,
    viewModel: CalendarViewModel,
    navigation: CalendarNavigation,
) {
    val overview = s.selectedOverview
    item(key = "dayHeader") { DayHeader(s.selected, s.today, overview) }
    if (overview.habits.isNotEmpty()) {
        item(key = "habitsHeader") { SectionHeader(stringResource(R.string.nav_habits)) }
        items(overview.habits, key = { "h_" + it.habit.id }) { item ->
            HabitCard(item, actions, Modifier.padding(horizontal = HabitTheme.spacing.screenHorizontal, vertical = HabitTheme.spacing.xs))
        }
    }
    item(key = "tasksHeader") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { SectionHeader(stringResource(R.string.nav_tasks)) }
            TextButton(onClick = { navigation.onNewTask(s.selected) }, modifier = Modifier.padding(end = HabitTheme.spacing.sm)) {
                Text(stringResource(R.string.task_new))
            }
        }
    }
    items(overview.tasks, key = { "t_" + it.key }) { occ ->
        TaskRow(occ, null, s.today, viewModel::toggleTask, { navigation.onOpenTask(it.task.id, it.date) }, showDate = false)
    }
    item(key = "journalHeader") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { SectionHeader(stringResource(R.string.nav_journal)) }
            TextButton(onClick = { navigation.onNewJournal(s.selected) }, modifier = Modifier.padding(end = HabitTheme.spacing.sm)) {
                Text(stringResource(R.string.journal_new))
            }
        }
    }
    items(s.journalForSelected, key = { "j_" + it.id }) { entry ->
        ListItem(
            headlineContent = { Text(entry.title.ifBlank { stringResource(R.string.journal_untitled) }) },
            supportingContent = { Text(RichText.plain(entry.content), maxLines = 2) },
            modifier = Modifier.clickable { navigation.onOpenJournal(entry.id) },
        )
    }
}

@Composable
private fun DayHeader(date: LocalDate, today: LocalDate, overview: DayOverview) {
    val formatter = rememberDateFormatter(FormatStyle.FULL)
    Column(Modifier.padding(horizontal = HabitTheme.spacing.screenHorizontal, vertical = HabitTheme.spacing.md)) {
        Text(date.format(formatter), style = MaterialTheme.typography.titleMedium)
        val percent = (overview.fraction * 100).toInt()
        Text(
            stringResource(R.string.calendar_day_summary, percent, overview.completedHabits, overview.totalHabits, overview.completedTasks, overview.totalTasks),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (date.isBefore(today)) {
            Text(stringResource(R.string.calendar_past_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MonthGrid(state: CalendarUiState.Ready, onSelect: (LocalDate) -> Unit) {
    val locale = currentLocale()
    val month = state.selected.month
    Column(Modifier.padding(horizontal = HabitTheme.spacing.md)) {
        Row(Modifier.fillMaxWidth()) {
            (0 until 7).forEach { i ->
                Text(
                    narrowDayName(state.firstDayOfWeek.plus(i.toLong()), locale),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        state.gridDays.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    DayCell(
                        summary = day,
                        inMonth = day.date.month == month,
                        isToday = day.date == state.today,
                        isSelected = day.date == state.selected,
                        onClick = { onSelect(day.date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(summary: DaySummary, inMonth: Boolean, isToday: Boolean, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val fraction = summary.habitFraction
    val description = stringResource(
        R.string.calendar_cell_cd,
        summary.date.dayOfMonth,
        ((fraction ?: 0f) * 100).toInt(),
        summary.openTaskCount,
    )
    Column(
        modifier = modifier
            .aspectRatio(0.82f)
            .padding(2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(
                    when {
                        fraction == null -> MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                        fraction >= 1f -> primary
                        fraction > 0f -> primary.copy(alpha = 0.18f + 0.5f * fraction)
                        else -> MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                )
                .then(if (isToday) Modifier.border(2.dp, MaterialTheme.colorScheme.tertiary, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                summary.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    fraction != null && fraction >= 1f -> MaterialTheme.colorScheme.onPrimary
                    !inMonth -> MaterialTheme.colorScheme.outline
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
        Row(Modifier.height(10.dp).padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            summary.completedColors.forEach { key ->
                Box(Modifier.size(5.dp).background(HabitColor.fromKey(key).resolve(), CircleShape))
            }
            if (summary.openTaskCount > 0) {
                Box(Modifier.size(5.dp).background(if (summary.hasOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary, RoundedCornerShape(1.dp)))
            }
            if (summary.hasJournal) {
                Box(Modifier.size(5.dp).background(MaterialTheme.colorScheme.tertiary, CircleShape))
            }
        }
    }
}

/**
 * Seven-day schedule. Long-press a task chip and drag it onto another day to reschedule it;
 * the change is written to the database when the finger is lifted.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekBoard(
    state: CalendarUiState.Ready,
    onSelect: (LocalDate) -> Unit,
    onMoveTask: (TaskOccurrence, LocalDate) -> Unit,
    onOpenTask: (TaskOccurrence) -> Unit,
) {
    val locale = currentLocale()
    val rowBounds = remember { mutableStateMapOf<LocalDate, Rect>() }
    var dragging by remember { mutableStateOf<TaskOccurrence?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    val target = if (dragging != null) rowBounds.entries.firstOrNull { it.value.contains(pointer) }?.key else null

    Column(Modifier.padding(horizontal = HabitTheme.spacing.screenHorizontal)) {
        Text(
            stringResource(R.string.calendar_drag_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = HabitTheme.spacing.sm),
        )
        state.weekOverviews.forEach { overview ->
            val date = overview.date
            val highlighted = target == date && dragging?.date != date
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .onGloballyPositioned { rowBounds[date] = it.boundsInRoot() }
                    .then(if (highlighted) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)) else Modifier),
                colors = CardDefaults.cardColors(
                    containerColor = if (date == state.selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                onClick = { onSelect(date) },
            ) {
                Row(Modifier.padding(HabitTheme.spacing.md), verticalAlignment = Alignment.Top) {
                    Column(Modifier.width(52.dp)) {
                        Text(date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale), style = MaterialTheme.typography.labelMedium)
                        Text(
                            date.dayOfMonth.toString(),
                            style = MaterialTheme.typography.titleLarge,
                            color = if (date == state.today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        if (overview.totalHabits > 0) {
                            LinearProgressIndicator(
                                progress = { overview.completedHabits.toFloat() / overview.totalHabits },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            )
                            Text(
                                overview.habits.joinToString(" ") { (if (it.progress.completed) "✓" else "·") + it.habit.emoji },
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            overview.tasks.forEach { occ ->
                                val isDragged = dragging?.key == occ.key
                                var chipOrigin by remember(occ.key) { mutableStateOf(Offset.Zero) }
                                AssistChip(
                                    onClick = { onOpenTask(occ) },
                                    label = {
                                        Text(
                                            (if (occ.completed) "✓ " else "") + occ.task.title,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    leadingIcon = {
                                        Box(Modifier.size(8.dp).background(priorityColor(occ.task.priority).takeIf { occ.task.priority.level > 0 } ?: MaterialTheme.colorScheme.outline, CircleShape))
                                    },
                                    modifier = Modifier
                                        .onGloballyPositioned { chipOrigin = it.positionInRoot() }
                                        .zIndex(if (isDragged) 10f else 0f)
                                        .graphicsLayer {
                                            if (isDragged) {
                                                translationX = dragOffset.x
                                                translationY = dragOffset.y
                                                shadowElevation = 12f
                                            }
                                        }
                                        .pointerInput(occ.key) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = { start ->
                                                    dragging = occ
                                                    dragOffset = Offset.Zero
                                                    pointer = chipOrigin + start
                                                },
                                                onDrag = { change, amount ->
                                                    change.consume()
                                                    dragOffset += amount
                                                    pointer += amount
                                                },
                                                onDragEnd = {
                                                    val dropDate = rowBounds.entries.firstOrNull { it.value.contains(pointer) }?.key
                                                    val moved = dragging
                                                    if (moved != null && dropDate != null && dropDate != moved.date) onMoveTask(moved, dropDate)
                                                    dragging = null
                                                    dragOffset = Offset.Zero
                                                },
                                                onDragCancel = {
                                                    dragging = null
                                                    dragOffset = Offset.Zero
                                                },
                                            )
                                        },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

object CalendarTestTags {
    const val SCREEN = "screen_calendar"
}
