package com.example.habitapp.presentation.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.habitapp.R
import com.example.habitapp.domain.model.HabitDisplay
import com.example.habitapp.domain.model.HabitType
import com.example.habitapp.domain.model.TaskOccurrence
import com.example.habitapp.domain.usecase.HabitDayItem
import com.example.habitapp.presentation.components.EmptyState
import com.example.habitapp.presentation.components.FullScreenError
import com.example.habitapp.presentation.components.FullScreenLoading
import com.example.habitapp.presentation.components.ListBadge
import com.example.habitapp.presentation.components.LocalDatePickerDialog
import com.example.habitapp.presentation.components.relativeDateText
import com.example.habitapp.presentation.habits.AmountDialog
import com.example.habitapp.presentation.theme.HabitColor
import kotlinx.coroutines.launch
import java.time.LocalDate

class HomeNavigation(
    val onOpenHabit: (String) -> Unit,
    val onNewHabit: () -> Unit,
    val onOpenTask: (String, LocalDate?) -> Unit,
    val onReports: () -> Unit,
    val onTimer: () -> Unit,
    val onManageLists: () -> Unit,
    val onRoutines: () -> Unit,
    val onStatistics: () -> Unit,
)

@Composable
fun HomeScreen(navigation: HomeNavigation, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var fabOpen by rememberSaveable { mutableStateOf(false) }
    var quickTask by rememberSaveable { mutableStateOf(false) }
    var amountFor by remember { mutableStateOf<HabitDayItem?>(null) }
    var taskMenu by remember { mutableStateOf<TaskOccurrence?>(null) }
    var postponeAll by remember { mutableStateOf<List<TaskOccurrence>?>(null) }
    var pickDateFor by remember { mutableStateOf<List<TaskOccurrence>?>(null) }
    var pickHomeDate by remember { mutableStateOf(false) }

    BackHandler(enabled = drawerOpen) { drawerOpen = false }
    BackHandler(enabled = fabOpen) { fabOpen = false }

    val ready = state as? HomeUiState.Ready
    HomeDrawerHost(
        open = drawerOpen,
        onClose = { drawerOpen = false },
        drawerContent = {
            if (ready != null) {
                HomeDrawer(
                    state = ready,
                    onSelect = { view ->
                        viewModel.setView(view)
                        drawerOpen = false
                    },
                    onDisplay = viewModel::setHabitDisplay,
                    onManageLists = {
                        drawerOpen = false
                        navigation.onManageLists()
                    },
                    onRoutines = {
                        drawerOpen = false
                        navigation.onRoutines()
                    },
                    onStatistics = {
                        drawerOpen = false
                        navigation.onStatistics()
                    },
                )
            }
        },
    ) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag(HomeTestTags.SCREEN)) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                when (val s = state) {
                    HomeUiState.Loading -> FullScreenLoading()
                    HomeUiState.Error -> FullScreenError(stringResource(R.string.error_loading))
                    is HomeUiState.Ready -> {
                        HomeTopBar(
                            title = homeTitle(s),
                            onMenu = { drawerOpen = true },
                            onReports = navigation.onReports,
                            onTimer = navigation.onTimer,
                            onTitleClick = { if (s.view == HomeView.Today || s.view == HomeView.AllHabits) pickHomeDate = true },
                        )
                        if (s.view == HomeView.Today || s.view == HomeView.AllHabits) {
                            WeekStrip(s.weekDays, s.selectedDate, s.today, viewModel::selectDate, viewModel::shiftWeek)
                            Spacer(Modifier.height(8.dp))
                        }
                        HorizontalDivider()
                        HomeContent(
                            state = s,
                            viewModel = viewModel,
                            navigation = navigation,
                            onTaskMenu = { taskMenu = it },
                            onPostpone = { postponeAll = it },
                            onAmount = { amountFor = it },
                            onNewTask = { quickTask = true },
                        )
                    }
                }
            }

            AnimatedVisibility(visible = fabOpen, enter = fadeIn(), exit = fadeOut()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable { fabOpen = false },
                )
            }
            Column(
                Modifier.align(Alignment.BottomEnd).padding(16.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (fabOpen) {
                    FabChoice(
                        stringResource(R.string.fab_task),
                        stringResource(R.string.fab_task_desc),
                        R.drawable.ic_check_circle,
                        onClick = { fabOpen = false; quickTask = true },
                        modifier = Modifier.testTag("fab_new_task"),
                    )
                    FabChoice(
                        stringResource(R.string.fab_habit),
                        stringResource(R.string.fab_habit_desc),
                        R.drawable.ic_spa,
                        onClick = { fabOpen = false; navigation.onNewHabit() },
                        modifier = Modifier.testTag("fab_new_habit"),
                    )
                }
                Box(
                    Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .clickable { fabOpen = !fabOpen }
                        .testTag("dashboard_fab"),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(if (fabOpen) R.drawable.ic_close else R.drawable.ic_add),
                        stringResource(R.string.action_add),
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
        }
    }

    if (quickTask && ready != null) {
        QuickTaskSheet(
            initialDate = if (ready.view == HomeView.Today) ready.selectedDate else ready.today,
            today = ready.today,
            lists = ready.lists.values.filter { !it.archived }.sortedBy { it.sortOrder },
            defaultListId = (ready.view as? HomeView.InList)?.listId ?: ready.defaultListId,
            onCreate = viewModel::createTask,
            onManageLists = { quickTask = false; navigation.onManageLists() },
            onDismiss = { quickTask = false },
        )
    }
    amountFor?.let { item ->
        AmountDialog(item.habit, { amount, note -> viewModel.addHabitProgress(item.habit.id, amount, note = note) }, { amountFor = null })
    }
    taskMenu?.let { occ ->
        TaskActionsDialog(
            occurrence = occ,
            onDismiss = { taskMenu = null },
            onComplete = { viewModel.toggleTask(occ) },
            onToday = { ready?.let { viewModel.postpone(listOf(occ), it.today) } },
            onTomorrow = { ready?.let { viewModel.postpone(listOf(occ), it.today.plusDays(1)) } },
            onPickDate = { pickDateFor = listOf(occ) },
            onEdit = { navigation.onOpenTask(occ.task.id, occ.date) },
            onDelete = { viewModel.deleteOccurrence(occ) },
        )
    }
    postponeAll?.let { list ->
        PostponeDialog(
            count = list.size,
            onToday = { ready?.let { viewModel.postpone(list, it.today) } },
            onTomorrow = { ready?.let { viewModel.postpone(list, it.today.plusDays(1)) } },
            onPickDate = { pickDateFor = list },
            onDismiss = { postponeAll = null },
        )
    }
    pickDateFor?.let { list ->
        LocalDatePickerDialog(ready?.today ?: LocalDate.now(), { viewModel.postpone(list, it) }, { pickDateFor = null })
    }
    if (pickHomeDate && ready != null) {
        LocalDatePickerDialog(ready.selectedDate, viewModel::selectDate, { pickHomeDate = false })
    }
}

