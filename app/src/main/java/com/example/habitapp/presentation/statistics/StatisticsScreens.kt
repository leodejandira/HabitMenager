package com.example.habitapp.presentation.statistics

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.example.habitapp.R
import com.example.habitapp.domain.model.DayProgress
import com.example.habitapp.domain.model.Habit
import com.example.habitapp.domain.model.HabitType
import com.example.habitapp.domain.repository.HabitRepository
import com.example.habitapp.domain.repository.TaskRepository
import com.example.habitapp.domain.repository.UserPreferencesRepository
import com.example.habitapp.domain.usecase.TaskQueries
import com.example.habitapp.domain.util.HabitCalculator
import com.example.habitapp.domain.util.StreakInfo
import com.example.habitapp.domain.util.TimeProvider
import com.example.habitapp.domain.util.resolveFirstDayOfWeek
import com.example.habitapp.presentation.components.BarChart
import com.example.habitapp.presentation.components.BarEntry
import com.example.habitapp.presentation.components.CalendarHeatmap
import com.example.habitapp.presentation.components.EmojiBadge
import com.example.habitapp.presentation.components.FullScreenError
import com.example.habitapp.presentation.components.FullScreenLoading
import com.example.habitapp.presentation.components.SectionHeader
import com.example.habitapp.presentation.components.StatTile
import com.example.habitapp.presentation.components.currentLocale
import com.example.habitapp.presentation.components.habitValueText
import com.example.habitapp.presentation.components.shortDayName
import com.example.habitapp.presentation.components.streakText
import com.example.habitapp.presentation.navigation.HabitReportRoute
import com.example.habitapp.presentation.theme.HabitColor
import com.example.habitapp.presentation.theme.HabitTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

data class HabitStatRow(val habit: Habit, val rate: Float, val streak: StreakInfo, val completions: Int)

sealed interface StatisticsUiState {
    data object Loading : StatisticsUiState
    data object Error : StatisticsUiState
    data class Ready(
        val today: LocalDate,
        val firstDayOfWeek: DayOfWeek,
        val todayRate: Float?,
        val weekRate: Float?,
        val monthRate: Float?,
        val totalHabitCompletions: Int,
        val totalTasksCompleted: Int,
        val bestCurrentStreak: HabitStatRow?,
        val longestStreak: HabitStatRow?,
        val dailyRates: Map<LocalDate, Float>,
        val weeklyRates: List<Pair<LocalDate, Float>>,
        val rows: List<HabitStatRow>,
    ) : StatisticsUiState
}

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    habitRepository: HabitRepository,
    taskRepository: TaskRepository,
    preferences: UserPreferencesRepository,
    private val time: TimeProvider,
) : ViewModel() {

    val state: StateFlow<StatisticsUiState> = combine(
        habitRepository.observeHabits(),
        habitRepository.observeAllActivities(),
        taskRepository.observeTaskData(),
        preferences.preferences,
    ) { habits, activities, tasks, prefs ->
        val firstDay = resolveFirstDayOfWeek(prefs.firstDayOfWeek)
        val today = time.today()
        val active = habits.filter { !it.archived }
        val byHabit = activities.groupBy { it.habitId }
        val progress: Map<String, Map<LocalDate, DayProgress>> =
            habits.associate { it.id to HabitCalculator.progressByDay(it, byHabit[it.id].orEmpty()) }

        fun rateOn(date: LocalDate): Float? {
            val counted = active.filter { HabitCalculator.countsTowardDailyProgress(it, progress[it.id].orEmpty(), date, firstDay) }
            if (counted.isEmpty()) return null
            return counted.count { progress[it.id]?.get(date)?.completed == true }.toFloat() / counted.size
        }

        val dailyRates = (0L until HEATMAP_DAYS).mapNotNull { offset ->
            val date = today.minusDays(offset)
            rateOn(date)?.let { date to it }
        }.toMap()

        fun average(days: Long): Float? = (0L until days).mapNotNull { dailyRates[today.minusDays(it)] }
            .takeIf { it.isNotEmpty() }?.average()?.toFloat()

        val currentWeek = today.with(TemporalAdjusters.previousOrSame(firstDay))
        val weekly = (11 downTo 0).map { w ->
            val start = currentWeek.minusWeeks(w.toLong())
            val values = (0L until 7L).mapNotNull { dailyRates[start.plusDays(it)] }
            start to (if (values.isEmpty()) 0f else values.average().toFloat())
        }

        val rows = active.sortedBy { it.sortOrder }.filter { it.type != HabitType.QUIT }.map { habit ->
            val p = progress[habit.id].orEmpty()
            HabitStatRow(
                habit = habit,
                rate = HabitCalculator.completionRate(habit, p, today, firstDay),
                streak = HabitCalculator.streak(habit, p, today, firstDay),
                completions = p.values.count { it.completed },
            )
        }
        StatisticsUiState.Ready(
            today = today,
            firstDayOfWeek = firstDay,
            todayRate = rateOn(today),
            weekRate = average(7),
            monthRate = average(30),
            totalHabitCompletions = progress.values.sumOf { map -> map.values.count { it.completed } },
            totalTasksCompleted = TaskQueries.completedHistory(tasks).size,
            bestCurrentStreak = rows.maxByOrNull { it.streak.current }?.takeIf { it.streak.current > 0 },
            longestStreak = rows.maxByOrNull { it.streak.longest }?.takeIf { it.streak.longest > 0 },
            dailyRates = dailyRates,
            weeklyRates = weekly,
            rows = rows,
        ) as StatisticsUiState
    }
        .catch { error ->
            Log.e("StatisticsViewModel", "Failed to compute statistics", error)
            emit(StatisticsUiState.Error)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatisticsUiState.Loading)

    private companion object {
        const val HEATMAP_DAYS = 371L
    }
}

