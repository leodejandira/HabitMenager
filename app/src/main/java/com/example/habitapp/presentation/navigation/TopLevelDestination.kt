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
    JOURNAL(JournalRoute, JournalRoute::class, R.drawable.ic_journal, R.string.nav_journal),
    CALENDAR(CalendarRoute, CalendarRoute::class, R.drawable.ic_calendar, R.string.nav_calendar),
    SETTINGS(SettingsRoute, SettingsRoute::class, R.drawable.ic_settings, R.string.settings_title),
}

fun NavDestination?.isInHierarchy(routeClass: KClass<*>): Boolean =
    this?.hierarchy?.any { it.hasRoute(routeClass) } ?: false
