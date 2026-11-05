package com.example.habitapp.presentation.dashboard

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.habitapp.R
import com.example.habitapp.domain.usecase.HabitDayItem
import com.example.habitapp.domain.util.DayPeriod
import com.example.habitapp.presentation.components.EmojiBadge
import com.example.habitapp.presentation.components.EmptyState
import com.example.habitapp.presentation.components.FullScreenError
import com.example.habitapp.presentation.components.FullScreenLoading
import com.example.habitapp.presentation.components.HabitCard
import com.example.habitapp.presentation.components.HabitCardActions
import com.example.habitapp.presentation.components.ProgressRing
import com.example.habitapp.presentation.components.SectionHeader
import com.example.habitapp.presentation.components.TaskRow
import com.example.habitapp.presentation.components.currentLocale
import com.example.habitapp.presentation.components.relativeDateText
import com.example.habitapp.presentation.habits.AmountDialog
import com.example.habitapp.presentation.theme.HabitColor
import com.example.habitapp.presentation.theme.HabitTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle

class DashboardNavigation(
    val onOpenSettings: () -> Unit,
    val onOpenHabit: (String) -> Unit,
    val onNewHabit: () -> Unit,
    val onSuggestions: () -> Unit,
    val onNewTask: (LocalDate) -> Unit,
    val onOpenTask: (taskId: String, occurrence: LocalDate?) -> Unit,
    val onTimer: (String) -> Unit,
    val onRoutine: (String) -> Unit,
    val onNewJournalEntry: (LocalDate) -> Unit,
    val onStatistics: () -> Unit,
)

@Composable
fun DashboardScreen(
    navigation: DashboardNavigation,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var amountFor by remember { mutableStateOf<HabitDayItem?>(null) }
    var fabMenu by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.testTag(DashboardTestTags.SCREEN),
        floatingActionButton = {
            Box {
                FloatingActionButton(onClick = { fabMenu = true }, modifier = Modifier.testTag("dashboard_fab")) {
                    Icon(painterResource(R.drawable.ic_add), stringResource(R.string.action_add))
                }
                val date = (uiState as? DashboardUiState.Ready)?.overview?.date ?: LocalDate.now()
                DropdownMenu(expanded = fabMenu, onDismissRequest = { fabMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.habit_new)) },
                        onClick = { fabMenu = false; navigation.onNewHabit() },
                        modifier = Modifier.testTag("fab_new_habit"),
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.task_new)) },
                        onClick = { fabMenu = false; navigation.onNewTask(date) },
                        modifier = Modifier.testTag("fab_new_task"),
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.journal_new)) },
                        onClick = { fabMenu = false; navigation.onNewJournalEntry(date) },
                    )
                }
            }
        },
    ) { innerPadding ->
        when (val state = uiState) {
            DashboardUiState.Loading -> FullScreenLoading(Modifier.padding(innerPadding))
            DashboardUiState.Error -> FullScreenError(stringResource(R.string.error_loading), Modifier.padding(innerPadding))
            is DashboardUiState.Ready -> DashboardReady(
                state = state,
                contentPadding = innerPadding,
                navigation = navigation,
                viewModel = viewModel,
                onCustomAmount = { amountFor = it },
            )
        }
    }

    amountFor?.let { item ->
        AmountDialog(
            habit = item.habit,
            onConfirm = { amount, note -> viewModel.addProgress(item.habit.id, amount, note) },
            onDismiss = { amountFor = null },
        )
    }
}