private fun Float?.percent(): String = this?.let { "${(it * 100).toInt()}%" } ?: "—"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StatisticsScreen(onBack: () -> Unit, onOpenReport: (String) -> Unit, viewModel: StatisticsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val locale = currentLocale()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.statistics_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back)) } },
            )
        },
    ) { padding ->
        when (val s = state) {
            StatisticsUiState.Loading -> FullScreenLoading(Modifier.padding(padding))
            StatisticsUiState.Error -> FullScreenError(stringResource(R.string.error_loading), Modifier.padding(padding))
            is StatisticsUiState.Ready -> LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 32.dp)) {
                item(key = "overview") {
                    Card(
                        Modifier.fillMaxWidth().padding(horizontal = HabitTheme.spacing.screenHorizontal, vertical = HabitTheme.spacing.sm),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
                        FlowRow(Modifier.padding(HabitTheme.spacing.lg), horizontalArrangement = Arrangement.spacedBy(HabitTheme.spacing.xl), verticalArrangement = Arrangement.spacedBy(HabitTheme.spacing.md)) {
                            StatTile(s.todayRate.percent(), stringResource(R.string.stat_today_rate))
                            StatTile(s.weekRate.percent(), stringResource(R.string.stat_week_rate))
                            StatTile(s.monthRate.percent(), stringResource(R.string.stat_month_rate))
                            StatTile(s.totalHabitCompletions.toString(), stringResource(R.string.stat_habit_completions))
                            StatTile(s.totalTasksCompleted.toString(), stringResource(R.string.stat_tasks_completed))
                            StatTile(
                                s.bestCurrentStreak?.let { "${it.habit.emoji} " + streakText(it.streak) } ?: "—",
                                stringResource(R.string.stat_best_current_streak),
                            )
                            StatTile(
                                s.longestStreak?.let { "${it.habit.emoji} " + streakText(it.streak, it.streak.longest) } ?: "—",
                                stringResource(R.string.stat_longest_streak),
                            )
                        }
                    }
                }
                item(key = "week") {
                    SectionHeader(stringResource(R.string.chart_this_week))
                    BarChart(
                        entries = (6 downTo 0).map { o ->
                            val d = s.today.minusDays(o.toLong())
                            BarEntry(shortDayName(d, locale).take(3), s.dailyRates[d] ?: 0f)
                        },
                        color = MaterialTheme.colorScheme.primary,
                        maxValue = 1f,
                        modifier = Modifier.padding(horizontal = HabitTheme.spacing.screenHorizontal),
                        description = stringResource(R.string.chart_this_week),
                    )
                }
                item(key = "weeks") {
                    SectionHeader(stringResource(R.string.chart_12_weeks))
                    BarChart(
                        entries = s.weeklyRates.map { (start, rate) -> BarEntry(start.dayOfMonth.toString(), rate) },
                        color = MaterialTheme.colorScheme.tertiary,
                        maxValue = 1f,
                        modifier = Modifier.padding(horizontal = HabitTheme.spacing.screenHorizontal),
                        description = stringResource(R.string.chart_12_weeks),
                    )
                }
                item(key = "year") {
                    SectionHeader(stringResource(R.string.chart_year))
                    CalendarHeatmap(
                        endDate = s.today,
                        weeks = 53,
                        firstDayOfWeek = s.firstDayOfWeek,
                        color = MaterialTheme.colorScheme.primary,
                        intensity = { s.dailyRates[it] ?: 0f },
                        modifier = Modifier.padding(horizontal = HabitTheme.spacing.screenHorizontal),
                        description = stringResource(R.string.chart_year),
                    )
                }
                item(key = "perHabit") { SectionHeader(stringResource(R.string.stat_per_habit)) }
                items(s.rows, key = { it.habit.id }) { row ->
                    ListItem(
                        leadingContent = { EmojiBadge(row.habit.emoji, HabitColor.fromKey(row.habit.colorKey).resolve(), 40) },
                        headlineContent = { Text(row.habit.name) },
                        supportingContent = {
                            Text(
                                listOf(
                                    stringResource(R.string.completion_rate_short, (row.rate * 100).toInt()),
                                    "🔥 " + streakText(row.streak),
                                    stringResource(R.string.stat_completions_count, row.completions),
                                ).joinToString(" · "),
                            )
                        },
                        modifier = Modifier.clickable { onOpenReport(row.habit.id) },
                    )
                }
            }
        }
    }
}

