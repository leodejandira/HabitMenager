package com.example.habitapp.presentation.habits

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.habitapp.domain.model.HabitGroup
import com.example.habitapp.presentation.components.ConfirmDialog
import com.example.habitapp.presentation.components.EmojiBadge
import com.example.habitapp.presentation.components.EmptyState
import com.example.habitapp.presentation.components.FullScreenError
import com.example.habitapp.presentation.components.FullScreenLoading
import com.example.habitapp.presentation.components.SectionHeader
import com.example.habitapp.presentation.components.streakText
import com.example.habitapp.presentation.theme.HabitColor
import com.example.habitapp.presentation.theme.HabitTheme

class HabitsNavigation(
    val onOpenHabit: (String) -> Unit,
    val onNewHabit: () -> Unit,
    val onSuggestions: () -> Unit,
    val onRoutines: () -> Unit,
    val onStatistics: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsScreen(navigation: HabitsNavigation, viewModel: HabitsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var renameGroup by remember { mutableStateOf<HabitGroup?>(null) }
    var deleteGroup by remember { mutableStateOf<HabitGroup?>(null) }
    var deleteHabitId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = Modifier.testTag(HabitsTestTags.SCREEN),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_habits)) },
                actions = {
                    IconButton(onClick = navigation.onSuggestions) {
                        Icon(painterResource(R.drawable.ic_lightbulb), stringResource(R.string.suggestions_title))
                    }
                    IconButton(onClick = navigation.onRoutines) {
                        Icon(painterResource(R.drawable.ic_routine), stringResource(R.string.routines_title))
                    }
                    IconButton(onClick = navigation.onStatistics) {
                        Icon(painterResource(R.drawable.ic_chart), stringResource(R.string.statistics_title))
                    }
                    IconButton(onClick = viewModel::toggleArchived) {
                        Icon(painterResource(R.drawable.ic_archive), stringResource(R.string.habits_show_archived))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = navigation.onNewHabit, modifier = Modifier.testTag("habits_fab")) {
                Icon(painterResource(R.drawable.ic_add), stringResource(R.string.habit_new))
            }
        },
    ) { padding ->
        when (val s = state) {
            HabitsUiState.Loading -> FullScreenLoading(Modifier.padding(padding))
            HabitsUiState.Error -> FullScreenError(stringResource(R.string.error_loading), Modifier.padding(padding))
            is HabitsUiState.Ready -> {
                if (s.totalActive == 0 && s.archived.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        EmptyState(
                            iconRes = R.drawable.ic_habits,
                            title = stringResource(R.string.habits_empty_title),
                            message = stringResource(R.string.habits_empty_message),
                            action = {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Button(onClick = navigation.onNewHabit) { Text(stringResource(R.string.habit_new)) }
                                    OutlinedButton(onClick = navigation.onSuggestions, modifier = Modifier.padding(top = 8.dp)) {
                                        Text(stringResource(R.string.suggestions_title))
                                    }
                                }
                            },
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            top = padding.calculateTopPadding(),
                            bottom = padding.calculateBottomPadding() + 88.dp,
                        ),
                    ) {
                        s.sections.forEach { section ->
                            val group = section.group
                            if (group != null) {
                                item(key = "group_${group.id}") {
                                    GroupHeader(
                                        group = group,
                                        count = section.items.size,
                                        done = section.items.count { it.doneToday },
                                        onToggle = { viewModel.setGroupCollapsed(group) },
                                        onRename = { renameGroup = group },
                                        onDelete = { deleteGroup = group },
                                    )
                                }
                            }
                            if (group == null || !group.collapsed) {
                                items(section.items, key = { it.habit.id }) { item ->
                                    HabitListRow(
                                        item = item,
                                        onOpen = { navigation.onOpenHabit(item.habit.id) },
                                        onMoveUp = { viewModel.move(item.habit.id, -1) },
                                        onMoveDown = { viewModel.move(item.habit.id, 1) },
                                        modifier = Modifier.animateItem(),
                                    )
                                }
                            }
                        }
                        if (s.showArchived) {
                            item(key = "archivedHeader") { SectionHeader(stringResource(R.string.habits_archived)) }
                            if (s.archived.isEmpty()) {
                                item(key = "archivedEmpty") {
                                    Text(
                                        stringResource(R.string.habits_archived_empty),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = HabitTheme.spacing.screenHorizontal),
                                    )
                                }
                            }
                            items(s.archived, key = { "archived_" + it.habit.id }) { item ->
                                ListItem(
                                    headlineContent = { Text(item.habit.name) },
                                    leadingContent = { EmojiBadge(item.habit.emoji, HabitColor.fromKey(item.habit.colorKey).resolve(), 40) },
                                    trailingContent = {
                                        Row {
                                            OutlinedButton(onClick = { viewModel.restore(item.habit.id) }) {
                                                Text(stringResource(R.string.action_restore))
                                            }
                                            IconButton(onClick = { deleteHabitId = item.habit.id }) {
                                                Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.action_delete))
                                            }
                                        }
                                    },
                                    modifier = Modifier.clickable { navigation.onOpenHabit(item.habit.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    renameGroup?.let { group ->
        TextInputDialog(
            title = stringResource(R.string.group_rename),
            label = stringResource(R.string.group_name),
            initial = group.name,
            onConfirm = { viewModel.renameGroup(group, it) },
            onDismiss = { renameGroup = null },
        )
    }
    deleteGroup?.let { group ->
        ConfirmDialog(
            title = stringResource(R.string.group_delete_title),
            message = stringResource(R.string.group_delete_message),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { viewModel.deleteGroup(group) },
            onDismiss = { deleteGroup = null },
        )
    }
    deleteHabitId?.let { id ->
        ConfirmDialog(
            title = stringResource(R.string.habit_delete_title),
            message = stringResource(R.string.habit_delete_message),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { viewModel.delete(id) },
            onDismiss = { deleteHabitId = null },
        )
    }
}

@Composable
private fun GroupHeader(
    group: HabitGroup,
    count: Int,
    done: Int,
    onToggle: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = HabitTheme.spacing.screenHorizontal),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(if (group.collapsed) R.drawable.ic_expand else R.drawable.ic_collapse), null)
        Spacer(Modifier.width(HabitTheme.spacing.sm))
        Text(group.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        Text(
            stringResource(R.string.group_progress, done, count),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box {
            IconButton(onClick = { menu = true }) { Icon(painterResource(R.drawable.ic_more), stringResource(R.string.more_options)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.group_rename)) }, onClick = { menu = false; onRename() })
                DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

@Composable
private fun HabitListRow(
    item: HabitListItem,
    onOpen: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    val habit = item.habit
    ListItem(
        modifier = modifier.clickable(onClick = onOpen),
        colors = ListItemDefaults.colors(),
        leadingContent = { EmojiBadge(habit.emoji, HabitColor.fromKey(habit.colorKey).resolve(), 44) },
        headlineContent = { Text(habit.name) },
        supportingContent = {
            val rate = (item.completionRate * 100).toInt()
            val parts = buildList {
                add(habitTypeLabel(habit.type))
                add(frequencyLabel(habit.frequency.type))
                add(stringResource(R.string.completion_rate_short, rate))
                if (item.streak.current > 0) add("🔥 " + streakText(item.streak))
            }
            Text(parts.joinToString(" · "), maxLines = 1)
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(painterResource(R.drawable.ic_more), stringResource(R.string.more_options)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.move_up)) }, onClick = { menu = false; onMoveUp() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.move_down)) }, onClick = { menu = false; onMoveDown() })
                }
            }
        },
    )
}

object HabitsTestTags {
    const val SCREEN = "screen_habits"
}
