# Steady — Architecture

Steady is an offline-first habit, task, routine and journaling app for Android,
inspired by the feature breadth of HelloHabit but with original code, visual
identity and assets. This document is the reference for every implementation phase.

## 1. Layers

```
presentation  ──►  domain  ◄──  data
(Compose, VMs)     (models,     (Room, DataStore,
                    use cases,   schedulers,
                    repo APIs)   backup)
```

| Layer | Contains | Depends on |
|---|---|---|
| `domain` | Pure-Kotlin models, repository interfaces, use cases, calculators (streaks, goals, recurrence), `TimeProvider` | nothing Android-specific (except `@Inject`) |
| `data` | Room entities/DAOs/database, repository implementations, DataStore, AlarmManager/WorkManager schedulers, JSON backup | `domain` |
| `presentation` | Screens, stateless `*Content` composables, ViewModels exposing immutable `StateFlow<UiState>` | `domain` |
| `di` | Hilt modules binding interfaces to implementations | all |
| `workers`, `widgets` | WorkManager workers, Glance widgets | `domain` |

Rules: Composables never touch repositories; ViewModels never touch Room entities;
business rules (streaks, goal evaluation, recurrence expansion) live in `domain` and
are unit-tested without Android.

### Screen pattern

```
XxxScreen(viewModel = hiltViewModel())      // collects state lifecycle-aware
  └─ XxxContent(uiState, actions)           // stateless, previewable, UI-testable
XxxViewModel: StateFlow<XxxUiState>          // sealed Loading / Ready / Error (+ Empty where useful)
```

## 2. Time and date strategy

Implemented in `domain/util/TimeProvider.kt` and used by every feature:

- **Instants** (activity logged, task completed, timer started) → stored as UTC epoch
  millis plus the IANA zone id active at that moment.