@Composable
private fun DashboardReady(
    state: DashboardUiState.Ready,
    contentPadding: PaddingValues,
    navigation: DashboardNavigation,
    viewModel: DashboardViewModel,
    onCustomAmount: (HabitDayItem) -> Unit,
) {
    val spacing = HabitTheme.spacing
    val overview = state.overview
    val habitActions = remember(viewModel, navigation) {
        HabitCardActions(
            onOpen = navigation.onOpenHabit,
            onToggle = viewModel::toggleHabit,
            onIncrement = { id, amount -> viewModel.addProgress(id, amount) },
            onCustomAmount = onCustomAmount,
            onTimer = navigation.onTimer,
        )
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 88.dp,
        ),
    ) {
        item(key = "header") { DashboardHeaderRow(header = state.header, onOpenSettings = navigation.onOpenSettings) }
        item(key = "dateNav") {
            DateNavigator(
                date = overview.date,
                today = state.today,
                onPrevious = { viewModel.shiftDay(-1) },
                onNext = { viewModel.shiftDay(1) },
                onToday = viewModel::goToToday,
            )
        }
        if (!state.fullOverview.isEmpty) {
            item(key = "progress") { ProgressCard(state, onStatistics = navigation.onStatistics) }
        }
        if (state.routines.isNotEmpty()) {
            item(key = "routinesHeader") { SectionHeader(stringResource(R.string.routines_title)) }
            item(key = "routines") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = spacing.screenHorizontal),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    items(state.routines, key = { it.routine.id }) { progress ->
                        RoutineChipCard(progress, onClick = { navigation.onRoutine(progress.routine.id) })
                    }
                }
            }
        }
        if (overview.habits.isNotEmpty()) {
            item(key = "habitsHeader") { SectionHeader(stringResource(R.string.nav_habits)) }
            items(overview.habits, key = { "habit_" + it.habit.id }) { item ->
                HabitCard(
                    item = item,
                    actions = habitActions,
                    modifier = Modifier
                        .padding(horizontal = spacing.screenHorizontal, vertical = spacing.xs)
                        .animateItem(),
                )
            }
        }
        if (state.hiddenCompleted > 0) {
            item(key = "hiddenCompleted") {
                Text(
                    stringResource(R.string.dashboard_hidden_completed, state.hiddenCompleted),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = spacing.screenHorizontal, vertical = spacing.xs),
                )
            }
        }
        if (overview.overdueTasks.isNotEmpty()) {
            item(key = "overdueHeader") { SectionHeader(stringResource(R.string.tasks_overdue)) }
            items(overview.overdueTasks, key = { "overdue_" + it.key }) { occurrence ->
                TaskRow(
                    occurrence = occurrence,
                    listName = null,
                    today = state.today,
                    onToggle = viewModel::toggleTask,
                    onOpen = { navigation.onOpenTask(it.task.id, it.date) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
        if (overview.tasks.isNotEmpty()) {
            item(key = "tasksHeader") { SectionHeader(stringResource(R.string.nav_tasks)) }
            items(overview.tasks, key = { "task_" + it.key }) { occurrence ->
                TaskRow(
                    occurrence = occurrence,
                    listName = null,
                    today = state.today,
                    onToggle = viewModel::toggleTask,
                    onOpen = { navigation.onOpenTask(it.task.id, it.date) },
                    showDate = false,
                    modifier = Modifier.animateItem(),
                )
            }
        }
        if (state.fullOverview.isEmpty) {
            item(key = "empty") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.screenHorizontal, vertical = spacing.lg),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    EmptyState(
                        iconRes = R.drawable.ic_today,
                        title = stringResource(if (state.hasAnyHabit) R.string.dashboard_empty_title else R.string.dashboard_welcome_title),
                        message = stringResource(if (state.hasAnyHabit) R.string.dashboard_empty_message else R.string.dashboard_welcome_message),
                        action = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                                Button(onClick = navigation.onNewHabit, modifier = Modifier.testTag("empty_new_habit")) {
                                    Text(stringResource(R.string.habit_new))
                                }
                                OutlinedButton(onClick = navigation.onSuggestions) { Text(stringResource(R.string.suggestions_title)) }
                                OutlinedButton(onClick = { navigation.onNewTask(overview.date) }) { Text(stringResource(R.string.task_new)) }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressCard(state: DashboardUiState.Ready, onStatistics: () -> Unit) {
    val spacing = HabitTheme.spacing
    val overview = state.fullOverview
    val percent = (overview.fraction * 100).toInt()
    Card(
        onClick = onStatistics,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm)
            .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(Modifier.padding(spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(fraction = overview.fraction, color = MaterialTheme.colorScheme.primary) {
                Text("$percent%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(spacing.lg))
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(
                    stringResource(if (percent >= 100) R.string.progress_all_done else R.string.progress_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    stringResource(R.string.progress_habits, overview.completedHabits, overview.totalHabits),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    stringResource(R.string.progress_tasks, overview.completedTasks, overview.totalTasks),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun RoutineChipCard(progress: RoutineProgress, onClick: () -> Unit) {
    val spacing = HabitTheme.spacing
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(spacing.md).width(150.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EmojiBadge(progress.routine.emoji, HabitColor.INDIGO.resolve(), size = 32)
                Spacer(Modifier.width(spacing.sm))
                Text(progress.routine.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            }
            Text(
                stringResource(R.string.routine_steps_progress, progress.completedSteps, progress.totalSteps),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = spacing.sm),
            )
            LinearProgressIndicator(
                progress = { if (progress.totalSteps == 0) 0f else progress.completedSteps.toFloat() / progress.totalSteps },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = spacing.xs),
            )
        }
    }
}

@Composable
private fun DateNavigator(date: LocalDate, today: LocalDate, onPrevious: () -> Unit, onNext: () -> Unit, onToday: () -> Unit) {
    val spacing = HabitTheme.spacing
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(painterResource(R.drawable.ic_chevron_left), stringResource(R.string.previous_day))
        }
        Text(
            relativeDateText(date, today),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        if (date != today) {
            AssistChip(onClick = onToday, label = { Text(stringResource(R.string.date_today)) })
        }
        IconButton(onClick = onNext, enabled = date.isBefore(today)) {
            Icon(painterResource(R.drawable.ic_chevron_right), stringResource(R.string.next_day))
        }
    }
}

@Composable
private fun DashboardHeaderRow(header: DashboardHeader, onOpenSettings: () -> Unit) {
    val spacing = HabitTheme.spacing
    val locale = currentLocale()
    val dayOfWeek = remember(header.date, locale) { header.date.dayOfWeek.getDisplayName(TextStyle.FULL, locale) }
    val formattedDate = remember(header.date, locale) {
        header.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = spacing.screenHorizontal, end = spacing.sm, top = spacing.lg),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(dayOfWeek.uppercase(locale), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(
                text = greeting(header.dayPeriod, header.displayName),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .padding(top = spacing.xs)
                    .semantics { heading() },
            )
            Text(
                formattedDate,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = spacing.xxs),
            )
        }
        IconButton(onClick = onOpenSettings) {
            Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.dashboard_settings_cd))
        }
    }
}

@Composable
private fun greeting(period: DayPeriod, name: String): String {
    val hasName = name.isNotBlank()
    return when (period) {
        DayPeriod.MORNING -> if (hasName) stringResource(R.string.greeting_morning_named, name) else stringResource(R.string.greeting_morning)
        DayPeriod.AFTERNOON -> if (hasName) stringResource(R.string.greeting_afternoon_named, name) else stringResource(R.string.greeting_afternoon)
        DayPeriod.EVENING -> if (hasName) stringResource(R.string.greeting_evening_named, name) else stringResource(R.string.greeting_evening)
        DayPeriod.NIGHT -> if (hasName) stringResource(R.string.greeting_night_named, name) else stringResource(R.string.greeting_night)
    }
}

object DashboardTestTags {
    const val SCREEN = "screen_today"
}
