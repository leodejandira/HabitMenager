package com.example.habitapp.presentation.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.habitapp.R
import com.example.habitapp.domain.model.DayProgress
import com.example.habitapp.domain.model.HabitDisplay
import com.example.habitapp.domain.model.HabitType
import com.example.habitapp.domain.model.TaskList
import com.example.habitapp.domain.model.TaskOccurrence
import com.example.habitapp.domain.usecase.HabitDayItem
import com.example.habitapp.presentation.components.ListBadge
import com.example.habitapp.presentation.components.TaskCardIconShape
import com.example.habitapp.presentation.components.currentLocale
import com.example.habitapp.presentation.components.habitGoalText
import com.example.habitapp.presentation.components.priorityColor
import com.example.habitapp.presentation.theme.HabitColor
import com.example.habitapp.presentation.theme.OnPastel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs

val CardShape = RoundedCornerShape(12.dp)

/** The rounded dark/light tile used for icon buttons in the top bar. */
@Composable
fun TileIconButton(icon: Int, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) { Icon(painterResource(icon), null, modifier = Modifier.size(24.dp)) }
}

@Composable
fun HomeTopBar(title: String, onMenu: () -> Unit, onReports: () -> Unit, onTimer: () -> Unit, onTitleClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Box(
                Modifier.size(44.dp).clickable(onClick = onMenu).semantics { contentDescription = "menu"; role = Role.Button }.testTag("home_menu"),
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(R.drawable.ic_menu), stringResource(R.string.home_open_menu)) }
            Box(
                Modifier.size(44.dp).clickable(onClick = onReports).testTag("home_reports"),
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(R.drawable.ic_chart), stringResource(R.string.reports_title)) }
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onTitleClick)
                .padding(horizontal = 8.dp),
        )
        TileIconButton(R.drawable.ic_timer, stringResource(R.string.timer_title), onTimer, Modifier.testTag("home_timer"))
    }
}

/** Seven day tiles; swipe sideways to move between weeks. */
@Composable
fun WeekStrip(days: List<LocalDate>, selected: LocalDate, today: LocalDate, onSelect: (LocalDate) -> Unit, onShiftWeek: (Long) -> Unit) {
    val locale = currentLocale()
    var drag by remember { mutableFloatStateOf(0f) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (abs(drag) > 120f) onShiftWeek(if (drag < 0) 1 else -1)
                        drag = 0f
                    },
                    onDragCancel = { drag = 0f },
                ) { change, amount ->
                    change.consume()
                    drag += amount
                }
            },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        days.forEach { day ->
            val isSelected = day == selected
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .then(if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(10.dp)) else Modifier)
                    .clickable { onSelect(day) }
                    .padding(vertical = 6.dp)
                    .testTag("week_day_${day.dayOfMonth}"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val dim = if (isSelected || day == today) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                Text(
                    day.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).lowercase(locale).trimEnd('.'),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (day == today) MaterialTheme.colorScheme.secondary else dim,
                )
                Text(
                    day.dayOfMonth.toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = dim,
                )
            }
        }
    }
}

@Composable
fun CountRing(done: Int, total: Int) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(28.dp)) {
        CircularProgressIndicator(
            progress = { if (total == 0) 0f else done.toFloat() / total },
            modifier = Modifier.size(26.dp),
            strokeWidth = 2.5.dp,
            color = MaterialTheme.colorScheme.secondary,
            trackColor = MaterialTheme.colorScheme.outline,
        )
    }
}

