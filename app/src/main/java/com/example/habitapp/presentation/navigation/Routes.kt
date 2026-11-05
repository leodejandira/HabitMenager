package com.example.habitapp.presentation.navigation

import kotlinx.serialization.Serializable

// Type-safe Navigation Compose routes.

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

/** Create (habitId == null) or edit a habit; [suggestionId] pre-fills from the catalog. */
@Serializable
data class HabitEditorRoute(val habitId: String? = null, val suggestionId: String? = null)

@Serializable
data class HabitDetailRoute(val habitId: String)

@Serializable
data class HabitReportRoute(val habitId: String)

@Serializable
data object SuggestionsRoute

@Serializable
data object RoutinesRoute

@Serializable
data class RoutineEditorRoute(val routineId: String? = null)

@Serializable
data class RoutineRunRoute(val routineId: String)

@Serializable
data class TimerRoute(val habitId: String)

@Serializable
data object StatisticsRoute

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
