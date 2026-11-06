package com.example.habitapp.presentation.habits

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.habitapp.R
import com.example.habitapp.domain.model.Habit
import com.example.habitapp.domain.repository.HabitRepository
import com.example.habitapp.domain.repository.UserPreferencesRepository
import com.example.habitapp.domain.util.HabitCalculator
import com.example.habitapp.domain.util.TimeProvider
import com.example.habitapp.domain.util.resolveFirstDayOfWeek
import com.example.habitapp.presentation.components.ConfirmDialog
import com.example.habitapp.presentation.home.TileIconButton
import com.example.habitapp.presentation.theme.HabitColor
import com.example.habitapp.presentation.theme.OnPastel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

@Composable
private fun PlainHeader(title: String, onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp)) {
        TileIconButton(R.drawable.ic_arrow_back, stringResource(R.string.navigate_back), onBack)
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
    }
}

// ---------------------------------------------------------------- Archived habits

@HiltViewModel
class ArchivedHabitsViewModel @Inject constructor(private val repository: HabitRepository) : ViewModel() {
    val habits: StateFlow<List<Habit>?> = repository.observeHabits().map { list -> list.filter { it.archived } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun restore(id: String) = launch { repository.setArchived(id, false) }

    fun delete(id: String) = launch { repository.deleteHabit(id) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                Log.e("ArchivedHabits", "Action failed", e)
            }
        }
    }
}

@Composable
fun ArchivedHabitsScreen(onBack: () -> Unit, onOpenHabit: (String) -> Unit, viewModel: ArchivedHabitsViewModel = hiltViewModel()) {
    val habits by viewModel.habits.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<Habit?>(null) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        PlainHeader(stringResource(R.string.settings_archived_habits), onBack)
        val list = habits ?: return@Column
        if (list.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(painterResource(R.drawable.ic_archive), null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.archived_empty), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.id }) { habit ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { onOpenHabit(habit.id) }.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(HabitColor.fromKey(habit.colorKey).color).padding(horizontal = 12.dp, vertical = 6.dp),
                    ) { Text(habit.name, color = OnPastel, fontWeight = FontWeight.Bold, maxLines = 1) }
                    Spacer(Modifier.weight(1f))
                    TileIconButton(R.drawable.ic_upload, stringResource(R.string.action_unarchive), { viewModel.restore(habit.id) })
                    Spacer(Modifier.width(6.dp))
                    TileIconButton(R.drawable.ic_delete, stringResource(R.string.action_delete), { deleting = habit })
                }
            }
        }
    }
    deleting?.let { habit ->
        ConfirmDialog(
            title = stringResource(R.string.habit_delete_title),
            message = stringResource(R.string.habit_delete_message, habit.name),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { viewModel.delete(habit.id) },
            onDismiss = { deleting = null },
        )
    }
}

// ---------------------------------------------------------------- Achievements

enum class AchievementKind { STREAK, COMPLETIONS, HABITS, PERFECT_WEEK }

/** A milestone; [progress] is how far along the user is (capped at [goal]). */
data class Achievement(val kind: AchievementKind, val goal: Int, val progress: Int) {
    val unlocked: Boolean get() = progress >= goal
}

object Achievements {
    private val streakGoals = listOf(3, 7, 14, 30, 60, 100, 365)
    private val completionGoals = listOf(1, 10, 50, 100, 250, 500, 1000)
    private val habitGoals = listOf(1, 3, 5, 10)
    private val perfectWeekGoals = listOf(1, 4, 12)

    fun compute(bestStreak: Int, completions: Int, habits: Int, perfectWeeks: Int): List<Achievement> =
        streakGoals.map { Achievement(AchievementKind.STREAK, it, bestStreak.coerceAtMost(it)) } +
            completionGoals.map { Achievement(AchievementKind.COMPLETIONS, it, completions.coerceAtMost(it)) } +
            habitGoals.map { Achievement(AchievementKind.HABITS, it, habits.coerceAtMost(it)) } +
            perfectWeekGoals.map { Achievement(AchievementKind.PERFECT_WEEK, it, perfectWeeks.coerceAtMost(it)) }
}

