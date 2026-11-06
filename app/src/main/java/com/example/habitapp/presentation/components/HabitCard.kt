package com.example.habitapp.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.habitapp.R
import com.example.habitapp.domain.model.FrequencyType
import com.example.habitapp.domain.model.HabitType
import com.example.habitapp.domain.usecase.HabitDayItem
import com.example.habitapp.presentation.theme.HabitColor
import com.example.habitapp.presentation.theme.HabitTheme

class HabitCardActions(
    val onOpen: (String) -> Unit,
    val onToggle: (String) -> Unit,
    val onIncrement: (String, Double) -> Unit,
    val onCustomAmount: (HabitDayItem) -> Unit,
    val onTimer: (String) -> Unit,
)

@Composable
fun HabitCard(item: HabitDayItem, actions: HabitCardActions, modifier: Modifier = Modifier) {
    val habit = item.habit
    val accent = HabitColor.fromKey(habit.colorKey).resolve()
    val done = item.done
    val container by animateColorAsState(
        if (done) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerLow,
        label = "cardColor",
    )
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("habit_card_${habit.name}"),
        colors = CardDefaults.cardColors(containerColor = container),
        onClick = { actions.onOpen(habit.id) },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = HabitTheme.spacing.md, vertical = HabitTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EmojiBadge(emoji = habit.emoji, color = accent)
            Spacer(Modifier.width(HabitTheme.spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = habit.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle(item),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (habit.type.isMeasurable) {
                    val progress by animateFloatAsState(item.progress.fraction, label = "habitProgress")
                    LinearProgressIndicator(
                        progress = { progress },
                        color = accent,
                        trackColor = accent.copy(alpha = 0.18f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = HabitTheme.spacing.sm)
                            .height(6.dp)
                            .clip(CircleShape),
                    )
                }
            }
            Spacer(Modifier.width(HabitTheme.spacing.sm))
            HabitQuickActions(item, accent, actions)
        }
    }
}

@Composable
private fun subtitle(item: HabitDayItem): String {
    val habit = item.habit
    val parts = mutableListOf<String>()
    when (habit.type) {
        HabitType.QUIT -> item.quit?.let { parts += elapsedText(it.elapsed) }
        HabitType.BOOLEAN -> Unit
        else -> parts += habitProgressText(habit, item.progress.total, item.progress.target)
    }
    if (habit.goalPeriod != com.example.habitapp.domain.model.GoalPeriod.DAY) {
        parts += habitProgressText(habit, item.period.total, item.period.target)
    }
    if (item.streak.current > 0) parts += "🔥 " + streakText(item.streak)
    if (item.progress.skipped) parts += stringResource(R.string.habit_skipped)
    if (parts.isEmpty()) parts += stringResource(if (item.done) R.string.habit_done else R.string.habit_not_done)
    return parts.joinToString(" · ")
}

@Composable
fun EmojiBadge(emoji: String, color: Color, size: Int = 44) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = (size * 0.48f).sp)
    }
}

@Composable
private fun HabitQuickActions(item: HabitDayItem, accent: Color, actions: HabitCardActions) {
    val habit = item.habit
    when (habit.type) {
        HabitType.BOOLEAN -> CheckButton(
            checked = item.done,
            color = accent,
            onClick = { actions.onToggle(habit.id) },
            label = habit.name,
        )
        HabitType.QUIT -> Text(
            text = pluralStringResource(
                R.plurals.days_count,
                (item.quit?.elapsed?.toDays() ?: 0).toInt(),
                (item.quit?.elapsed?.toDays() ?: 0).toInt(),
            ),
            style = MaterialTheme.typography.labelLarge,
            color = accent,
        )
        else -> Row(horizontalArrangement = Arrangement.spacedBy(HabitTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            if (habit.type == HabitType.DURATION) {
                SmallActionButton(R.drawable.ic_timer, stringResource(R.string.action_start_timer), accent) {
                    actions.onTimer(habit.id)
                }
            } else {
                SmallActionButton(R.drawable.ic_remove, stringResource(R.string.action_decrement), accent, enabled = item.progress.total > 0) {
                    actions.onIncrement(habit.id, -habit.step)
                }
            }
            SmallActionButton(R.drawable.ic_add, stringResource(R.string.action_increment), accent) {
                if (habit.type == HabitType.DURATION) actions.onCustomAmount(item) else actions.onIncrement(habit.id, habit.step)
            }
        }
    }
}

@Composable
private fun SmallActionButton(icon: Int, description: String, color: Color, enabled: Boolean = true, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(38.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = color.copy(alpha = 0.16f),
            contentColor = color,
        ),
    ) {
        Icon(painterResource(icon), contentDescription = description, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun CheckButton(checked: Boolean, color: Color, onClick: () -> Unit, label: String, size: Int = 40) {
    val scale by animateFloatAsState(
        targetValue = if (checked) 1f else 0.92f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "checkScale",
    )
    val fill by animateColorAsState(if (checked) color else Color.Transparent, label = "checkFill")
    val stateText = stringResource(if (checked) R.string.habit_done else R.string.habit_not_done)
    Box(
        modifier = Modifier
            .size(size.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(fill)
            .border(2.dp, color, CircleShape)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Checkbox
                contentDescription = label
                stateDescription = stateText
            },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.surface,
                modifier = Modifier.size((size * 0.55f).dp),
            )
        }
    }
}
