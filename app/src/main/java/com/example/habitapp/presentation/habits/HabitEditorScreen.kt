package com.example.habitapp.presentation.habits

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.habitapp.R
import com.example.habitapp.domain.model.FrequencyType
import com.example.habitapp.domain.model.GoalPeriod
import com.example.habitapp.domain.model.GoalType
import com.example.habitapp.domain.model.HabitReminder
import com.example.habitapp.domain.model.HabitType
import com.example.habitapp.domain.model.TaskPriority
import com.example.habitapp.presentation.components.ColorPicker
import com.example.habitapp.presentation.components.FullScreenError
import com.example.habitapp.presentation.components.FullScreenLoading
import com.example.habitapp.presentation.components.ListBadge
import com.example.habitapp.presentation.components.LocalDatePickerDialog
import com.example.habitapp.presentation.components.LocalTimePickerDialog
import com.example.habitapp.presentation.components.WeekdayChips
import com.example.habitapp.presentation.components.formatAmount
import com.example.habitapp.presentation.components.priorityColor
import com.example.habitapp.presentation.components.priorityLabel
import com.example.habitapp.presentation.components.rememberDateFormatter
import com.example.habitapp.presentation.components.rememberTimeFormatter
import com.example.habitapp.presentation.home.BigButton
import com.example.habitapp.presentation.home.ChipBox
import com.example.habitapp.presentation.home.FormRow
import com.example.habitapp.presentation.home.PriorityMenu
import com.example.habitapp.presentation.theme.HabitColor
import com.example.habitapp.presentation.theme.OnPastel
import java.time.LocalTime

@Composable
fun habitTypeLabel(type: HabitType): String = stringResource(
    if (type == HabitType.QUIT) R.string.tracking_quit else R.string.tracking_count,
)

@Composable
fun frequencyLabel(type: FrequencyType): String = stringResource(
    when (type) {
        FrequencyType.WEEKDAYS -> R.string.freq_weekdays
        else -> R.string.freq_daily
    },
)

@Composable
fun periodLabel(period: GoalPeriod): String = stringResource(
    when (period) {
        GoalPeriod.DAY -> R.string.period_day
        GoalPeriod.WEEK -> R.string.period_week
        GoalPeriod.MONTH -> R.string.period_month
        GoalPeriod.YEAR -> R.string.period_year
    },
)

@Composable
fun unitLabel(unit: String, count: Double): String = when (unit) {
    HabitUnits.TIMES -> pluralStringResource(R.plurals.times_word, count.toInt().coerceAtLeast(1))
    else -> unit
}

/** Header colour band + scrollable form + Cancel/Save bar. */
@Composable
fun HabitEditorScreen(onBack: () -> Unit, viewModel: HabitEditorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val editing = state as? HabitEditorUiState.Editing
    LaunchedEffect(editing?.saved) { if (editing?.saved == true) onBack() }
    val headerColor = editing?.let { HabitColor.fromKey(it.form.colorKey).color } ?: MaterialTheme.colorScheme.surfaceContainerHigh

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.fillMaxWidth().background(headerColor).statusBarsPadding().padding(12.dp)) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(OnPastel.copy(alpha = 0.08f)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back), tint = OnPastel) }
            Text(
                stringResource(if (editing?.form?.isNew != false) R.string.habit_new else R.string.habit_edit),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = OnPastel,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        when (val s = state) {
            HabitEditorUiState.Loading -> FullScreenLoading()
            HabitEditorUiState.NotFound -> FullScreenError(stringResource(R.string.error_not_found))
            is HabitEditorUiState.Editing -> {
                Column(Modifier.weight(1f).imePadding().verticalScroll(rememberScrollState())) {
                    HabitFormContent(s, viewModel)
                }
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BigButton(stringResource(R.string.action_cancel), R.drawable.ic_close, primary = false, onClick = onBack, modifier = Modifier.weight(1f))
                    BigButton(
                        stringResource(R.string.habit_save),
                        R.drawable.ic_save,
                        primary = true,
                        onClick = viewModel::save,
                        modifier = Modifier.weight(1f).testTag("habit_save"),
                    )
                }
            }
        }
    }
}

