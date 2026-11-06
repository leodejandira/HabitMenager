package com.example.habitapp.presentation.home

import android.Manifest
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.habitapp.R
import com.example.habitapp.domain.model.RecurrenceType
import com.example.habitapp.domain.model.TaskList
import com.example.habitapp.domain.model.TaskPriority
import com.example.habitapp.domain.model.TaskRecurrence
import com.example.habitapp.presentation.components.ListBadge
import com.example.habitapp.presentation.components.LocalDatePickerDialog
import com.example.habitapp.presentation.components.LocalTimePickerDialog
import com.example.habitapp.presentation.components.priorityColor
import com.example.habitapp.presentation.components.priorityLabel
import com.example.habitapp.presentation.components.rememberDateFormatter
import com.example.habitapp.presentation.tasks.recurrenceLabel
import com.example.habitapp.presentation.theme.HabitColor
import java.time.LocalDate
import java.time.LocalTime

/** Date, time, repeat and notification chosen in the "date and time" dialog. */
data class Schedule(
    val date: LocalDate?,
    val time: LocalTime?,
    val recurrence: RecurrenceType,
    val remind: Boolean,
)

/** "What would you like to do?" quick-add panel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickTaskSheet(
    initialDate: LocalDate?,
    today: LocalDate,
    lists: List<TaskList>,
    defaultListId: String?,
    onCreate: (TaskDraft) -> Unit,
    onManageLists: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var schedule by remember { mutableStateOf(Schedule(initialDate, null, RecurrenceType.NONE, false)) }
    var listId by rememberSaveable { mutableStateOf(defaultListId.orEmpty()) }
    var priority by rememberSaveable { mutableStateOf(TaskPriority.NONE) }
    var colorKey by rememberSaveable { mutableStateOf<String?>(null) }
    var showSchedule by remember { mutableStateOf(false) }
    var listMenu by remember { mutableStateOf(false) }
    var priorityMenu by remember { mutableStateOf(false) }
    var colorMenu by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val list = lists.firstOrNull { it.id == listId } ?: lists.firstOrNull()
    LaunchedEffect(Unit) { focus.requestFocus() }

    fun submit() {
        if (title.isBlank() || list == null) return
        onCreate(
            TaskDraft(
                title = title.trim(),
                description = description.trim(),
                date = schedule.date,
                time = schedule.time,
                recurrence = TaskRecurrence(schedule.recurrence, if (schedule.recurrence == RecurrenceType.WEEKDAYS) setOfNotNull(schedule.date?.dayOfWeek) else emptySet()),
                remind = schedule.remind,
                listId = list.id,
                priority = priority,
                colorKey = colorKey,
            ),
        )
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = null,
    ) {
        Column(Modifier.imePadding().padding(bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 12.dp, top = 8.dp)) {
                TextField(
                    value = title,
                    onValueChange = { title = it.take(200) },
                    placeholder = { Text(stringResource(R.string.quick_task_hint), style = MaterialTheme.typography.titleMedium) },
                    textStyle = MaterialTheme.typography.titleMedium,
                    colors = transparentFieldColors(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.weight(1f).focusRequester(focus).testTag("quick_task_title"),
                )
                Box {
                    TileIconButton(R.drawable.ic_contrast, stringResource(R.string.habit_color), { colorMenu = true })
                    DropdownMenu(expanded = colorMenu, onDismissRequest = { colorMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.color_list_default)) }, onClick = { colorKey = null; colorMenu = false })
                        HabitColor.entries.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c.key) },
                                leadingIcon = { Box(Modifier.size(20.dp).clip(CircleShape).background(c.color)) },
                                onClick = { colorKey = c.key; colorMenu = false },
                            )
                        }
                    }
                }
            }
            TextField(
                value = description,
                onValueChange = { description = it.take(2000) },
                placeholder = { Text(stringResource(R.string.task_description)) },
                colors = transparentFieldColors(),
                modifier = Modifier.fillMaxWidth().height(72.dp),
            )
            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ChipBox(onClick = { showSchedule = true }, modifier = Modifier.testTag("quick_task_date")) {
                    Box(Modifier.size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerLowest), contentAlignment = Alignment.Center) {
                        Icon(painterResource(if (schedule.remind) R.drawable.ic_bell else R.drawable.ic_bell_off), null, Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(scheduleLabel(schedule, today), style = MaterialTheme.typography.bodyLarge)
                }
                Box {
                    ChipBox(onClick = { listMenu = true }) { ListBadge(list?.icon, list?.colorKey, size = 30.dp) }
                    DropdownMenu(expanded = listMenu, onDismissRequest = { listMenu = false }) {
                        lists.forEach { l ->
                            DropdownMenuItem(
                                text = { Text(l.name) },
                                trailingIcon = { ListBadge(l.icon, l.colorKey, size = 28.dp) },
                                onClick = { listId = l.id; listMenu = false },
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text(stringResource(R.string.lists_manage)) }, onClick = { listMenu = false; onManageLists() })
                    }
                }
                Box {
                    ChipBox(onClick = { priorityMenu = true }) {
                        Icon(
                            painterResource(if (priority == TaskPriority.NONE) R.drawable.ic_flag else R.drawable.ic_flag_fill),
                            stringResource(R.string.task_priority),
                            tint = if (priority == TaskPriority.NONE) MaterialTheme.colorScheme.onSurfaceVariant else priorityColor(priority),
                        )
                    }
                    PriorityMenu(expanded = priorityMenu, onDismiss = { priorityMenu = false }, onSelected = { priority = it })
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(if (title.isBlank()) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.primary)
                        .clickable(enabled = title.isNotBlank()) { submit() }
                        .testTag("quick_task_send"),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_arrow_up), stringResource(R.string.action_save), tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
    if (showSchedule) {
        ScheduleDialog(initial = schedule, onApply = { schedule = it }, onDismiss = { showSchedule = false })
    }
}

@Composable
fun PriorityMenu(expanded: Boolean, onDismiss: () -> Unit, onSelected: (TaskPriority) -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        listOf(TaskPriority.NONE, TaskPriority.LOW, TaskPriority.MEDIUM, TaskPriority.HIGH, TaskPriority.URGENT).forEach { p ->
            DropdownMenuItem(
                text = { Text(priorityLabel(p)) },
                trailingIcon = {
                    Icon(
                        painterResource(if (p == TaskPriority.NONE) R.drawable.ic_flag else R.drawable.ic_flag_fill),
                        null,
                        tint = if (p == TaskPriority.NONE) MaterialTheme.colorScheme.onSurfaceVariant else priorityColor(p),
                    )
                },
                onClick = {
                    onSelected(p)
                    onDismiss()
                },
            )
        }
    }
}

@Composable
private fun transparentFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
)

@Composable
fun ChipBox(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun scheduleLabel(schedule: Schedule, today: LocalDate): String {
    val formatter = rememberDateFormatter()
    val date = schedule.date ?: return stringResource(R.string.task_no_date)
    val base = when (date) {
        today -> stringResource(R.string.date_today)
        today.plusDays(1) -> stringResource(R.string.date_tomorrow)
        else -> date.format(formatter)
    }
    return schedule.time?.let { "$base %02d:%02d".format(it.hour, it.minute) } ?: base
}

/** "Date and time settings" dialog: date, time, repeat and notification. */
@Composable
fun ScheduleDialog(initial: Schedule, onApply: (Schedule) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val formatter = rememberDateFormatter()
    var draft by remember { mutableStateOf(initial) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var repeatMenu by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Text(
                stringResource(R.string.schedule_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            HorizontalDivider()
            FormRow(stringResource(R.string.schedule_date)) {
                ChipBox(onClick = { pickDate = true }) {
                    Text(draft.date?.format(formatter) ?: stringResource(R.string.schedule_none))
                    Icon(painterResource(R.drawable.ic_expand), null)
                }
                if (draft.date != null) {
                    Spacer(Modifier.width(6.dp))
                    ChipBox(onClick = { draft = draft.copy(date = null, time = null, recurrence = RecurrenceType.NONE, remind = false) }) {
                        Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.task_clear_date))
                    }
                }
            }
            HorizontalDivider()
            FormRow(stringResource(R.string.schedule_time)) {
                ChipBox(onClick = { if (draft.date != null) pickTime = true }) {
                    Text(draft.time?.let { "%02d:%02d".format(it.hour, it.minute) } ?: stringResource(R.string.schedule_none))
                    Icon(painterResource(R.drawable.ic_expand), null)
                }
                Spacer(Modifier.width(6.dp))
                ChipBox(onClick = { if (draft.time != null) draft = draft.copy(time = null) else if (draft.date != null) pickTime = true }) {
                    Icon(painterResource(if (draft.time != null) R.drawable.ic_delete else R.drawable.ic_add), null)
                }
            }
            HorizontalDivider()
            FormRow(stringResource(R.string.task_repeat)) {
                Box {
                    ChipBox(onClick = { if (draft.date != null) repeatMenu = true }) {
                        Text(recurrenceLabel(draft.recurrence))
                        Icon(painterResource(R.drawable.ic_expand), null)
                    }
                    DropdownMenu(expanded = repeatMenu, onDismissRequest = { repeatMenu = false }) {
                        RecurrenceType.entries.forEach { r ->
                            DropdownMenuItem(text = { Text(recurrenceLabel(r)) }, onClick = { draft = draft.copy(recurrence = r); repeatMenu = false })
                        }
                    }
                }
            }
            HorizontalDivider()
            FormRow(stringResource(R.string.schedule_notification)) {
                Switch(
                    checked = draft.remind,
                    enabled = draft.date != null,
                    onCheckedChange = { on ->
                        if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        draft = draft.copy(remind = on)
                    },
                )
            }
            HorizontalDivider()
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton(stringResource(R.string.action_cancel), R.drawable.ic_close, primary = false, onClick = onDismiss, modifier = Modifier.weight(1f))
                BigButton(stringResource(R.string.schedule_apply), R.drawable.ic_save, primary = true, onClick = { onApply(draft); onDismiss() }, modifier = Modifier.weight(1f))
            }
        }
    }
    if (pickDate) LocalDatePickerDialog(draft.date ?: LocalDate.now(), { draft = draft.copy(date = it) }, { pickDate = false })
    if (pickTime) {
        LocalTimePickerDialog(draft.time ?: LocalTime.of(9, 0), DateFormat.is24HourFormat(context), { draft = draft.copy(time = it) }, { pickTime = false })
    }
}

@Composable
fun FormRow(label: String, help: String? = null, content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
        if (help != null) {
            Spacer(Modifier.width(8.dp))
            HelpBubble(help)
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) { content() }
    }
}

@Composable
fun HelpBubble(text: String) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest).clickable { open = true },
            contentAlignment = Alignment.Center,
        ) { Text("?", fontWeight = FontWeight.Bold) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text(text, modifier = Modifier.padding(12.dp).width(240.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Large rounded action button used at the bottom of forms and dialogs. */
@Composable
fun BigButton(text: String, icon: Int?, primary: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val bg = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest
    val fg = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) bg else bg.copy(alpha = 0.5f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(painterResource(icon), null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, color = fg, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}
