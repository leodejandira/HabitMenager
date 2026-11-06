package com.example.habitapp.presentation.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.habitapp.presentation.calendar.CalendarNavigation
import com.example.habitapp.presentation.calendar.CalendarScreen
import com.example.habitapp.presentation.habits.AchievementsScreen
import com.example.habitapp.presentation.habits.ArchivedHabitsScreen
import com.example.habitapp.presentation.habits.HabitCreateScreen
import com.example.habitapp.presentation.habits.HabitDetailNavigation
import com.example.habitapp.presentation.habits.HabitDetailScreen
import com.example.habitapp.presentation.habits.HabitEditorScreen
import com.example.habitapp.presentation.habits.HabitTypeScreen
import com.example.habitapp.presentation.home.HomeNavigation
import com.example.habitapp.presentation.home.HomeScreen
import com.example.habitapp.presentation.journal.JournalEditorScreen
import com.example.habitapp.presentation.journal.JournalScreen
import com.example.habitapp.presentation.reports.ReportsScreen
import com.example.habitapp.presentation.routines.RoutineEditorScreen
import com.example.habitapp.presentation.routines.RoutineRunScreen
import com.example.habitapp.presentation.routines.RoutinesScreen
import com.example.habitapp.presentation.settings.SettingsLink
import com.example.habitapp.presentation.settings.SettingsScreen
import com.example.habitapp.presentation.statistics.HabitReportScreen
import com.example.habitapp.presentation.statistics.StatisticsScreen
import com.example.habitapp.presentation.tasks.TaskEditorScreen
import com.example.habitapp.presentation.tasks.TaskListsScreen
import com.example.habitapp.presentation.timers.TimerScreen
import java.time.LocalDate

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val nav = navController
    fun go(route: Any) = nav.navigate(route) { launchSingleTop = true }
    val back: () -> Unit = { nav.popBackStack() }

    NavHost(
        navController = nav,
        startDestination = TodayRoute,
        modifier = modifier,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() },
    ) {
        composable<TodayRoute> {
            val navigation = remember(nav) {
                HomeNavigation(
                    onOpenHabit = { go(HabitDetailRoute(it)) },
                    onNewHabit = { go(HabitCreateRoute) },
                    onOpenTask = { id, day -> go(TaskEditorRoute(taskId = id, occurrenceDay = day?.toEpochDay())) },
                    onReports = { go(ReportsRoute) },
                    onTimer = { go(TimerRoute()) },
                    onManageLists = { go(TaskListsRoute) },
                    onRoutines = { go(RoutinesRoute) },
                    onStatistics = { go(StatisticsRoute) },
                )
            }
            HomeScreen(navigation)
        }
        composable<CalendarRoute> {
            val navigation = remember(nav) {
                CalendarNavigation(
                    onOpenHabit = { go(HabitDetailRoute(it)) },
                    onTimer = { go(TimerRoute(it)) },
                    onOpenTask = { id, day -> go(TaskEditorRoute(taskId = id, occurrenceDay = day?.toEpochDay())) },
                    onNewTask = { go(TaskEditorRoute(presetDay = it.toEpochDay())) },
                    onOpenJournal = { go(JournalEditorRoute(entryId = it)) },
                    onNewJournal = { go(JournalEditorRoute(day = it.toEpochDay())) },
                )
            }
            CalendarScreen(navigation)
        }
        composable<JournalRoute> {
            JournalScreen(
                onNewEntry = { go(JournalEditorRoute(day = LocalDate.now().toEpochDay())) },
                onOpenEntry = { go(JournalEditorRoute(entryId = it)) },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(
                onOpenLink = { link ->
                    go(
                        when (link) {
                            SettingsLink.ARCHIVED -> ArchivedHabitsRoute
                            SettingsLink.ACHIEVEMENTS -> AchievementsRoute
                            SettingsLink.ROUTINES -> RoutinesRoute
                            SettingsLink.STATISTICS -> StatisticsRoute
                            SettingsLink.REPORTS -> ReportsRoute
                            SettingsLink.LISTS -> TaskListsRoute
                            SettingsLink.TIMER -> TimerRoute()
                        },
                    )
                },
            )
        }

        // Habit creation: suggestions → (custom) tracking type → editor.
        composable<HabitCreateRoute> {
            HabitCreateScreen(
                onBack = back,
                onSuggestion = { id ->
                    nav.navigate(HabitEditorRoute(suggestionId = id)) { popUpTo<HabitCreateRoute> { inclusive = true } }
                },
                onCustom = { go(HabitTypeRoute) },
            )
        }
        composable<HabitTypeRoute> {
            HabitTypeScreen(
                onBack = back,
                onSelected = { type ->
                    nav.navigate(HabitEditorRoute(trackingType = type.name)) { popUpTo<HabitCreateRoute> { inclusive = true } }
                },
            )
        }
        composable<HabitEditorRoute> { HabitEditorScreen(onBack = back) }
        composable<HabitDetailRoute> {
            val navigation = remember(nav) {
                HabitDetailNavigation(
                    onBack = back,
                    onEdit = { go(HabitEditorRoute(habitId = it)) },
                    onOpenHabit = { id ->
                        nav.navigate(HabitDetailRoute(id)) { popUpTo<HabitDetailRoute> { inclusive = true } }
                    },
                    onTimer = { go(TimerRoute(it)) },
                    onReport = { go(HabitReportRoute(it)) },
                    onNewNote = { id, day -> go(JournalEditorRoute(habitId = id, day = day.toEpochDay())) },
                    onOpenNote = { go(JournalEditorRoute(entryId = it)) },
                )
            }
            HabitDetailScreen(navigation)
        }
        composable<HabitReportRoute> { HabitReportScreen(onBack = back) }
        composable<ReportsRoute> { ReportsScreen(onBack = back, onStatistics = { go(StatisticsRoute) }) }
        composable<ArchivedHabitsRoute> { ArchivedHabitsScreen(onBack = back, onOpenHabit = { go(HabitDetailRoute(it)) }) }
        composable<AchievementsRoute> { AchievementsScreen(onBack = back) }
        composable<RoutinesRoute> {
            RoutinesScreen(
                onBack = back,
                onNew = { go(RoutineEditorRoute()) },
                onEdit = { go(RoutineEditorRoute(it)) },
                onRun = { go(RoutineRunRoute(it)) },
            )
        }
        composable<RoutineEditorRoute> { RoutineEditorScreen(onBack = back) }
        composable<RoutineRunRoute> {
            RoutineRunScreen(onBack = back, onOpenHabit = { go(HabitDetailRoute(it)) }, onTimer = { go(TimerRoute(it)) })
        }
        composable<TimerRoute> { TimerScreen(onBack = back) }
        composable<StatisticsRoute> { StatisticsScreen(onBack = back, onOpenReport = { go(HabitReportRoute(it)) }) }
        composable<TaskEditorRoute> { TaskEditorScreen(onBack = back) }
        composable<TaskListsRoute> { TaskListsScreen(onBack = back) }
        composable<JournalEditorRoute> { JournalEditorScreen(onBack = back) }
    }
}