- **Calendar days** (the day a habit activity counts for, a task's due date) →
  stored as `LocalDate` (ISO `yyyy-MM-dd` / epoch-day), never as midnight timestamps.
- **Times of day** (reminder at 07:30) → stored as `LocalTime` and re-resolved in the
  current zone whenever alarms are scheduled, so DST and travel are handled.
- The zone is read on every call (`ZoneId.systemDefault()`), so time-zone changes are
  picked up without restarting. Phase 8 also re-schedules alarms on
  `ACTION_TIMEZONE_CHANGED`.

## 3. Data model (Room, Phase 2)

All ids are UUID strings so records can be merged with a future sync backend.
Every table has `createdAt`/`updatedAt` instants for the same reason.

| Entity | Key relations | Notes |
|---|---|---|
| `HabitEntity` | → `HabitGroupEntity` (SET NULL) | type, unit, target, color key, icon, archivedAt, sortOrder |
| `HabitScheduleEntity` | → habit (CASCADE) | versioned: `effectiveFrom` so goal edits never rewrite history |
| `HabitActivityEntity` | → habit (CASCADE), index (habitId, date) | value + unit snapshot, date, loggedAt, zone |
| `HabitReminderEntity` | → habit (CASCADE) | time, days mask, repeat rule, enabled |
| `HabitGroupEntity` | — | name, color, collapsed, sortOrder |
| `RoutineEntity` / `RoutineHabitCrossRef` | cross-ref → routine, habit (CASCADE) | ordered steps, optional duration |
| `TaskListEntity` | — | predefined lists seeded on create, archivedAt |
| `TaskEntity` | → list (RESTRICT, moved on delete), → recurrence | due date/time, priority, color |
| `TaskRecurrenceEntity` | → task series (CASCADE) | rule + `TaskOccurrenceOverrideEntity` for per-occurrence edits/completions |
| `TaskReminderEntity` | → task (CASCADE) | |
| `JournalEntryEntity` (+ FTS4 table) | optional → habit (SET NULL) | rich text stored as versioned span JSON |
| `HabitNoteEntity` | → habit (CASCADE) | note per habit per date |
| `TimerSessionEntity` | optional → habit (SET NULL) | startedAt, pausedAccumulated, state |
| `QuitTrackerEntity` / `QuitAttemptEntity` / `QuitMilestoneEntity` | → habit (CASCADE) | relapses close an attempt instead of deleting it |

App settings live in DataStore (`UserPreferences`), not Room.

## 4. Platform decisions

- **Reminders:** `AlarmManager.setExactAndAllowWhileIdle` when the user has granted
  exact alarms (`SCHEDULE_EXACT_ALARM`, user-controlled on Android 14+); otherwise
  `setWindow`/WorkManager with an explicit "may arrive a few minutes late" note in
  the UI. Rescheduled on boot, app update, and time/zone change.
- **Timers:** state persisted in Room (start instant + accumulated pauses); elapsed time
  is always computed from stored instants, never from a UI coroutine, so it survives
  rotation and process death. A foreground service (type `specialUse`, with the Play
  declaration that requires) runs only while a countdown is active in the background;
  stopwatch sessions need no service because elapsed time is derived on return.
- **Rich text:** Compose `TextFieldState` + `AnnotatedString` spans serialized to a
  versioned JSON schema (no WebView editor).
- **Charts:** Vico is added in Phase 7 once its exact artifact coordinates for the
  selected version are verified.
- **Per-app language:** `locales_config.xml` (en, pt-BR); Android 13+ uses
  `LocaleManager`; older versions keep the device language (documented in Phase 9).

## 5. Directory tree (target, all phases)

```
app/src/main/java/com/example/habitapp/
├── HabitApplication.kt                 (P1)
├── MainActivity.kt                     (P1)
├── data/
│   ├── preferences/                    DataStoreUserPreferencesRepository (P1)
│   ├── local/{database,dao,entity,converters}   (P2)
│   ├── repository/                     (P2)
│   ├── scheduler/                      AlarmScheduler, ReminderRescheduler (P8)
│   └── backup/                         JSON export/import + validation (P9)
├── domain/
│   ├── model/                          UserPreferences (P1), Habit, Task, … (P2)
│   ├── repository/                     UserPreferencesRepository (P1), … (P2)
│   ├── usecase/                        (P2–P7)
│   └── util/                           TimeProvider, DayPeriod (P1); StreakCalculator, GoalEvaluator, RecurrenceExpander (P2/P7)
├── presentation/
│   ├── MainViewModel.kt                (P1)
│   ├── navigation/                     Routes, TopLevelDestination, AppNavHost, HabitApp (P1)
│   ├── theme/                          Color, HabitColors, Type, Shape, Spacing, Theme (P1)
│   ├── components/                     EmptyState, SectionHeader, scaffolds (P1) …
│   ├── dashboard/                      (P1 header, P4 full)
│   ├── habits/                         (P1 shell, P3)
│   ├── routines/                       (P3)
│   ├── tasks/                          (P1 shell, P5)
│   ├── calendar/                       (P1 shell, P6)
│   ├── journal/                        (P1 shell, P6)
│   ├── statistics/                     (P7)
│   ├── timers/                         (P7)
│   ├── quit/                           (P7)
│   └── settings/                       (P1 core, P9 data management)
├── di/                                 CoroutinesModule, DataStoreModule, BindingsModule (P1), DatabaseModule (P2)
├── workers/                            (P8)
└── widgets/                            (P8)
```

## 6. Phase plan

| Phase | Scope | Status |
|---|---|---|
| 1 | Gradle, manifest, Hilt, navigation, design system, settings core | **delivered** |
| 2 | Room schema, DAOs, repositories, domain models, core use cases, tests | next |
| 3 | Habit CRUD, types, schedules, activity history, groups, routines | |
| 4 | Dashboard with habit cards, quick actions, daily tasks, animations | |
| 5 | Tasks, lists, priorities, recurrence with per-occurrence edits | |
| 6 | Calendar (month/week/day, drag-to-reschedule), journal, rich text, search | |
| 7 | Streaks, statistics, charts, reports, timers, quit trackers | |
| 8 | Reminders, notification actions, reboot handling, Glance widgets | |
| 9 | Backup/restore, import validation, language, remaining preferences | |
| 10 | Integration, QA, performance review | |