/** Collapsible section header with icon, title, counter and progress ring. */
@Composable
fun SectionHeaderRow(
    icon: Int,
    title: String,
    count: Int,
    collapsed: Boolean,
    onToggle: () -> Unit,
    ring: Pair<Int, Int>? = null,
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        if (trailing != null) {
            trailing()
        } else {
            Text(count.toString(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            if (ring != null) {
                Box(contentAlignment = Alignment.Center) {
                    CountRing(ring.first, ring.second)
                    Icon(painterResource(if (collapsed) R.drawable.ic_expand else R.drawable.ic_collapse), null, modifier = Modifier.size(16.dp))
                }
            } else {
                Icon(painterResource(if (collapsed) R.drawable.ic_chevron_right else R.drawable.ic_expand), null)
            }
        }
    }
}

/** A task card: list badge, title, date/notes hints and a square check box. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskCard(
    occurrence: TaskOccurrence,
    list: TaskList?,
    today: LocalDate,
    showDate: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val task = occurrence.task
    val locale = currentLocale()
    val dateLabel = occurrence.date?.let { date ->
        when (date) {
            today -> stringResource(R.string.date_today)
            today.plusDays(1) -> stringResource(R.string.date_tomorrow)
            today.minusDays(1) -> stringResource(R.string.date_yesterday)
            else -> date.format(DateTimeFormatter.ofPattern("d MMM", locale)).trimEnd('.')
        }
    }
    val overdue = !occurrence.completed && occurrence.date?.isBefore(today) == true
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CardShape)
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress)
            .padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)
            .testTag("task_row_${task.title}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ListBadge(list?.icon, task.colorKey ?: list?.colorKey, size = 32.dp, shape = TaskCardIconShape)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (occurrence.completed) TextDecoration.LineThrough else null,
                color = if (occurrence.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            if (task.priority.level > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Icon(painterResource(R.drawable.ic_flag_fill), null, tint = priorityColor(task.priority), modifier = Modifier.size(14.dp))
                }
            }
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(horizontal = 8.dp)) {
            val timeText = task.dueTime?.let { "%02d:%02d".format(it.hour, it.minute) }
            if (showDate && dateLabel != null) {
                Text(dateLabel, style = MaterialTheme.typography.bodyMedium, color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (timeText != null) Text(timeText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row {
                if (task.recurrence.isRecurring) Icon(painterResource(R.drawable.ic_repeat), null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                if (task.description.isNotBlank()) Icon(painterResource(R.drawable.ic_note), null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                if (task.reminderMinutesBefore != null) Icon(painterResource(R.drawable.ic_bell), null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SquareCheck(occurrence.completed, onToggle, task.title)
    }
}

@Composable
fun SquareCheck(checked: Boolean, onClick: () -> Unit, label: String) {
    val bg by animateColorAsState(if (checked) MaterialTheme.colorScheme.secondary else Color.Transparent, label = "check")
    val doneText = stringResource(R.string.habit_done)
    val notDoneText = stringResource(R.string.habit_not_done)
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(2.dp, if (checked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Checkbox
                contentDescription = label
                stateDescription = if (checked) doneText else notDoneText
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_check),
            null,
            tint = if (checked) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(24.dp),
        )
    }
}

/** Habit name inside a pastel pill. */
@Composable
fun HabitPill(name: String, colorKey: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(HabitColor.fromKey(colorKey).color)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            name,
            color = OnPastel,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun StreakBadge(count: Int) {
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(count.toString(), color = Color.White, style = MaterialTheme.typography.labelLarge)
        Icon(
            painterResource(R.drawable.ic_fire),
            stringResource(R.string.stat_current_streak),
            tint = if (count > 0) HabitColor.CORAL.color else Color(0xFF6E6E6E),
            modifier = Modifier.size(16.dp),
        )
    }
}

/** The dashed "+" box (or filled check when done). Tap adds one step, long-press asks for an amount. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlusBox(done: Boolean, colorKey: String, onClick: () -> Unit, onLongClick: () -> Unit, description: String) {
    val outline = MaterialTheme.colorScheme.outline
    val fill = HabitColor.fromKey(colorKey).color
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (done) fill else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics { contentDescription = description; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        if (!done) {
            Canvas(Modifier.size(40.dp)) {
                drawRoundRect(
                    color = outline,
                    style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
                    cornerRadius = CornerRadius(8.dp.toPx()),
                )
            }
            Icon(painterResource(R.drawable.ic_add), null)
        } else {
            Icon(painterResource(R.drawable.ic_check), null, tint = OnPastel)
        }
    }
}

class HabitRowActions(
    val onOpen: (String) -> Unit,
    val onPlus: (HabitDayItem) -> Unit,
    val onPlusLong: (HabitDayItem) -> Unit,
    val onDayTap: (HabitDayItem, LocalDate) -> Unit,
)

@Composable
fun HabitRow(
    item: HabitDayItem,
    display: HabitDisplay,
    weekDays: List<LocalDate>,
    selected: LocalDate,
    today: LocalDate,
    firstDayOfWeek: DayOfWeek,
    actions: HabitRowActions,
    modifier: Modifier = Modifier,
) {
    val habit = item.habit
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CardShape)
            .clickable { actions.onOpen(habit.id) }
            .padding(10.dp)
            .testTag("habit_card_${habit.name}"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HabitPill(habit.name, habit.colorKey, Modifier.widthIn(max = 200.dp))
            Spacer(Modifier.width(8.dp))
            if (habit.type != HabitType.QUIT) StreakBadge(item.streak.current)
            Spacer(Modifier.weight(1f))
            Text(
                habitStatusText(item),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            if (habit.type == HabitType.QUIT) {
                Icon(painterResource(R.drawable.ic_chevron_right), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                PlusBox(
                    done = item.done && item.period.total > 0,
                    colorKey = habit.colorKey,
                    onClick = { actions.onPlus(item) },
                    onLongClick = { actions.onPlusLong(item) },
                    description = stringResource(R.string.habit_add_progress, habit.name),
                )
            }
        }
        when (display) {
            HabitDisplay.COMPACT -> Unit
            HabitDisplay.WEEKLY -> WeekCells(item, weekDays, today, actions)
            HabitDisplay.MONTHLY -> MonthCells(item, selected, today, firstDayOfWeek, actions)
            HabitDisplay.YEARLY -> YearCells(item, today, firstDayOfWeek)
        }
    }
}

@Composable
private fun habitStatusText(item: HabitDayItem): String {
    val habit = item.habit
    if (habit.type == HabitType.QUIT) {
        val elapsed = item.quit?.elapsed ?: return ""
        val days = elapsed.toDays()
        return if (days > 0) pluralStringResource(R.plurals.days_count, days.toInt(), days.toInt()) else "${elapsed.toHours()}h ${elapsed.toMinutes() % 60}m"
    }
    return habitGoalText(habit, item.period.total, item.period.target)
}

private fun cellFraction(item: HabitDayItem, date: LocalDate): Float {
    val p: DayProgress = item.history[date] ?: return 0f
    if (p.skipped) return 0f
    return if (item.habit.goalType == com.example.habitapp.domain.model.GoalType.AT_MOST) {
        if (p.completed) 1f else 0.25f
    } else {
        p.fraction
    }
}

@Composable
private fun DayCell(fraction: Float, colorKey: String, label: String?, isToday: Boolean, modifier: Modifier, onClick: (() -> Unit)?) {
    val color = HabitColor.fromKey(colorKey).color
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    val animated by animateFloatAsState(fraction, label = "cell")
    Box(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(if (animated <= 0f) empty else color.copy(alpha = 0.35f + 0.65f * animated))
            .then(if (isToday) Modifier.border(1.5.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(6.dp)) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (label != null) {
            Text(label, fontSize = 11.sp, color = if (animated > 0.5f) OnPastel else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WeekCells(item: HabitDayItem, days: List<LocalDate>, today: LocalDate, actions: HabitRowActions) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        days.forEach { date ->
            DayCell(
                fraction = cellFraction(item, date),
                colorKey = item.habit.colorKey,
                label = date.dayOfMonth.toString(),
                isToday = date == today,
                modifier = Modifier.weight(1f),
                onClick = if (date.isAfter(today) || item.habit.type == HabitType.QUIT) null else ({ actions.onDayTap(item, date) }),
            )
        }
    }
}

@Composable
private fun MonthCells(item: HabitDayItem, selected: LocalDate, today: LocalDate, firstDay: DayOfWeek, actions: HabitRowActions) {
    val first = selected.withDayOfMonth(1)
    val start = first.with(TemporalAdjusters.previousOrSame(firstDay))
    val weeks = ((first.lengthOfMonth() + (first.toEpochDay() - start.toEpochDay()).toInt()) + 6) / 7
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(weeks) { w ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(7) { d ->
                    val date = start.plusDays((w * 7 + d).toLong())
                    if (date.month != first.month) {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        DayCell(
                            fraction = cellFraction(item, date),
                            colorKey = item.habit.colorKey,
                            label = date.dayOfMonth.toString(),
                            isToday = date == today,
                            modifier = Modifier.weight(1f),
                            onClick = if (date.isAfter(today) || item.habit.type == HabitType.QUIT) null else ({ actions.onDayTap(item, date) }),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun YearCells(item: HabitDayItem, today: LocalDate, firstDay: DayOfWeek) {
    val weeks = 26
    val end = today.with(TemporalAdjusters.previousOrSame(firstDay))
    val start = end.minusWeeks((weeks - 1).toLong())
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(weeks) { w ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(7) { d ->
                    val date = start.plusWeeks(w.toLong()).plusDays(d.toLong())
                    if (date.isAfter(today)) {
                        Spacer(Modifier.fillMaxWidth().aspectRatio(1f))
                    } else {
                        val f = cellFraction(item, date)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (f <= 0f) MaterialTheme.colorScheme.surfaceContainerHighest else HabitColor.fromKey(item.habit.colorKey).color.copy(alpha = 0.35f + 0.65f * f)),
                        )
                    }
                }
            }
        }
    }
}

/** Card shown in the FAB menu ("Task" / "Habit"). */
@Composable
fun FabChoice(title: String, subtitle: String, icon: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .widthIn(max = 320.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
            }
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
        Icon(painterResource(R.drawable.ic_chevron_right), null)
    }
}

/** A row in the side menu. */
@Composable
fun DrawerRow(
    title: String,
    count: Int?,
    selected: Boolean,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (count != null) Text(count.toString(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun DrawerIcon(icon: Int, color: Color = MaterialTheme.colorScheme.secondary) {
    Box(Modifier.size(32.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), null, tint = OnPastel, modifier = Modifier.size(19.dp))
    }
}

@Composable
fun DrawerDivider(title: String, expanded: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(3.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest))
        Row(
            Modifier
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = onToggle)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Icon(painterResource(if (expanded) R.drawable.ic_expand else R.drawable.ic_chevron_right), null, Modifier.size(20.dp))
        }
        Box(Modifier.weight(1f).height(3.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest))
    }
}