@Composable
private fun homeTitle(s: HomeUiState.Ready): String = when (val v = s.view) {
    HomeView.Today -> relativeDateText(s.selectedDate, s.today)
    HomeView.AllTasks -> stringResource(R.string.drawer_all_tasks)
    HomeView.AllHabits -> stringResource(R.string.drawer_all_habits)
    is HomeView.InList -> s.listName ?: stringResource(R.string.task_list)
    is HomeView.Search -> stringResource(R.string.search_title, v.query)
}

@Composable
private fun HomeContent(
    state: HomeUiState.Ready,
    viewModel: HomeViewModel,
    navigation: HomeNavigation,
    onTaskMenu: (TaskOccurrence) -> Unit,
    onPostpone: (List<TaskOccurrence>) -> Unit,
    onAmount: (HabitDayItem) -> Unit,
    onNewTask: () -> Unit,
) {
    val habitActions = remember(viewModel, navigation) {
        HabitRowActions(
            onOpen = navigation.onOpenHabit,
            onPlus = { item ->
                if (item.habit.type == HabitType.BOOLEAN) {
                    viewModel.toggleHabitDay(item.habit.id, item.progress.date)
                } else {
                    viewModel.addHabitProgress(item.habit.id, item.habit.step, item.progress.date)
                }
            },
            onPlusLong = onAmount,
            onDayTap = { item, date -> viewModel.toggleHabitDay(item.habit.id, date) },
        )
    }
    if (state.isEmpty) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            EmptyState(
                iconRes = R.drawable.ic_today,
                title = stringResource(if (state.view is HomeView.Search) R.string.journal_no_results else R.string.home_empty_title),
                message = stringResource(if (state.view is HomeView.Search) R.string.journal_no_results_message else R.string.home_empty_message),
                action = {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BigButton(stringResource(R.string.fab_task), R.drawable.ic_check_circle, primary = false, onClick = onNewTask)
                        BigButton(stringResource(R.string.fab_habit), R.drawable.ic_spa, primary = true, onClick = navigation.onNewHabit, modifier = Modifier.testTag("empty_new_habit"))
                    }
                },
            )
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 110.dp)) {
        state.sections.forEach { section ->
            item(key = "header_${section.id}") {
                SectionHeader(section, state, onToggle = { viewModel.toggleSection(section.id) }, onPostpone = { onPostpone(section.tasks) })
            }
            if (!section.collapsed) {
                items(section.tasks, key = { "${section.id}_${it.key}_${it.completedAt?.toEpochMilli()}" }) { occ ->
                    TaskCard(
                        occurrence = occ,
                        list = state.lists[occ.task.listId],
                        today = state.today,
                        showDate = section.kind != SectionKind.TASKS || state.view != HomeView.Today || occ.date != state.selectedDate,
                        onToggle = { viewModel.toggleTask(occ) },
                        onOpen = { navigation.onOpenTask(occ.task.id, occ.date) },
                        onLongPress = { onTaskMenu(occ) },
                        modifier = Modifier.animateItem(),
                    )
                }
                items(section.habits, key = { "${section.id}_habit_${it.habit.id}" }) { item ->
                    HabitRow(
                        item = item,
                        display = state.habitDisplay,
                        weekDays = state.weekDays,
                        selected = state.selectedDate,
                        today = state.today,
                        firstDayOfWeek = state.firstDayOfWeek,
                        actions = habitActions,
                        modifier = Modifier.animateItem(),
                    )
                }
                if (section.kind == SectionKind.HABITS && section.habits.isEmpty()) {
                    item(key = "${section.id}_empty") {
                        TextButton(onClick = navigation.onNewHabit, modifier = Modifier.padding(horizontal = 12.dp)) {
                            Text(stringResource(R.string.home_add_first_habit))
                        }
                    }
                }
                if (section.kind == SectionKind.TASKS && section.tasks.isEmpty() && state.view == HomeView.Today) {
                    item(key = "${section.id}_empty") {
                        TextButton(onClick = onNewTask, modifier = Modifier.padding(horizontal = 12.dp)) {
                            Text(stringResource(R.string.home_add_task))
                        }
                    }
                }
            }
            item(key = "divider_${section.id}") { HorizontalDivider(Modifier.padding(top = 6.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(section: HomeSection, state: HomeUiState.Ready, onToggle: () -> Unit, onPostpone: () -> Unit) {
    val title = when (section.title) {
        SectionTitle.OVERDUE -> stringResource(R.string.tasks_overdue)
        SectionTitle.TASKS -> stringResource(R.string.home_task_list)
        SectionTitle.HABITS -> stringResource(R.string.home_habit_list)
        SectionTitle.COMPLETED_TASKS -> stringResource(R.string.home_completed_tasks)
        SectionTitle.TODAY -> stringResource(R.string.date_today)
        SectionTitle.UPCOMING -> stringResource(R.string.tasks_upcoming)
        SectionTitle.NO_DATE -> stringResource(R.string.tasks_no_date)
        SectionTitle.RESULTS_TASKS -> stringResource(R.string.nav_tasks)
        SectionTitle.RESULTS_HABITS -> stringResource(R.string.nav_habits)
    }
    val icon = when (section.kind) {
        SectionKind.OVERDUE -> R.drawable.ic_info
        SectionKind.TASKS -> R.drawable.ic_tasks
        SectionKind.HABITS -> R.drawable.ic_spa
        SectionKind.COMPLETED -> R.drawable.ic_check_circle
    }
    SectionHeaderRow(
        icon = icon,
        title = title,
        count = section.total,
        collapsed = section.collapsed,
        onToggle = onToggle,
        ring = if (section.kind == SectionKind.TASKS || section.kind == SectionKind.HABITS) section.done to section.total else null,
        trailing = if (section.kind == SectionKind.OVERDUE) {
            {
                Text(
                    stringResource(R.string.home_postpone, section.total),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onPostpone).padding(6.dp).testTag("postpone_overdue"),
                )
                Icon(painterResource(if (section.collapsed) R.drawable.ic_chevron_right else R.drawable.ic_expand), null)
            }
        } else {
            null
        },
    )
}

/** Side menu that only exists while open, so it can never intercept taps on the page. */
@Composable
private fun HomeDrawerHost(open: Boolean, onClose: () -> Unit, drawerContent: @Composable () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        content()
        AnimatedVisibility(open, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f))
                    .clickable(interactionSource = null, indication = null, onClick = onClose),
            )
        }
        AnimatedVisibility(
            open,
            enter = slideInHorizontally { -it },
            exit = slideOutHorizontally { -it },
        ) {
            Box(Modifier.fillMaxHeight().statusBarsPadding()) { drawerContent() }
        }
    }
}

