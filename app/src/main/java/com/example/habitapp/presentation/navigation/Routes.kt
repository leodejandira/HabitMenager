package com.example.habitapp.presentation.navigation

import kotlinx.serialization.Serializable

// Type-safe Navigation Compose routes. Detail routes added in later phases carry
// their arguments as constructor properties (e.g. HabitDetailRoute(habitId: String)).

@Serializable
data object TodayRoute

@Serializable
data object HabitsRoute

@Serializable
data object CalendarRoute

@Serializable
data object JournalRoute

@Serializable
data object TasksRoute

@Serializable
data object SettingsRoute
