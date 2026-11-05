package com.example.habitapp.presentation.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.habitapp.R
import com.example.habitapp.domain.model.TaskPriority
import com.example.habitapp.presentation.components.EmptyState
import com.example.habitapp.presentation.components.FullScreenError
import com.example.habitapp.presentation.components.FullScreenLoading
import com.example.habitapp.presentation.components.SectionHeader
import com.example.habitapp.presentation.components.StatTile
import com.example.habitapp.presentation.components.TaskRow
import com.example.habitapp.presentation.components.priorityLabel
import com.example.habitapp.presentation.components.relativeDateText
import com.example.habitapp.presentation.theme.HabitTheme
import java.time.LocalDate

@Composable
fun taskFilterLabel(filter: TaskFilter): String = stringResource(
    when (filter) {
        TaskFilter.TODAY -> R.string.date_today
        TaskFilter.UPCOMING -> R.string.tasks_upcoming
        TaskFilter.OVERDUE -> R.string.tasks_overdue
        TaskFilter.ALL -> R.string.tasks_all_open
        TaskFilter.NO_DATE -> R.string.tasks_no_date
        TaskFilter.COMPLETED -> R.string.tasks_completed
    },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    onNewTask: (LocalDate?) -> Unit,
    onOpenTask: (String, LocalDate?) -> Unit,
    onManageLists: () -> Unit,
    viewModel: TasksViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var sortMenu by remember { mutableStateOf(false) }
    var priorityMenu by remember { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.testTag(TasksTestTags.SCREEN),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_tasks)) },
                actions = {
                    val ready = state as? TasksUiState.Ready
                    Box {
                        IconButton(onClick = { priorityMenu = true }) {
                            Icon(painterResource(R.drawable.ic_flag), stringResource(R.string.tasks_filter_priority))
                        }
                        DropdownMenu(expanded = priorityMenu, onDismissRequest = { priorityMenu = false }) {
                            TaskPriority.entries.forEach { p ->
                                DropdownMenuItem(
                                    leadingIcon = { RadioButton(selected = ready?.minPriority == p, onClick = null) },
                                    text = { Text(if (p == TaskPriority.NONE) stringResource(R.string.filter_all_priorities) else stringResource(R.string.priority_at_least, priorityLabel(p))) },
                                    onClick = { priorityMenu = false; viewModel.setMinPriority(p) },
                                )
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { sortMenu = true }) { Icon(painterResource(R.drawable.ic_list_bulleted), stringResource(R.string.tasks_sort)) }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            TaskSort.entries.forEach { s ->
                                DropdownMenuItem(
                                    leadingIcon = { RadioButton(selected = ready?.sort == s, onClick = null) },
                                    text = {
                                        Text(
                                            stringResource(
                                                when (s) {
                                                    TaskSort.DUE_DATE -> R.string.sort_due_date
                                                    TaskSort.PRIORITY -> R.string.sort_priority
                                                    TaskSort.CREATED -> R.string.sort_created
                                                },
                                            ),
                                        )
                                    },
                                    onClick = { sortMenu = false; viewModel.setSort(s) },
                                )
                            }
                        }
                    }
                    IconButton(onClick = onManageLists) { Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.lists_manage)) }
                },
            )
        },
        floatingActionButton = {
            val ready = state as? TasksUiState.Ready
            FloatingActionButton(
                onClick = { onNewTask(if (ready?.filter == TaskFilter.NO_DATE) null else ready?.today) },
                modifier = Modifier.testTag("tasks_fab"),
            ) {
                Icon(painterResource(R.drawable.ic_add), stringResource(R.string.task_new))
            }
        },
    ) { padding ->
        when (val s = state) {
            TasksUiState.Loading -> FullScreenLoading(Modifier.padding(padding))
            TasksUiState.Error -> FullScreenError(stringResource(R.string.error_loading), Modifier.padding(padding))
            is TasksUiState.Ready -> {
                val listNames = s.lists.associate { it.id to it.name }
                LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 88.dp)) {
                    item(key = "filters") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = HabitTheme.spacing.screenHorizontal),
                            horizontalArrangement = Arrangement.spacedBy(HabitTheme.spacing.sm),
                        ) {
                            items(TaskFilter.entries) { f ->
                                FilterChip(selected = s.filter == f, onClick = { viewModel.setFilter(f) }, label = { Text(taskFilterLabel(f)) })
                            }
                        }
                    }
                    item(key = "lists") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = HabitTheme.spacing.screenHorizontal),
                            horizontalArrangement = Arrangement.spacedBy(HabitTheme.spacing.sm),
                        ) {
                            item { FilterChip(selected = s.listId == null, onClick = { viewModel.setList(null) }, label = { Text(stringResource(R.string.lists_all)) }) }
                            items(s.lists, key = { it.id }) { list ->
                                FilterChip(
                                    selected = s.listId == list.id,
                                    onClick = { viewModel.setList(if (s.listId == list.id) null else list.id) },
                                    label = { Text(list.name) },
                                )
                            }
                        }
                    }
                    item(key = "stats") {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = HabitTheme.spacing.screenHorizontal, vertical = HabitTheme.spacing.md),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            StatTile(s.stats.open.toString(), stringResource(R.string.tasks_stat_open))
                            StatTile(s.stats.completedToday.toString(), stringResource(R.string.tasks_stat_today))
                            StatTile(s.stats.completedThisWeek.toString(), stringResource(R.string.tasks_stat_week))
                            StatTile(s.stats.completedTotal.toString(), stringResource(R.string.tasks_stat_total))
                        }
                    }
                    val empty = s.sections.all { it.items.isEmpty() }
                    if (empty) {
                        item(key = "empty") {
                            Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                                EmptyState(
                                    iconRes = R.drawable.ic_tasks,
                                    title = stringResource(if (s.hasAnyTask) R.string.tasks_nothing_here else R.string.tasks_empty_title),
                                    message = stringResource(if (s.hasAnyTask) R.string.tasks_nothing_here_message else R.string.tasks_empty_message),
                                    action = { Button(onClick = { onNewTask(s.today) }) { Text(stringResource(R.string.task_new)) } },
                                )
                            }
                        }
                    }
                    s.sections.forEach { section ->
                        if (section.items.isEmpty()) return@forEach
                        val showHeader = section.titleOverdue || (section.date != null && s.filter != TaskFilter.TODAY)
                        if (showHeader) {
                            item(key = "h_${section.date}_${section.titleOverdue}") {
                                SectionHeader(
                                    if (section.titleOverdue || section.date == null) {
                                        stringResource(R.string.tasks_overdue)
                                    } else {
                                        relativeDateText(section.date, s.today)
                                    },
                                )
                            }
                        }
                        items(section.items, key = { "${section.titleOverdue}_${it.key}_${it.completedAt?.toEpochMilli()}" }) { occ ->
                            TaskRow(
                                occurrence = occ,
                                listName = listNames[occ.task.listId],
                                today = s.today,
                                onToggle = viewModel::toggle,
                                onOpen = { onOpenTask(it.task.id, it.date) },
                                showDate = s.filter != TaskFilter.UPCOMING,
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }
}

object TasksTestTags {
    const val SCREEN = "screen_tasks"
}
