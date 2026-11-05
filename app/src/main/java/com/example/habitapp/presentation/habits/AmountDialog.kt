package com.example.habitapp.presentation.habits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.example.habitapp.R
import com.example.habitapp.domain.model.Habit
import com.example.habitapp.domain.model.HabitType
import com.example.habitapp.presentation.theme.HabitTheme

/** Lets the user log any amount (negative values correct a mistake) with an optional note. */
@Composable
fun AmountDialog(
    habit: Habit,
    onConfirm: (amount: Double, note: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var amount by rememberSaveable { mutableStateOf(formatNumber(habit.step)) }
    var note by rememberSaveable { mutableStateOf("") }
    val parsed = amount.replace(',', '.').trim().toDoubleOrNull()
    val unitLabel = when (habit.type) {
        HabitType.DURATION -> stringResource(R.string.unit_minutes)
        else -> habit.unit
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${habit.emoji} ${habit.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(HabitTheme.spacing.sm)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.take(10) },
                    label = { Text(stringResource(R.string.amount_label, unitLabel)) },
                    supportingText = { Text(stringResource(R.string.amount_negative_hint)) },
                    singleLine = true,
                    isError = parsed == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.testTag("amount_field"),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(500) },
                    label = { Text(stringResource(R.string.note_optional)) },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    parsed?.let { onConfirm(it, note.ifBlank { null }) }
                    onDismiss()
                },
                enabled = parsed != null && (parsed != 0.0 || note.isNotBlank()),
            ) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
