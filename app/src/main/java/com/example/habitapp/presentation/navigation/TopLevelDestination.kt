package com.example.habitapp.presentation.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import com.example.habitapp.R
import kotlin.reflect.KClass

enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    @param:DrawableRes val iconRes: Int,
    @param:StringRes val labelRes: Int,
) {
    TODAY(TodayRoute, TodayRoute::class, R.drawable.ic_today, R.string.nav_today),
    HABITS(HabitsRoute, HabitsRoute::class, R.drawable.ic_habits, R.string.nav_habits),
    CALENDAR(CalendarRoute, CalendarRoute::class, R.drawable.ic_calendar, R.string.nav_calendar),
    JOURNAL(JournalRoute, JournalRoute::class, R.drawable.ic_journal, R.string.nav_journal),
    TASKS(TasksRoute, TasksRoute::class, R.drawable.ic_tasks, R.string.nav_tasks),
}

fun NavDestination?.isInHierarchy(routeClass: KClass<*>): Boolean =
    this?.hierarchy?.any { it.hasRoute(routeClass) } ?: false