@HiltViewModel
class AchievementsViewModel @Inject constructor(
    repository: HabitRepository,
    preferences: UserPreferencesRepository,
    time: TimeProvider,
) : ViewModel() {
    val achievements: StateFlow<List<Achievement>?> = combine(
        repository.observeHabits(),
        repository.observeAllActivities(),
        preferences.preferences,
    ) { habits, activities, prefs ->
        val today = time.today()
        val firstDay = resolveFirstDayOfWeek(prefs.firstDayOfWeek)
        val byHabit = activities.groupBy { it.habitId }
        var best = 0
        var completions = 0
        val dailyDone = mutableMapOf<java.time.LocalDate, Boolean>()
        habits.forEach { habit ->
            val acts = byHabit[habit.id].orEmpty()
            val stats = HabitCalculator.stats(habit, acts, today, firstDay)
            best = maxOf(best, stats.streak.longest)
            completions += stats.totalCompletions
            if (!habit.archived) {
                val progress = HabitCalculator.progressByDay(habit, acts)
                var d = today.minusDays(400).coerceAtLeastDate(habit.startDate)
                while (!d.isAfter(today)) {
                    if (HabitCalculator.isScheduledOn(habit, d)) {
                        val ok = progress[d]?.completed == true
                        dailyDone[d] = (dailyDone[d] ?: true) && ok
                    }
                    d = d.plusDays(1)
                }
            }
        }
        // A perfect week: every scheduled habit met on every day of a full past week.
        val perfectWeeks = dailyDone.entries.groupBy { it.key.with(java.time.temporal.TemporalAdjusters.previousOrSame(firstDay)) }
            .count { (start, days) -> days.size >= 7 && start.plusDays(6).isBefore(today.plusDays(1)) && days.all { it.value } }
        Achievements.compute(best, completions, habits.size, perfectWeeks)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

private fun java.time.LocalDate.coerceAtLeastDate(min: java.time.LocalDate) = if (isBefore(min)) min else this

@Composable
fun AchievementsScreen(onBack: () -> Unit, viewModel: AchievementsViewModel = hiltViewModel()) {
    val list by viewModel.achievements.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        PlainHeader(stringResource(R.string.achievements_title), onBack)
        val items = list ?: return@Column
        Text(
            stringResource(R.string.achievements_summary, items.count { it.unlocked }, items.size),
            modifier = Modifier.padding(horizontal = 20.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items, key = { "${it.kind}-${it.goal}" }) { a -> AchievementTile(a) }
        }
    }
}

@Composable
private fun AchievementTile(a: Achievement) {
    val (icon, color, labelRes) = when (a.kind) {
        AchievementKind.STREAK -> Triple(R.drawable.ic_fire, HabitColor.CORAL, R.string.achievement_streak)
        AchievementKind.COMPLETIONS -> Triple(R.drawable.ic_check_circle, HabitColor.MINT, R.string.achievement_completions)
        AchievementKind.HABITS -> Triple(R.drawable.ic_spa, HabitColor.PERIWINKLE, R.string.achievement_habits)
        AchievementKind.PERFECT_WEEK -> Triple(R.drawable.ic_trophy, HabitColor.AMBER, R.string.achievement_perfect_weeks)
    }
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(52.dp).aspectRatio(1f).clip(CircleShape)
                .background(if (a.unlocked) color.color else MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(icon),
                null,
                tint = if (a.unlocked) OnPastel else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp).alpha(if (a.unlocked) 1f else 0.6f),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text("${a.goal}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(stringResource(labelRes), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        if (!a.unlocked) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { a.progress.toFloat() / a.goal },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = color.color,
            )
        }
    }
}
