package com.example.habitapp.presentation.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.habitapp.presentation.calendar.CalendarScreen
import com.example.habitapp.presentation.dashboard.DashboardScreen
import com.example.habitapp.presentation.habits.HabitsScreen
import com.example.habitapp.presentation.journal.JournalScreen
import com.example.habitapp.presentation.settings.SettingsScreen
import com.example.habitapp.presentation.tasks.TasksScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = TodayRoute,
        modifier = modifier,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() },
    ) {
        composable<TodayRoute> {
            DashboardScreen(onOpenSettings = { navController.navigate(SettingsRoute) { launchSingleTop = true } })
        }
        composable<HabitsRoute> { HabitsScreen() }
        composable<CalendarRoute> { CalendarScreen() }
        composable<JournalRoute> { JournalScreen() }
        composable<TasksRoute> { TasksScreen() }
        composable<SettingsRoute> {
            SettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
