package com.example.habitapp.presentation.tasks

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import com.example.habitapp.presentation.components.ListBadge
import com.example.habitapp.presentation.components.ListIconPickerDialog
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.habitapp.R
import com.example.habitapp.domain.model.TaskList
import com.example.habitapp.domain.repository.TaskRepository
import com.example.habitapp.presentation.components.ColorPicker
import com.example.habitapp.presentation.components.ConfirmDialog
import com.example.habitapp.presentation.theme.HabitTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ListWithCount(val list: TaskList, val openTasks: Int)

@HiltViewModel
class TaskListsViewModel @Inject constructor(private val repository: TaskRepository) : ViewModel() {

    val lists: StateFlow<List<ListWithCount>> = combine(repository.observeLists(), repository.observeTaskData()) { lists, data ->
        lists.map { list -> ListWithCount(list, data.tasks.count { it.listId == list.id && it.completedAt == null }) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(list: TaskList) = launch { repository.saveList(list) }
    fun delete(list: TaskList) = launch { repository.deleteList(list.id) }
    fun setArchived(list: TaskList, archived: Boolean) = launch { repository.saveList(list.copy(archived = archived)) }

    fun move(index: Int, delta: Int) {
        val ids = lists.value.map { it.list.id }.toMutableList()
        val target = index + delta
        if (index !in ids.indices || target !in ids.indices) return
        ids.add(target, ids.removeAt(index))
        launch { repository.reorderLists(ids) }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { runCatching { block() }.onFailure { Log.e("TaskListsViewModel", "List action failed", it) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListsScreen(onBack: () -> Unit, viewModel: TaskListsViewModel = hiltViewModel()) {
    val lists by viewModel.lists.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<TaskList?>(null) }
    var deleting by remember { mutableStateOf<TaskList?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.lists_manage)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back)) } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editing = TaskList(UUID.randomUUID().toString(), "", "periwinkle", lists.size, archived = false, isInbox = false, icon = "checklist")
            }) { Icon(painterResource(R.drawable.ic_add), stringResource(R.string.list_new)) }
        },
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 96.dp)) {
            itemsIndexed(lists, key = { _, item -> item.list.id }) { index, item ->
                val list = item.list
                ListItem(
                    leadingContent = {
                        ListBadge(list.icon, list.colorKey, size = 36.dp)
                    },
                    headlineContent = { Text(list.name) },
                    supportingContent = {
                        val parts = buildList {
                            add(stringResource(R.string.list_open_tasks, item.openTasks))
                            if (list.isInbox) add(stringResource(R.string.list_default))
                            if (list.archived) add(stringResource(R.string.habit_archived_label))
                        }
                        Text(parts.joinToString(" · "))
                    },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { viewModel.move(index, -1) }, enabled = index > 0) {
                                Icon(painterResource(R.drawable.ic_collapse), stringResource(R.string.move_up))
                            }
                            IconButton(onClick = { viewModel.move(index, 1) }, enabled = index < lists.lastIndex) {
                                Icon(painterResource(R.drawable.ic_expand), stringResource(R.string.move_down))
                            }
                            IconButton(onClick = { editing = list }) { Icon(painterResource(R.drawable.ic_edit), stringResource(R.string.action_edit)) }
                            if (!list.isInbox) {
                                IconButton(onClick = { viewModel.setArchived(list, !list.archived) }) {
                                    Icon(
                                        painterResource(R.drawable.ic_archive),
                                        stringResource(if (list.archived) R.string.action_restore else R.string.action_archive),
                                    )
                                }
                                IconButton(onClick = { deleting = list }) { Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.action_delete)) }
                            }
                        }
                    },
                )
            }
        }
    }
    editing?.let { list ->
        ListEditDialog(list, onSave = viewModel::save, onDismiss = { editing = null })
    }
    deleting?.let { list ->
        ConfirmDialog(
            title = stringResource(R.string.list_delete_title),
            message = stringResource(R.string.list_delete_message),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { viewModel.delete(list) },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun ListEditDialog(list: TaskList, onSave: (TaskList) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(list.name) }
    var color by rememberSaveable { mutableStateOf(list.colorKey) }
    var icon by rememberSaveable { mutableStateOf(list.icon) }
    var pickingIcon by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (list.name.isEmpty()) R.string.list_new else R.string.list_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(HabitTheme.spacing.md)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ListBadge(icon, color, size = 48.dp, modifier = Modifier.clickable { pickingIcon = true })
                    TextButton(onClick = { pickingIcon = true }) { Text(stringResource(R.string.list_choose_icon)) }
                }
                OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, label = { Text(stringResource(R.string.habit_name)) }, singleLine = true)
                ColorPicker(selectedKey = color, onSelected = { color = it })
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                onSave(list.copy(name = name, colorKey = color, icon = icon))
                onDismiss()
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        modifier = Modifier.padding(HabitTheme.spacing.sm),
    )
    if (pickingIcon) {
        ListIconPickerDialog(colorKey = color, onSelected = { icon = it }, onDismiss = { pickingIcon = false })
    }
}
