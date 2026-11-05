package com.example.habitapp.widgets

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.habitapp.MainActivity
import com.example.habitapp.R
import com.example.habitapp.domain.model.HabitType
import com.example.habitapp.domain.repository.HabitRepository
import com.example.habitapp.domain.repository.UserPreferencesRepository
import com.example.habitapp.domain.usecase.DayOverview
import com.example.habitapp.domain.usecase.HabitDayItem
import com.example.habitapp.domain.usecase.ObserveDayOverviewUseCase
import com.example.habitapp.domain.util.HabitCalculator
import com.example.habitapp.domain.util.TimeProvider
import com.example.habitapp.domain.util.resolveFirstDayOfWeek
import com.example.habitapp.presentation.components.formatAmount
import com.example.habitapp.presentation.theme.DarkColors
import com.example.habitapp.presentation.theme.LightColors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun observeDayOverview(): ObserveDayOverviewUseCase
    fun habitRepository(): HabitRepository
    fun preferences(): UserPreferencesRepository
    fun timeProvider(): TimeProvider
}

private fun Context.entryPoint(): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java)

private suspend fun loadToday(context: Context): DayOverview? = try {
    val ep = context.entryPoint()
    ep.observeDayOverview()(flowOf(ep.timeProvider().today())).first()
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (error: Exception) {
    Log.e("Widgets", "Failed to load widget data", error)
    null
}

private val WidgetColors = ColorProviders(light = LightColors, dark = DarkColors)

/** Refreshes every Steady widget; called whenever habits or tasks change. */
object WidgetUpdater {
    suspend fun updateAll(context: Context) {
        try {
            TodayHabitsWidget().updateAll(context)
            DailyProgressWidget().updateAll(context)
            HabitSummaryWidget().updateAll(context)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Log.w("Widgets", "Widget update failed", error)
        }
    }
}

// ---------------------------------------------------------------------------
// Today's habits (with quick completion)
// ---------------------------------------------------------------------------

class TodayHabitsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val overview = loadToday(context)
        provideContent {
            GlanceTheme(colors = WidgetColors) { TodayHabitsContent(context, overview) }
        }
    }
}

@Composable
private fun TodayHabitsContent(context: Context, overview: DayOverview?) {
    Column(
        modifier = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.surface).cornerRadius(20.dp).padding(12.dp),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                context.getString(R.string.widget_today_title),
                style = TextStyle(color = GlanceTheme.colors.primary, fontWeight = FontWeight.Bold, fontSize = 15.sp),
                modifier = GlanceModifier.defaultWeight(),
            )
            if (overview != null && overview.totalHabits > 0) {
                Text(
                    "${overview.completedHabits}/${overview.totalHabits}",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                )
            }
        }
        Spacer(GlanceModifier.height(6.dp))
        val habits = overview?.habits.orEmpty()
        when {
            overview == null -> Text(context.getString(R.string.widget_error), style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant))
            habits.isEmpty() -> Text(
                context.getString(R.string.widget_no_habits),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>()),
            )
            else -> LazyColumn {
                items(habits, itemId = { it.habit.id.hashCode().toLong() }) { item -> HabitWidgetRow(item) }
            }
        }
    }
}

@Composable
private fun HabitWidgetRow(item: HabitDayItem) {
    val habit = item.habit
    val done = item.progress.completed
    val status = when {
        habit.type == HabitType.QUIT -> "${item.quit?.elapsed?.toDays() ?: 0}d"
        habit.type == HabitType.BOOLEAN -> if (done) "✓" else "○"
        else -> "${formatAmount(item.progress.total)}/${formatAmount(item.progress.target)}" + if (done) " ✓" else ""
    }
    val clickable = habit.type != HabitType.QUIT
    val base = GlanceModifier.fillMaxWidth().padding(vertical = 6.dp)
    Row(
        modifier = if (clickable) {
            base.clickable(actionRunCallback<QuickLogAction>(actionParametersOf(QuickLogAction.HabitIdKey to habit.id)))
        } else {
            base.clickable(actionStartActivity<MainActivity>())
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(habit.emoji, style = TextStyle(fontSize = 18.sp))
        Spacer(GlanceModifier.width(8.dp))
        Text(
            habit.name,
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
            modifier = GlanceModifier.defaultWeight(),
        )
        Text(
            status,
            style = TextStyle(
                color = if (done) GlanceTheme.colors.primary else GlanceTheme.colors.onSurfaceVariant,
                fontWeight = if (done) FontWeight.Bold else FontWeight.Normal,
                fontSize = 14.sp,
            ),
        )
    }
}

/** Tap on a widget row: toggles yes/no habits, adds one step to measurable habits. */
class QuickLogAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val habitId = parameters[HabitIdKey] ?: return
        val ep = context.entryPoint()
        val repository = ep.habitRepository()
        val habit = repository.getHabit(habitId) ?: return
        val today = ep.timeProvider().today()
        try {
            if (habit.type == HabitType.BOOLEAN) {
                repository.toggleCompletion(habitId, today)
            } else if (habit.type.isMeasurable) {
                repository.addProgress(habitId, today, habit.step)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Log.e("Widgets", "Quick log failed", error)
        }
        WidgetUpdater.updateAll(context)
    }

    companion object {
        val HabitIdKey = ActionParameters.Key<String>("habitId")
    }
}