@Composable
private fun HabitFormContent(state: HabitEditorUiState.Editing, viewModel: HabitEditorViewModel) {
    val form = state.form
    val context = LocalContext.current
    val dateFormatter = rememberDateFormatter()
    val timeFormatter = rememberTimeFormatter()
    var colorDialog by remember { mutableStateOf(false) }
    var typeDialog by remember { mutableStateOf(false) }
    var goalTypeMenu by remember { mutableStateOf(false) }
    var unitMenu by remember { mutableStateOf(false) }
    var customUnit by remember { mutableStateOf(false) }
    var periodMenu by remember { mutableStateOf(false) }
    var listMenu by remember { mutableStateOf(false) }
    var priorityMenu by remember { mutableStateOf(false) }
    var stepDialog by remember { mutableStateOf(false) }
    var timerDialog by remember { mutableStateOf(false) }
    var pickStart by remember { mutableStateOf(false) }
    var pickQuitTime by remember { mutableStateOf(false) }
    var pickReminder by remember { mutableStateOf(false) }
    var editReminder by remember { mutableStateOf<HabitReminder?>(null) }
    var advanced by rememberSaveable { mutableStateOf(!form.isNew) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    state.errorRes?.let {
        Text(stringResource(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
    }
    FormRow(stringResource(R.string.habit_name), stringResource(R.string.help_name)) {
        OutlinedTextField(
            value = form.name,
            onValueChange = { v -> viewModel.update { it.copy(name = v.take(60)) } },
            placeholder = { Text(stringResource(R.string.habit_name_hint)) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.width(200.dp).testTag("habit_name_field"),
        )
    }
    HorizontalDivider()
    FormRow(stringResource(R.string.habit_color), stringResource(R.string.help_color)) {
        ChipBox(onClick = { colorDialog = true }) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(HabitColor.fromKey(form.colorKey).color))
        }
        Spacer(Modifier.width(6.dp))
        ChipBox(onClick = { colorDialog = true }) { Icon(painterResource(R.drawable.ic_chevron_right), null) }
    }
    HorizontalDivider()
    FormRow(stringResource(R.string.tracking_type), stringResource(R.string.help_tracking)) {
        ChipBox(onClick = { if (form.isNew) typeDialog = true }) {
            Icon(painterResource(if (form.quit) R.drawable.ic_block else R.drawable.ic_today), null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(if (form.quit) R.string.tracking_quit else R.string.tracking_count))
            if (form.isNew) Icon(painterResource(R.drawable.ic_chevron_right), null)
        }
    }
    HorizontalDivider()

    if (form.quit) {
        FormRow(stringResource(R.string.quit_started), stringResource(R.string.help_quit_start)) {
            ChipBox(onClick = { pickStart = true }) { Text(form.startDate.format(dateFormatter)) }
            Spacer(Modifier.width(6.dp))
            ChipBox(onClick = { pickQuitTime = true }) {
                val t = form.quitStartedAt?.atZone(java.time.ZoneId.systemDefault())?.toLocalTime()
                Text(t?.format(timeFormatter) ?: stringResource(R.string.schedule_none))
            }
        }
        HorizontalDivider()
    } else {
        FormRow(stringResource(R.string.goal_type), stringResource(R.string.help_goal_type)) {
            Box {
                ChipBox(onClick = { goalTypeMenu = true }) {
                    Icon(painterResource(if (form.goalType == GoalType.AT_LEAST) R.drawable.ic_thumb_up else R.drawable.ic_thumb_down), null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (form.goalType == GoalType.AT_LEAST) R.string.goal_positive else R.string.goal_negative))
                    Icon(painterResource(R.drawable.ic_expand), null)
                }
                DropdownMenu(expanded = goalTypeMenu, onDismissRequest = { goalTypeMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.goal_positive)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_thumb_up), null) },
                        onClick = { viewModel.update { it.copy(goalType = GoalType.AT_LEAST) }; goalTypeMenu = false },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.goal_negative)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_thumb_down), null) },
                        onClick = { viewModel.update { it.copy(goalType = GoalType.AT_MOST) }; goalTypeMenu = false },
                    )
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(if (form.goalType == GoalType.AT_LEAST) R.string.goal_at_least else R.string.goal_at_most),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                Stepper(value = form.target, onChange = { v -> viewModel.update { it.copy(target = v) } })
                Spacer(Modifier.width(10.dp))
                Box {
                    ChipBox(onClick = { unitMenu = true }, modifier = Modifier.testTag("habit_unit")) {
                        Text(unitLabel(form.unit, form.target), modifier = Modifier.width(90.dp))
                        Icon(painterResource(R.drawable.ic_expand), null)
                    }
                    DropdownMenu(expanded = unitMenu, onDismissRequest = { unitMenu = false }) {
                        HabitUnits.presets.forEach { u ->
                            DropdownMenuItem(
                                text = { Text(unitLabel(u, 2.0)) },
                                onClick = {
                                    viewModel.update {
                                        it.copy(
                                            unit = u,
                                            target = if (u == HabitUnits.MINUTES && it.unit != u) 15.0 else it.target,
                                            step = if (u == HabitUnits.MINUTES && it.unit != u) 5.0 else it.step,
                                        )
                                    }
                                    unitMenu = false
                                },
                                modifier = Modifier.testTag("unit_${u.ifEmpty { "times" }}"),
                            )
                        }
                        DropdownMenuItem(text = { Text(stringResource(R.string.unit_custom)) }, onClick = { unitMenu = false; customUnit = true })
                    }
                }
            }
            Text(stringResource(R.string.goal_per), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.padding(top = 8.dp)) {
                ChipBox(onClick = { periodMenu = true }) {
                    Text(periodLabel(form.goalPeriod))
                    Spacer(Modifier.width(8.dp))
                    Icon(painterResource(R.drawable.ic_expand), null)
                }
                DropdownMenu(expanded = periodMenu, onDismissRequest = { periodMenu = false }) {
                    GoalPeriod.entries.forEach { p ->
                        DropdownMenuItem(text = { Text(periodLabel(p)) }, onClick = { viewModel.update { it.copy(goalPeriod = p) }; periodMenu = false })
                    }
                }
            }
        }
        HorizontalDivider()
        if (form.goalPeriod == GoalPeriod.DAY) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                RadioLine(stringResource(R.string.days_all), !form.specificDays) { viewModel.update { it.copy(specificDays = false) } }
                RadioLine(stringResource(R.string.days_specific), form.specificDays) { viewModel.update { it.copy(specificDays = true) } }
                if (form.specificDays) {
                    WeekdayChips(form.weekdays, state.firstDayOfWeek, { d -> viewModel.update { it.copy(weekdays = d) } })
                }
            }
            HorizontalDivider()
        }
        // Reminders box
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                pluralStringResource(R.plurals.reminders_count, form.reminders.size, form.reminders.size),
                style = MaterialTheme.typography.bodyLarge,
            )
            form.reminders.forEach { r ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    ChipBox(onClick = { editReminder = r }) {
                        Icon(painterResource(R.drawable.ic_bell), null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(LocalTime.of(r.minuteOfDay / 60, r.minuteOfDay % 60).format(timeFormatter))
                    }
                    Spacer(Modifier.width(6.dp))
                    ChipBox(onClick = { viewModel.removeReminder(r.id) }) { Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.action_delete)) }
                }
                WeekdayChips(
                    selected = r.weekdays.ifEmpty { java.time.DayOfWeek.entries.toSet() },
                    firstDayOfWeek = state.firstDayOfWeek,
                    onChange = { days -> if (days.isNotEmpty()) viewModel.updateReminder(r.copy(weekdays = if (days.size == 7) emptySet() else days)) },
                )
            }
            Spacer(Modifier.height(10.dp))
            ChipBox(onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                pickReminder = true
            }) {
                Text(stringResource(R.string.habit_add_reminder), color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(8.dp))
                Icon(painterResource(R.drawable.ic_add), null, tint = MaterialTheme.colorScheme.secondary)
            }
        }
    }

    // "Additional settings" divider
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f), thickness = 3.dp)
        ChipBox(onClick = { advanced = !advanced }, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(stringResource(R.string.habit_additional_settings))
            Icon(painterResource(if (advanced) R.drawable.ic_collapse else R.drawable.ic_expand), null)
        }
        HorizontalDivider(Modifier.weight(1f), thickness = 3.dp)
    }
    if (advanced) {
        FormRow(stringResource(R.string.habit_description_label), stringResource(R.string.help_description)) {
            OutlinedTextField(
                value = form.description,
                onValueChange = { v -> viewModel.update { it.copy(description = v.take(500)) } },
                placeholder = { Text(stringResource(R.string.optional)) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.width(200.dp),
            )
        }
        HorizontalDivider()
        FormRow(stringResource(R.string.task_list), stringResource(R.string.help_list)) {
            Box {
                val list = state.lists.firstOrNull { it.id == form.listId }
                ChipBox(onClick = { listMenu = true }) {
                    if (list != null) ListBadge(list.icon, list.colorKey, size = 28.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(list?.name ?: stringResource(R.string.habit_no_list))
                    Icon(painterResource(R.drawable.ic_expand), null)
                }
                DropdownMenu(expanded = listMenu, onDismissRequest = { listMenu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.habit_no_list)) }, onClick = { viewModel.update { it.copy(listId = null) }; listMenu = false })
                    state.lists.forEach { l ->
                        DropdownMenuItem(
                            text = { Text(l.name) },
                            trailingIcon = { ListBadge(l.icon, l.colorKey, size = 26.dp) },
                            onClick = { viewModel.update { it.copy(listId = l.id) }; listMenu = false },
                        )
                    }
                }
            }
        }
        HorizontalDivider()
        FormRow(stringResource(R.string.task_priority), stringResource(R.string.help_priority)) {
            Box {
                val p = TaskPriority.fromLevel(form.priority)
                ChipBox(onClick = { priorityMenu = true }) {
                    Icon(
                        painterResource(if (p == TaskPriority.NONE) R.drawable.ic_flag else R.drawable.ic_flag_fill),
                        null,
                        tint = if (p == TaskPriority.NONE) MaterialTheme.colorScheme.onSurfaceVariant else priorityColor(p),
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(priorityLabel(p))
                    Icon(painterResource(R.drawable.ic_expand), null)
                }
                PriorityMenu(priorityMenu, { priorityMenu = false }, { pr -> viewModel.update { it.copy(priority = pr.level) } })
            }
        }
        HorizontalDivider()
        if (!form.quit) {
            FormRow(stringResource(R.string.habit_step_label), stringResource(R.string.help_step)) {
                ChipBox(onClick = { stepDialog = true }) {
                    Text("${formatAmount(form.step)} ${unitLabel(form.unit, form.step)}")
                    Icon(painterResource(R.drawable.ic_chevron_right), null)
                }
            }
            HorizontalDivider()
            FormRow(stringResource(R.string.habit_timer_settings), stringResource(R.string.help_timer)) {
                ChipBox(onClick = { timerDialog = true }) {
                    Text(form.timerMinutes?.let { stringResource(R.string.routine_minutes, it) } ?: stringResource(R.string.customize))
                    Icon(painterResource(R.drawable.ic_chevron_right), null)
                }
            }
            HorizontalDivider()
            FormRow(stringResource(R.string.habit_start_label), stringResource(R.string.help_start)) {
                ChipBox(onClick = { pickStart = true }) { Text(form.startDate.format(dateFormatter)) }
            }
            HorizontalDivider()
        }
    }
    Spacer(Modifier.height(24.dp))

    if (colorDialog) {
        AlertDialog(
            onDismissRequest = { colorDialog = false },
            title = { Text(stringResource(R.string.habit_color)) },
            text = { ColorPicker(form.colorKey, { key -> viewModel.update { it.copy(colorKey = key) }; colorDialog = false }) },
            confirmButton = { TextButton(onClick = { colorDialog = false }) { Text(stringResource(R.string.action_close)) } },
        )
    }
    if (typeDialog) {
        AlertDialog(
            onDismissRequest = { typeDialog = false },
            title = { Text(stringResource(R.string.tracking_type)) },
            text = {
                Column {
                    TrackingOption(R.drawable.ic_today, stringResource(R.string.tracking_count), stringResource(R.string.tracking_count_desc)) {
                        viewModel.update { it.copy(quit = false) }; typeDialog = false
                    }
                    TrackingOption(R.drawable.ic_block, stringResource(R.string.tracking_quit), stringResource(R.string.tracking_quit_desc)) {
                        viewModel.update { it.copy(quit = true) }; typeDialog = false
                    }
                }
            },
            confirmButton = { TextButton(onClick = { typeDialog = false }) { Text(stringResource(R.string.action_close)) } },
        )
    }
    if (customUnit) {
        TextInputDialog(stringResource(R.string.unit_custom), stringResource(R.string.habit_unit), "", { u -> viewModel.update { it.copy(unit = u.trim().take(16)) } }, { customUnit = false })
    }
    if (stepDialog) {
        TextInputDialog(
            stringResource(R.string.habit_step_label),
            unitLabel(form.unit, 2.0),
            formatAmount(form.step),
            { v -> v.toPositiveDouble()?.let { s -> viewModel.update { it.copy(step = s) } } },
            { stepDialog = false },
            keyboardType = KeyboardType.Decimal,
        )
    }
    if (timerDialog) {
        TextInputDialog(
            stringResource(R.string.habit_timer_settings),
            stringResource(R.string.unit_minutes),
            form.timerMinutes?.toString() ?: "25",
            { v -> viewModel.update { it.copy(timerMinutes = v.filter(Char::isDigit).take(3).toIntOrNull()?.takeIf { m -> m > 0 }) } },
            { timerDialog = false },
            keyboardType = KeyboardType.Number,
        )
    }
    if (pickStart) LocalDatePickerDialog(form.startDate, { d -> viewModel.update { it.copy(startDate = d) } }, { pickStart = false })
    if (pickQuitTime) {
        LocalTimePickerDialog(LocalTime.now(), DateFormat.is24HourFormat(context), { t ->
            viewModel.update { it.copy(quitStartedAt = it.startDate.atTime(t).atZone(java.time.ZoneId.systemDefault()).toInstant()) }
        }, { pickQuitTime = false })
    }
    if (pickReminder) {
        LocalTimePickerDialog(LocalTime.of(9, 0), DateFormat.is24HourFormat(context), { t -> viewModel.addReminder(t.hour * 60 + t.minute) }, { pickReminder = false })
    }
    editReminder?.let { r ->
        LocalTimePickerDialog(
            LocalTime.of(r.minuteOfDay / 60, r.minuteOfDay % 60),
            DateFormat.is24HourFormat(context),
            { t -> viewModel.updateReminder(r.copy(minuteOfDay = t.hour * 60 + t.minute)) },
            { editReminder = null },
        )
    }
}

