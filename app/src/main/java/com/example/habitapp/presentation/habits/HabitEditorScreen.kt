package com.example.habitapp.presentation.habits

import android.Manifest
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.habitapp.R
import com.example.habitapp.domain.model.FrequencyType
import com.example.habitapp.domain.model.HabitReminder
import com.example.habitapp.domain.model.HabitType
import com.example.habitapp.presentation.components.ColorPicker
import com.example.habitapp.presentation.components.EmojiBadge
import com.example.habitapp.presentation.components.EmojiPickerDialog
import com.example.habitapp.presentation.components.FullScreenError
import com.example.habitapp.presentation.components.FullScreenLoading
import com.example.habitapp.presentation.components.LocalDatePickerDialog
import com.example.habitapp.presentation.components.LocalTimePickerDialog
import com.example.habitapp.presentation.components.SectionHeader
import com.example.habitapp.presentation.components.WeekdayChips
import com.example.habitapp.presentation.components.rememberDateFormatter
import com.example.habitapp.presentation.components.rememberTimeFormatter
import com.example.habitapp.presentation.theme.HabitColor
import com.example.habitapp.presentation.theme.HabitTheme
import java.time.LocalTime

@Composable
fun habitTypeLabel(type: HabitType): String = stringResource(
    when (type) {
        HabitType.BOOLEAN -> R.string.habit_type_boolean
        HabitType.NUMERIC -> R.string.habit_type_numeric
        HabitType.DURATION -> R.string.habit_type_duration
        HabitType.COUNT -> R.string.habit_type_count
        HabitType.QUIT -> R.string.habit_type_quit
    },
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HabitEditorScreen(
    onBack: () -> Unit,
    viewModel: HabitEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val editing = state as? HabitEditorUiState.Editing
    LaunchedEffect(editing?.saved) { if (editing?.saved == true) onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (editing?.form?.isNew != false) R.string.habit_new else R.string.habit_edit))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_close), stringResource(R.string.action_close))
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::save, enabled = editing != null, modifier = Modifier.testTag("habit_save")) {
                        Text(stringResource(R.string.action_save))
                    }
                },
            )
        },
    ) { padding ->
        when (val s = state) {
            HabitEditorUiState.Loading -> FullScreenLoading(Modifier.padding(padding))
            HabitEditorUiState.NotFound -> FullScreenError(stringResource(R.string.error_not_found), Modifier.padding(padding))
            is HabitEditorUiState.Editing -> HabitForm(
                state = s,
                modifier = Modifier.padding(padding),
                viewModel = viewModel,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HabitForm(state: HabitEditorUiState.Editing, modifier: Modifier, viewModel: HabitEditorViewModel) {
    val form = state.form
    val spacing = HabitTheme.spacing
    val context = LocalContext.current
    val dateFormatter = rememberDateFormatter()
    val timeFormatter = rememberTimeFormatter()
    var showEmoji by remember { mutableStateOf(false) }
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    var pickReminderTime by remember { mutableStateOf(false) }
    var editReminder by remember { mutableStateOf<HabitReminder?>(null) }
    var newGroupDialog by rememberSaveable { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Column(
        modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = spacing.xxl),
    ) {
        state.errorRes?.let {
            Text(
                stringResource(it),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm),
            )
        }
        Row(
            Modifier.padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.clickable { showEmoji = true }) {
                EmojiBadge(form.emoji, HabitColor.fromKey(form.colorKey).resolve(), size = 56)
            }
            Spacer(Modifier.width(spacing.md))
            OutlinedTextField(
                value = form.name,
                onValueChange = { v -> viewModel.update { it.copy(name = v.take(60)) } },
                label = { Text(stringResource(R.string.habit_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .weight(1f)
                    .testTag("habit_name_field"),
            )
        }
        OutlinedTextField(
            value = form.description,
            onValueChange = { v -> viewModel.update { it.copy(description = v.take(500)) } },
            label = { Text(stringResource(R.string.habit_description)) },
            minLines = 2,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.screenHorizontal),
        )

        SectionHeader(stringResource(R.string.habit_color))
        ColorPicker(
            selectedKey = form.colorKey,
            onSelected = { key -> viewModel.update { it.copy(colorKey = key) } },
            modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
        )

        SectionHeader(stringResource(R.string.habit_type))
        FlowRow(
            Modifier.padding(horizontal = spacing.screenHorizontal),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            HabitType.entries.forEach { type ->
                FilterChip(
                    selected = form.type == type,
                    onClick = { viewModel.update { it.withType(type) } },
                    label = { Text(habitTypeLabel(type)) },
                    enabled = form.isNew || form.type == type || (form.type != HabitType.QUIT && type != HabitType.QUIT),
                )
            }
        }
        Text(
            text = stringResource(
                when (form.type) {
                    HabitType.BOOLEAN -> R.string.habit_type_boolean_help
                    HabitType.NUMERIC -> R.string.habit_type_numeric_help
                    HabitType.DURATION -> R.string.habit_type_duration_help
                    HabitType.COUNT -> R.string.habit_type_count_help
                    HabitType.QUIT -> R.string.habit_type_quit_help
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = spacing.screenHorizontal, vertical = spacing.xs),
        )

        if (form.type.isMeasurable) {
            Row(
                Modifier.padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                OutlinedTextField(
                    value = form.target,
                    onValueChange = { v -> viewModel.update { it.copy(target = v.take(9)) } },
                    label = {
                        Text(stringResource(if (form.type == HabitType.DURATION) R.string.habit_target_minutes else R.string.habit_target))
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f).testTag("habit_target_field"),
                )
                if (form.type != HabitType.DURATION) {
                    OutlinedTextField(
                        value = form.unit,
                        onValueChange = { v -> viewModel.update { it.copy(unit = v.take(16)) } },
                        label = { Text(stringResource(R.string.habit_unit)) },
                        placeholder = { Text(stringResource(R.string.habit_unit_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = form.step,
                    onValueChange = { v -> viewModel.update { it.copy(step = v.take(9)) } },
                    label = { Text(stringResource(R.string.habit_step)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (form.type != HabitType.QUIT) {
            SectionHeader(stringResource(R.string.habit_frequency))
            FlowRow(
                Modifier.padding(horizontal = spacing.screenHorizontal),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                FrequencyType.entries.forEach { type ->
                    FilterChip(
                        selected = form.frequencyType == type,
                        onClick = { viewModel.update { it.copy(frequencyType = type) } },
                        label = { Text(frequencyLabel(type)) },
                    )
                }
            }
            when (form.frequencyType) {
                FrequencyType.WEEKDAYS -> WeekdayChips(
                    selected = form.weekdays,
                    firstDayOfWeek = state.firstDayOfWeek,
                    onChange = { days -> viewModel.update { it.copy(weekdays = days) } },
                    modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                )
                FrequencyType.TIMES_PER_WEEK, FrequencyType.TIMES_PER_MONTH -> OutlinedTextField(
                    value = form.timesPerPeriod,
                    onValueChange = { v -> viewModel.update { it.copy(timesPerPeriod = v.filter(Char::isDigit).take(2)) } },
                    label = {
                        Text(
                            stringResource(
                                if (form.frequencyType == FrequencyType.TIMES_PER_WEEK) R.string.habit_times_per_week else R.string.habit_times_per_month,
                            ),
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm),
                )
                FrequencyType.DAILY -> Unit
            }
        }

        SectionHeader(stringResource(R.string.habit_dates))
        Row(
            Modifier.padding(horizontal = spacing.screenHorizontal),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            OutlinedButton(onClick = { pickStart = true }) {
                Text(stringResource(R.string.habit_start_date, form.startDate.format(dateFormatter)))
            }
            OutlinedButton(onClick = { pickEnd = true }) {
                Text(
                    form.endDate?.let { stringResource(R.string.habit_end_date, it.format(dateFormatter)) }
                        ?: stringResource(R.string.habit_no_end_date),
                )
            }
            if (form.endDate != null) {
                IconButton(onClick = { viewModel.update { it.copy(endDate = null) } }) {
                    Icon(painterResource(R.drawable.ic_close), stringResource(R.string.habit_clear_end_date))
                }
            }
        }

        SectionHeader(stringResource(R.string.habit_group))
        FlowRow(
            Modifier.padding(horizontal = spacing.screenHorizontal),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            FilterChip(
                selected = form.groupId == null,
                onClick = { viewModel.update { it.copy(groupId = null) } },
                label = { Text(stringResource(R.string.habit_no_group)) },
            )
            state.groups.forEach { group ->
                FilterChip(
                    selected = form.groupId == group.id,
                    onClick = { viewModel.update { it.copy(groupId = group.id) } },
                    label = { Text(group.name) },
                )
            }
            FilterChip(
                selected = false,
                onClick = { newGroupDialog = true },
                label = { Text(stringResource(R.string.habit_new_group)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_add), null) },
            )
        }

        if (form.type != HabitType.QUIT) {
            SectionHeader(stringResource(R.string.habit_reminders))
            form.reminders.forEach { reminder ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.screenHorizontal, vertical = spacing.xs),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Row(Modifier.padding(start = spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            LocalTime.of(reminder.minuteOfDay / 60, reminder.minuteOfDay % 60).format(timeFormatter),
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { editReminder = reminder },
                        )
                        IconButton(onClick = { viewModel.removeReminder(reminder.id) }) {
                            Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.action_delete))
                        }
                    }
                    WeekdayChips(
                        selected = reminder.weekdays.ifEmpty { java.time.DayOfWeek.entries.toSet() },
                        firstDayOfWeek = state.firstDayOfWeek,
                        onChange = { days ->
                            val normalized = if (days.size == 7) emptySet() else days
                            if (days.isNotEmpty()) viewModel.updateReminder(reminder.copy(weekdays = normalized))
                        },
                        modifier = Modifier.padding(horizontal = spacing.md),
                    )
                }
            }
            OutlinedButton(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    pickReminderTime = true
                },
                modifier = Modifier.padding(horizontal = spacing.screenHorizontal, vertical = spacing.sm),
            ) {
                Icon(painterResource(R.drawable.ic_add), null)
                Spacer(Modifier.width(spacing.sm))
                Text(stringResource(R.string.habit_add_reminder))
            }
        }
    }

    if (showEmoji) {
        EmojiPickerDialog(onSelected = { e -> viewModel.update { it.copy(emoji = e) } }, onDismiss = { showEmoji = false })
    }
    if (pickStart) {
        LocalDatePickerDialog(form.startDate, { d -> viewModel.update { it.copy(startDate = d) } }, { pickStart = false })
    }
    if (pickEnd) {
        LocalDatePickerDialog(form.endDate ?: form.startDate.plusMonths(1), { d -> viewModel.update { it.copy(endDate = d) } }, { pickEnd = false })
    }
    if (pickReminderTime) {
        LocalTimePickerDialog(
            initial = LocalTime.of(9, 0),
            is24Hour = DateFormat.is24HourFormat(context),
            onConfirm = { t -> viewModel.addReminder(t.hour * 60 + t.minute) },
            onDismiss = { pickReminderTime = false },
        )
    }
    editReminder?.let { reminder ->
        LocalTimePickerDialog(
            initial = LocalTime.of(reminder.minuteOfDay / 60, reminder.minuteOfDay % 60),
            is24Hour = DateFormat.is24HourFormat(context),
            onConfirm = { t -> viewModel.updateReminder(reminder.copy(minuteOfDay = t.hour * 60 + t.minute)) },
            onDismiss = { editReminder = null },
        )
    }
    if (newGroupDialog) {
        TextInputDialog(
            title = stringResource(R.string.habit_new_group),
            label = stringResource(R.string.group_name),
            initial = "",
            onConfirm = viewModel::createGroup,
            onDismiss = { newGroupDialog = false },
        )
    }
}

@Composable
fun frequencyLabel(type: FrequencyType): String = stringResource(
    when (type) {
        FrequencyType.DAILY -> R.string.freq_daily
        FrequencyType.WEEKDAYS -> R.string.freq_weekdays
        FrequencyType.TIMES_PER_WEEK -> R.string.freq_per_week
        FrequencyType.TIMES_PER_MONTH -> R.string.freq_per_month
    },
)

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