enum class ReportPeriod(val days: Int) { WEEK(7), MONTH(30), QUARTER(90), YEAR(365) }

data class HabitReport(
    val habit: Habit,
    val period: ReportPeriod,
    val today: LocalDate,
    val bars: List<Pair<LocalDate, Float>>,
    val rate: Float,
    val completions: Int,
    val total: Double,
    val average: Double,
    val best: Double,
    val streak: StreakInfo,
)

@HiltViewModel
class HabitReportViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    habitRepository: HabitRepository,
    preferences: UserPreferencesRepository,
    private val time: TimeProvider,
) : ViewModel() {
    private val habitId = savedStateHandle.toRoute<HabitReportRoute>().habitId
    private val period = savedStateHandle.getStateFlow(KEY_PERIOD, ReportPeriod.MONTH.name)

    val report: StateFlow<HabitReport?> = combine(
        habitRepository.observeHabit(habitId),
        habitRepository.observeActivitiesForHabit(habitId),
        preferences.preferences,
        period,
    ) { habit, activities, prefs, periodName ->
        habit ?: return@combine null
        val firstDay = resolveFirstDayOfWeek(prefs.firstDayOfWeek)
        val selected = ReportPeriod.valueOf(periodName)
        val today = time.today()
        val from = today.minusDays(selected.days - 1L)
        val progress = HabitCalculator.progressByDay(habit, activities)
        val inPeriod = progress.filterKeys { !it.isBefore(from) && !it.isAfter(today) }
        fun valueOf(p: DayProgress?): Float = when {
            p == null -> 0f
            habit.type.isMeasurable -> p.total.toFloat()
            p.completed -> 1f
            else -> 0f
        }
        val bars = if (selected.days <= 30) {
            (0 until selected.days).map { from.plusDays(it.toLong()) }.map { it to valueOf(inPeriod[it]) }
        } else {
            val weekStart = from.with(TemporalAdjusters.previousOrSame(firstDay))
            generateSequence(weekStart) { it.plusWeeks(1) }.takeWhile { !it.isAfter(today) }.map { start ->
                start to (0L until 7L).sumOf { valueOf(inPeriod[start.plusDays(it)]).toDouble() }.toFloat()
            }.toList()
        }
        var scheduled = 0
        var done = 0
        var d = maxOf(from, habit.startDate)
        while (!d.isAfter(today)) {
            if (HabitCalculator.isScheduledOn(habit, d) && inPeriod[d]?.skipped != true && (d != today || inPeriod[d]?.completed == true)) {
                scheduled++
                if (inPeriod[d]?.completed == true) done++
            }
            d = d.plusDays(1)
        }
        val withValues = inPeriod.values.filter { it.entryCount > 0 }
        HabitReport(
            habit = habit,
            period = selected,
            today = today,
            bars = bars,
            rate = if (habit.goalPeriod != com.example.habitapp.domain.model.GoalPeriod.DAY) HabitCalculator.completionRate(habit, progress, today, firstDay) else if (scheduled == 0) 0f else done.toFloat() / scheduled,
            completions = inPeriod.values.count { it.completed },
            total = withValues.sumOf { it.total },
            average = if (withValues.isEmpty()) 0.0 else withValues.sumOf { it.total } / withValues.size,
            best = withValues.maxOfOrNull { it.total } ?: 0.0,
            streak = HabitCalculator.streak(habit, progress, today, firstDay),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setPeriod(value: ReportPeriod) {
        savedStateHandle[KEY_PERIOD] = value.name
    }

    private companion object {
        const val KEY_PERIOD = "period"
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HabitReportScreen(onBack: () -> Unit, viewModel: HabitReportViewModel = hiltViewModel()) {
    val report by viewModel.report.collectAsStateWithLifecycle()
    val locale = currentLocale()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(report?.habit?.let { "${it.emoji} ${it.name}" } ?: stringResource(R.string.habit_report), maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back)) } },
            )
        },
    ) { padding ->
        val r = report
        if (r == null) {
            FullScreenLoading(Modifier.padding(padding))
            return@Scaffold
        }
        val habit = r.habit
        val accent = HabitColor.fromKey(habit.colorKey).resolve()
        Column(Modifier.padding(padding)) {
            LazyRow(contentPadding = PaddingValues(horizontal = HabitTheme.spacing.screenHorizontal), horizontalArrangement = Arrangement.spacedBy(HabitTheme.spacing.sm)) {
                items(ReportPeriod.entries) { p ->
                    FilterChip(
                        selected = r.period == p,
                        onClick = { viewModel.setPeriod(p) },
                        label = {
                            Text(
                                stringResource(
                                    when (p) {
                                        ReportPeriod.WEEK -> R.string.range_7_days
                                        ReportPeriod.MONTH -> R.string.range_30_days
                                        ReportPeriod.QUARTER -> R.string.range_90_days
                                        ReportPeriod.YEAR -> R.string.range_365_days
                                    },
                                ),
                            )
                        },
                    )
                }
            }
            FlowRow(
                Modifier.padding(HabitTheme.spacing.screenHorizontal),
                horizontalArrangement = Arrangement.spacedBy(HabitTheme.spacing.xl),
                verticalArrangement = Arrangement.spacedBy(HabitTheme.spacing.md),
            ) {
                StatTile("${(r.rate * 100).toInt()}%", stringResource(R.string.stat_completion_rate))
                StatTile(r.completions.toString(), stringResource(R.string.stat_total_completions))
                StatTile(streakText(r.streak), stringResource(R.string.stat_current_streak))
                if (habit.type.isMeasurable) {
                    StatTile(habitValueText(habit, r.total), stringResource(R.string.stat_total))
                    StatTile(habitValueText(habit, r.average), stringResource(R.string.stat_average))
                    StatTile(habitValueText(habit, r.best), stringResource(R.string.stat_best_day))
                }
            }
            SectionHeader(stringResource(if (r.period.days <= 30) R.string.report_daily else R.string.report_weekly))
            BarChart(
                entries = r.bars.mapIndexed { index, (date, value) ->
                    val label = when {
                        r.period.days == 7 -> shortDayName(date, locale).take(3)
                        r.bars.size <= 16 || index % (r.bars.size / 8 + 1) == 0 -> date.dayOfMonth.toString()
                        else -> ""
                    }
                    BarEntry(label, value)
                },
                color = accent,
                targetLine = if (habit.type.isMeasurable && r.period.days <= 30) habit.target.toFloat() else null,
                maxValue = if (!habit.type.isMeasurable && r.period.days <= 30) 1f else null,
                modifier = Modifier.padding(horizontal = HabitTheme.spacing.screenHorizontal),
                chartHeight = 180.dp,
                description = stringResource(R.string.habit_report),
            )
        }
    }
}