@Composable
private fun RadioLine(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun Stepper(value: Double, onChange: (Double) -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest).clickable { if (value > 1) onChange(value - 1) },
            contentAlignment = Alignment.Center,
        ) { Icon(painterResource(R.drawable.ic_expand), stringResource(R.string.action_decrement)) }
        var editing by remember { mutableStateOf(false) }
        Text(
            formatAmount(value),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(72.dp).clickable { editing = true }.testTag("habit_target_value"),
        )
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest).clickable { onChange(value + 1) }.testTag("habit_target_plus"),
            contentAlignment = Alignment.Center,
        ) { Icon(painterResource(R.drawable.ic_collapse), stringResource(R.string.action_increment)) }
        if (editing) {
            TextInputDialog(
                stringResource(R.string.habit_target),
                stringResource(R.string.habit_target),
                formatAmount(value),
                { v -> v.toPositiveDouble()?.let(onChange) },
                { editing = false },
                keyboardType = KeyboardType.Decimal,
            )
        }
    }
}

@Composable
fun TrackingOption(icon: Int, title: String, description: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), null, Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Text(description, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}

@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    supporting: String? = null,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(80) },
                label = { Text(label) },
                supportingText = supporting?.let { s -> { Text(s) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.testTag("text_input_dialog_field"),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(text)
                    onDismiss()
                },
                enabled = text.isNotBlank(),
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
