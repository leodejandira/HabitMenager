package com.example.habitapp.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.habitapp.R
import com.example.habitapp.presentation.theme.HabitColor
import com.example.habitapp.presentation.theme.HabitTheme
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) {
                Text(
                    confirmLabel,
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Material date picker working in local dates (the picker itself uses UTC millis). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalDatePickerDialog(
    initial: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { millis ->
                    onConfirm(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                }
                onDismiss()
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalTimePickerDialog(
    initial: LocalTime,
    is24Hour: Boolean,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = is24Hour)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onConfirm(LocalTime.of(state.hour, state.minute))
                onDismiss()
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        text = { TimePicker(state = state) },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPicker(selectedKey: String, onSelected: (String) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(HabitTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(HabitTheme.spacing.sm),
    ) {
        HabitColor.entries.forEach { color ->
            val selected = color.key == selectedKey
            val label = color.key
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color.resolve())
                    .border(
                        width = if (selected) 3.dp else 0.dp,
                        color = if (selected) MaterialTheme.colorScheme.onSurface else color.resolve(),
                        shape = CircleShape,
                    )
                    .clickable { onSelected(color.key) }
                    .semantics {
                        role = Role.RadioButton
                        contentDescription = label
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

val EmojiChoices = listOf(
    "✅", "💧", "📚", "🏃", "🧘", "💪", "🥗", "😴", "🦷", "💊", "🚶", "🚴",
    "🏊", "🧠", "✍️", "🎸", "🎨", "💻", "📖", "🗣️", "🌱", "☀️", "🌙", "🧹",
    "🧺", "🍎", "🥦", "☕", "🚭", "🍷", "📵", "🍔", "💰", "📈", "🙏", "❤️",
    "👨‍👩‍👧", "📞", "🐶", "🌳", "🎯", "⏰", "🧴", "🛏️", "🚿", "🎧", "📝", "⭐",
)

@Composable
fun EmojiPickerDialog(onSelected: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.habit_choose_icon)) },
        text = {
            LazyVerticalGrid(columns = GridCells.Adaptive(48.dp), modifier = Modifier.heightIn(max = 360.dp)) {
                items(EmojiChoices) { emoji ->
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable {
                                onSelected(emoji)
                                onDismiss()
                            },
                        contentAlignment = Alignment.Center,
                    ) { Text(emoji, fontSize = 24.sp) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeekdayChips(
    selected: Set<DayOfWeek>,
    firstDayOfWeek: DayOfWeek,
    onChange: (Set<DayOfWeek>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    val ordered = (0 until 7).map { firstDayOfWeek.plus(it.toLong()) }
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(HabitTheme.spacing.xs)) {
        ordered.forEach { day ->
            val isOn = day in selected
            FilterChip(
                selected = isOn,
                onClick = { onChange(if (isOn) selected - day else selected + day) },
                label = { Text(day.getDisplayName(java.time.format.TextStyle.SHORT, locale)) },
            )
        }
    }
}