@Composable
private fun HomeDrawer(
    state: HomeUiState.Ready,
    onSelect: (HomeView) -> Unit,
    onDisplay: (HabitDisplay) -> Unit,
    onManageLists: () -> Unit,
    onRoutines: () -> Unit,
    onStatistics: () -> Unit,
) {
    var listsExpanded by rememberSaveable { mutableStateOf(true) }
    var displayExpanded by rememberSaveable { mutableStateOf(true) }
    var query by rememberSaveable { mutableStateOf("") }
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp), modifier = Modifier.fillMaxHeight().width(320.dp)) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(8.dp))
                DrawerDivider(stringResource(R.string.drawer_lists_filters), listsExpanded) { listsExpanded = !listsExpanded }
                if (listsExpanded) {
                    DrawerRow(stringResource(R.string.drawer_today_view), state.drawer.today, state.view == HomeView.Today, { onSelect(HomeView.Today) }, {
                        DrawerIcon(R.drawable.ic_calendar)
                    })
                    DrawerRow(stringResource(R.string.drawer_all_tasks), state.drawer.allTasks, state.view == HomeView.AllTasks, { onSelect(HomeView.AllTasks) }, {
                        DrawerIcon(R.drawable.ic_check_circle)
                    }, Modifier.testTag("drawer_all_tasks"))
                    DrawerRow(stringResource(R.string.drawer_all_habits), state.drawer.habits, state.view == HomeView.AllHabits, { onSelect(HomeView.AllHabits) }, {
                        DrawerIcon(R.drawable.ic_spa)
                    }, Modifier.testTag("drawer_all_habits"))
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), thickness = 2.dp)
                    state.drawer.lists.forEach { entry ->
                        DrawerRow(
                            entry.list.name,
                            entry.count,
                            (state.view as? HomeView.InList)?.listId == entry.list.id,
                            { onSelect(HomeView.InList(entry.list.id)) },
                            { ListBadge(entry.list.icon, entry.list.colorKey, size = 32.dp) },
                        )
                    }
                }
                DrawerDivider(stringResource(R.string.drawer_habit_display), displayExpanded) { displayExpanded = !displayExpanded }
                if (displayExpanded) {
                    listOf(
                        HabitDisplay.COMPACT to (R.string.display_compact to R.drawable.ic_view_compact),
                        HabitDisplay.WEEKLY to (R.string.display_weekly to R.drawable.ic_view_week),
                        HabitDisplay.MONTHLY to (R.string.display_monthly to R.drawable.ic_view_month),
                        HabitDisplay.YEARLY to (R.string.display_yearly to R.drawable.ic_view_year),
                    ).forEach { (display, res) ->
                        DrawerRow(stringResource(res.first), null, state.habitDisplay == display, { onDisplay(display) }, {
                            DrawerIcon(res.second, HabitColor.LILAC.color)
                        })
                    }
                }
                DrawerDivider(stringResource(R.string.drawer_more), true) { }
                DrawerRow(stringResource(R.string.routines_title), null, false, onRoutines, { DrawerIcon(R.drawable.ic_routine, HabitColor.TEAL.color) })
                DrawerRow(stringResource(R.string.statistics_title), null, false, onStatistics, { DrawerIcon(R.drawable.ic_chart, HabitColor.PEACH.color) })
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(80) },
                placeholder = { Text(stringResource(R.string.drawer_search)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (query.isNotBlank()) onSelect(HomeView.Search(query)) }),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            )
            BigButton(
                stringResource(R.string.drawer_manage_lists),
                R.drawable.ic_settings,
                primary = true,
                onClick = onManageLists,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun TaskActionsDialog(
    occurrence: TaskOccurrence,
    onDismiss: () -> Unit,
    onComplete: () -> Unit,
    onToday: () -> Unit,
    onTomorrow: () -> Unit,
    onPickDate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    ActionListDialog(
        title = occurrence.task.title,
        actions = listOf(
            (if (occurrence.completed) R.string.task_mark_open else R.string.action_mark_done) to onComplete,
            R.string.reschedule_today to onToday,
            R.string.reschedule_tomorrow to onTomorrow,
            R.string.reschedule_pick to onPickDate,
            R.string.action_edit to onEdit,
            R.string.action_delete to onDelete,
        ),
        onDismiss = onDismiss,
    )
}

@Composable
private fun PostponeDialog(count: Int, onToday: () -> Unit, onTomorrow: () -> Unit, onPickDate: () -> Unit, onDismiss: () -> Unit) {
    ActionListDialog(
        title = stringResource(R.string.home_postpone, count),
        actions = listOf(R.string.reschedule_today to onToday, R.string.reschedule_tomorrow to onTomorrow, R.string.reschedule_pick to onPickDate),
        onDismiss = onDismiss,
    )
}

@Composable
fun ActionListDialog(title: String, actions: List<Pair<Int, () -> Unit>>, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(vertical = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp), maxLines = 2)
            actions.forEach { (label, action) ->
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            action()
                            onDismiss()
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                )
            }
        }
    }
}

object HomeTestTags {
    const val SCREEN = "screen_today"
}
