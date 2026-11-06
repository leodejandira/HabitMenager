package com.example.habitapp.presentation.navigation

import kotlinx.serialization.Serializable

// Type-safe Navigation Compose routes.

@Serializable
data object TodayRoute

@Serializable
data object CalendarRoute

@Serializable
data object JournalRoute

@Serializable
data object SettingsRoute

/** Step 1 of creating a habit: suggestions. */
@Serializable
data object HabitCreateRoute

/** Step 2 of a custom habit: choose the tracking type. */
@Serializable
data object HabitTypeRoute

/** Create (habitId == null) or edit a habit; [suggestionId] / [trackingType] pre-fill a new one. */
@Serializable
data class HabitEditorRoute(val habitId: String? = null, val suggestionId: String? = null, val trackingType: String? = null)

@Serializable
data class HabitDetailRoute(val habitId: String)

@Serializable
data class HabitReportRoute(val habitId: String)

@Serializable
data object ReportsRoute

@Serializable
data object RoutinesRoute

@Serializable
data class RoutineEditorRoute(val routineId: String? = null)

@Serializable
data class RoutineRunRoute(val routineId: String)

@Serializable
data class TimerRoute(val habitId: String? = null)

@Serializable
data object StatisticsRoute

@Serializable
data object ArchivedHabitsRoute

@Serializable
data object AchievementsRoute

/** [occurrenceDay] is the epoch day of the occurrence being edited; [presetDay] pre-fills a new task's date. */
@Serializable
data class TaskEditorRoute(
    val taskId: String? = null,
    val occurrenceDay: Long? = null,
    val presetDay: Long? = null,
)

@Serializable
data object TaskListsRoute

@Serializable
data class JournalEditorRoute(
    val entryId: String? = null,
    val day: Long? = null,
    val habitId: String? = null,
)