// ---------------------------------------------------------------------------
// Daily progress
// ---------------------------------------------------------------------------

class DailyProgressWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val overview = loadToday(context)
        provideContent {
            GlanceTheme(colors = WidgetColors) {
                Column(
                    modifier = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.primaryContainer).cornerRadius(20.dp)
                        .padding(14.dp).clickable(actionStartActivity<MainActivity>()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (overview == null) {
                        Text(context.getString(R.string.widget_error), style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer))
                    } else {
                        val percent = (overview.fraction * 100).roundToInt()
                        Text(
                            context.getString(R.string.widget_progress_title),
                            style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = 13.sp),
                        )
                        Text(
                            "$percent%",
                            style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = 34.sp, fontWeight = FontWeight.Bold),
                        )
                        LinearProgressIndicator(
                            progress = overview.fraction,
                            modifier = GlanceModifier.fillMaxWidth().height(8.dp),
                            color = GlanceTheme.colors.primary,
                            backgroundColor = GlanceTheme.colors.surface,
                        )
                        Spacer(GlanceModifier.height(6.dp))
                        Text(
                            context.getString(R.string.progress_habits, overview.completedHabits, overview.totalHabits) + " · " +
                                context.getString(R.string.progress_tasks, overview.completedTasks, overview.totalTasks),
                            style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = 12.sp),
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Habit report summary
// ---------------------------------------------------------------------------

private data class SummaryData(val weekRate: Int?, val bestStreakLabel: String?, val bestStreak: Int, val completionsThisWeek: Int)

private suspend fun loadSummary(context: Context): SummaryData? = try {
    val ep = context.entryPoint()
    val habits = ep.habitRepository().observeHabits().first().filter { !it.archived && it.type != HabitType.QUIT }
    val activities = ep.habitRepository().observeAllActivities().first()
    val firstDay = resolveFirstDayOfWeek(ep.preferences().preferences.first().firstDayOfWeek)
    val today = ep.timeProvider().today()
    val byHabit = activities.groupBy { it.habitId }
    val progress = habits.associateWith { HabitCalculator.progressByDay(it, byHabit[it.id].orEmpty()) }
    val rates = (0L until 7L).mapNotNull { offset ->
        val date = today.minusDays(offset)
        val counted = habits.filter { HabitCalculator.countsTowardDailyProgress(it, progress[it].orEmpty(), date, firstDay) }
        if (counted.isEmpty()) null else counted.count { progress[it]?.get(date)?.completed == true }.toFloat() / counted.size
    }
    val best = habits.maxByOrNull { HabitCalculator.streak(it, progress[it].orEmpty(), today, firstDay).current }
    val bestStreak = best?.let { HabitCalculator.streak(it, progress[it].orEmpty(), today, firstDay).current } ?: 0
    SummaryData(
        weekRate = if (rates.isEmpty()) null else (rates.average() * 100).roundToInt(),
        bestStreakLabel = best?.takeIf { bestStreak > 0 }?.let { "${it.emoji} ${it.name}" },
        bestStreak = bestStreak,
        completionsThisWeek = progress.values.sumOf { map -> map.values.count { it.completed && !it.date.isBefore(today.minusDays(6)) } },
    )
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (error: Exception) {
    Log.e("Widgets", "Failed to load summary", error)
    null
}

class HabitSummaryWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadSummary(context)
        provideContent {
            GlanceTheme(colors = WidgetColors) {
                Column(
                    modifier = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.surface).cornerRadius(20.dp)
                        .padding(14.dp).clickable(actionStartActivity<MainActivity>()),
                ) {
                    Text(
                        context.getString(R.string.widget_summary_title),
                        style = TextStyle(color = GlanceTheme.colors.primary, fontWeight = FontWeight.Bold, fontSize = 15.sp),
                    )
                    Spacer(GlanceModifier.height(6.dp))
                    if (data == null) {
                        Text(context.getString(R.string.widget_error), style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant))
                    } else {
                        SummaryLine(context.getString(R.string.stat_week_rate), data.weekRate?.let { "$it%" } ?: "—")
                        SummaryLine(context.getString(R.string.widget_completions_week), data.completionsThisWeek.toString())
                        SummaryLine(
                            context.getString(R.string.stat_best_current_streak),
                            data.bestStreakLabel?.let { "$it · ${data.bestStreak}" } ?: "—",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp), modifier = GlanceModifier.defaultWeight())
        Text(value, maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium))
    }
}

class TodayHabitsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayHabitsWidget()
}

class DailyProgressWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DailyProgressWidget()
}

class HabitSummaryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HabitSummaryWidget()
}
