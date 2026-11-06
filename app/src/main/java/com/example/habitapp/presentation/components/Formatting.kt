package com.example.habitapp.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.example.habitapp.R
import com.example.habitapp.domain.model.Habit
import com.example.habitapp.domain.model.HabitType
import com.example.habitapp.domain.util.StreakInfo
import com.example.habitapp.domain.util.StreakUnit
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** 2.0 -> "2", 2.5 -> "2.5", 1234.0 -> "1234". */
fun formatAmount(value: Double): String {
    val rounded = (value * 100).roundToLong() / 100.0
    return if (abs(rounded - rounded.toLong()) < 1e-9) rounded.toLong().toString() else rounded.toString()
}

/** Minutes as "1h 20m" / "45m". */
fun formatMinutes(minutes: Double): String {
    val total = minutes.roundToLong()
    val h = total / 60
    val m = total % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}

fun formatClock(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

@Composable
fun habitValueText(habit: Habit, value: Double): String = when (habit.type) {
    HabitType.DURATION -> formatMinutes(value)
    HabitType.BOOLEAN, HabitType.QUIT -> formatAmount(value)
    else -> if (habit.unit.isBlank()) formatAmount(value) else "${formatAmount(value)} ${habit.unit}"
}

@Composable
fun habitProgressText(habit: Habit, total: Double, target: Double): String = when (habit.type) {
    HabitType.DURATION -> "${formatMinutes(total)} / ${formatMinutes(target)}"
    else -> {
        val unit = if (habit.unit.isBlank()) "" else " ${habit.unit}"
        "${formatAmount(total)} / ${formatAmount(target)}$unit"
    }
}

@Composable
fun streakText(streak: StreakInfo, count: Int = streak.current): String = when (streak.unit) {
    StreakUnit.DAY -> pluralStringResource(R.plurals.streak_days, count, count)
    StreakUnit.WEEK -> pluralStringResource(R.plurals.streak_weeks, count, count)
    StreakUnit.MONTH -> pluralStringResource(R.plurals.streak_months, count, count)
    StreakUnit.YEAR -> pluralStringResource(R.plurals.streak_years, count, count)
}

@Composable
fun elapsedText(duration: Duration): String {
    val days = duration.toDays()
    val hours = duration.toHours() % 24
    val minutes = duration.toMinutes() % 60
    return stringResource(R.string.elapsed_dhm, days, hours, minutes)
}

@Composable
fun rememberDateFormatter(style: FormatStyle = FormatStyle.MEDIUM): DateTimeFormatter {
    val locale = currentLocale()
    return remember(locale, style) { DateTimeFormatter.ofLocalizedDate(style).withLocale(locale) }
}

@Composable
fun rememberTimeFormatter(): DateTimeFormatter {
    val locale = currentLocale()
    return remember(locale) { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale) }
}

/** "Today", "Yesterday", "Tomorrow" or a medium date. */
@Composable
fun relativeDateText(date: LocalDate, today: LocalDate): String {
    val formatter = rememberDateFormatter()
    return when (date) {
        today -> stringResource(R.string.date_today)
        today.minusDays(1) -> stringResource(R.string.date_yesterday)
        today.plusDays(1) -> stringResource(R.string.date_tomorrow)
        else -> date.format(formatter)
    }
}

fun LocalTime.minuteOfDay(): Int = hour * 60 + minute

fun shortDayName(date: LocalDate, locale: Locale): String =
    date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)

fun narrowDayName(day: java.time.DayOfWeek, locale: Locale): String =
    day.getDisplayName(TextStyle.NARROW, locale)

/** "0 / 15 min", "2 / 5 vezes", "0,5 / 2 litros". */
@Composable
fun habitGoalText(habit: Habit, total: Double, target: Double): String = when (habit.type) {
    HabitType.DURATION -> "${formatAmount(total)} / ${formatAmount(target)} min"
    HabitType.COUNT, HabitType.BOOLEAN -> if (habit.unit.isBlank()) {
        "${formatAmount(total)} / " + pluralStringResource(R.plurals.times_unit, target.toInt().coerceAtLeast(1), formatAmount(target))
    } else {
        "${formatAmount(total)} / ${formatAmount(target)} ${habit.unit}"
    }
    else -> "${formatAmount(total)} / ${formatAmount(target)}" + if (habit.unit.isBlank()) "" else " ${habit.unit}"
}
