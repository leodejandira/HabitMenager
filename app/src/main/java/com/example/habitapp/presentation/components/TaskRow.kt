package com.example.habitapp.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.habitapp.R
import com.example.habitapp.domain.model.TaskOccurrence
import com.example.habitapp.domain.model.TaskPriority
import com.example.habitapp.presentation.theme.HabitColor
import com.example.habitapp.presentation.theme.HabitTheme
import java.time.LocalDate

@Composable
fun priorityColor(priority: TaskPriority): Color = when (priority) {
    TaskPriority.NONE -> Color.Transparent
    TaskPriority.LOW -> HabitColor.OCEAN.resolve()
    TaskPriority.MEDIUM -> HabitColor.AMBER.resolve()
    TaskPriority.HIGH -> HabitColor.CORAL.resolve()
    TaskPriority.URGENT -> MaterialTheme.colorScheme.error
}

@Composable
fun priorityLabel(priority: TaskPriority): String = stringResource(
    when (priority) {
        TaskPriority.NONE -> R.string.priority_none
        TaskPriority.LOW -> R.string.priority_low
        TaskPriority.MEDIUM -> R.string.priority_medium
        TaskPriority.HIGH -> R.string.priority_high
        TaskPriority.URGENT -> R.string.priority_urgent
    },
)

@Composable
fun TaskRow(
    occurrence: TaskOccurrence,
    listName: String?,
    today: LocalDate,
    onToggle: (TaskOccurrence) -> Unit,
    onOpen: (TaskOccurrence) -> Unit,
    modifier: Modifier = Modifier,
    showDate: Boolean = true,
) {
    val task = occurrence.task
    val timeFormatter = rememberTimeFormatter()
    val overdue = !occurrence.completed && occurrence.date != null && occurrence.date.isBefore(today)
    val details = buildList {
        if (showDate && occurrence.date != null) add(relativeDateText(occurrence.date, today))
        task.dueTime?.let { add(it.format(timeFormatter)) }
        if (task.recurrence.isRecurring) add("↻")
        if (!listName.isNullOrBlank()) add(listName)
        if (task.priority != TaskPriority.NONE) add(priorityLabel(task.priority))
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onOpen(occurrence) }
            .padding(end = HabitTheme.spacing.lg)
            .testTag("task_row_${task.title}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .padding(start = HabitTheme.spacing.sm)
                .width(4.dp)
                .height(32.dp)
                .background(
                    task.colorKey?.let { HabitColor.fromKey(it).resolve() } ?: priorityColor(task.priority),
                    RoundedCornerShape(2.dp),
                ),
        )
        Checkbox(checked = occurrence.completed, onCheckedChange = { onToggle(occurrence) })
        Column(Modifier.weight(1f).padding(vertical = HabitTheme.spacing.sm)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (occurrence.completed) TextDecoration.LineThrough else null,
                color = if (occurrence.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (details.isNotEmpty()) {
                Text(
                    text = details.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
