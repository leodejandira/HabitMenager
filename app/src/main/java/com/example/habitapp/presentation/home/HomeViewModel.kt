package com.example.habitapp.presentation.home

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.habitapp.domain.model.Habit
import com.example.habitapp.domain.model.HabitDisplay
import com.example.habitapp.domain.model.Task
import com.example.habitapp.domain.model.TaskList
import com.example.habitapp.domain.model.TaskOccurrence
import com.example.habitapp.domain.repository.HabitRepository
import com.example.habitapp.domain.repository.TaskData
import com.example.habitapp.domain.repository.TaskRepository
import com.example.habitapp.domain.repository.UserPreferencesRepository
import com.example.habitapp.domain.usecase.BuildDayOverviewUseCase
import com.example.habitapp.domain.usecase.HabitDayItem
import com.example.habitapp.domain.usecase.TaskQueries
import com.example.habitapp.domain.util.HabitCalculator
import com.example.habitapp.domain.util.TimeProvider
import com.example.habitapp.domain.util.minuteTicks
import com.example.habitapp.domain.util.resolveFirstDayOfWeek
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/** What the home screen is showing; chosen from the side menu. */
sealed interface HomeView {
    data object Today : HomeView
    data object AllTasks : HomeView
    data object AllHabits : HomeView
    data class InList(val listId: String) : HomeView
    data class Search(val query: String) : HomeView

    fun encode(): String = when (this) {
        Today -> "today"
        AllTasks -> "tasks"
        AllHabits -> "habits"
        is InList -> "list:$listId"
        is Search -> "search:$query"
    }

    companion object {
        fun decode(value: String): HomeView = when {
            value == "tasks" -> AllTasks
            value == "habits" -> AllHabits
            value.startsWith("list:") -> InList(value.removePrefix("list:"))
            value.startsWith("search:") -> Search(value.removePrefix("search:"))
            else -> Today
        }
    }
}

enum class SectionKind { OVERDUE, TASKS, HABITS, COMPLETED }

/** Section titles; [DATE] uses [HomeSection.date], [LIST] uses [HomeSection.listName]. */
enum class SectionTitle { OVERDUE, TASKS, HABITS, COMPLETED_TASKS, TODAY, UPCOMING, NO_DATE, RESULTS_TASKS, RESULTS_HABITS }

data class HomeSection(
    val id: String,
    val kind: SectionKind,
    val title: SectionTitle,
    val tasks: List<TaskOccurrence> = emptyList(),
    val habits: List<HabitDayItem> = emptyList(),
    val collapsed: Boolean = false,
    /** Completed / total, for the small progress ring. */
    val done: Int = 0,
    val total: Int = 0,
)

data class DrawerList(val list: TaskList, val count: Int)

data class DrawerCounts(val today: Int, val allTasks: Int, val habits: Int, val lists: List<DrawerList>)

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object Error : HomeUiState
    data class Ready(
        val view: HomeView,
        val listName: String?,
        val today: LocalDate,
        val selectedDate: LocalDate,
        val weekDays: List<LocalDate>,
        val firstDayOfWeek: DayOfWeek,
        val sections: List<HomeSection>,
        val drawer: DrawerCounts,
        val habitDisplay: HabitDisplay,
        val lists: Map<String, TaskList>,
        val defaultListId: String?,
        val isEmpty: Boolean,
    ) : HomeUiState
}

/** Everything the quick-add panel can set on a new task. */
data class TaskDraft(
    val title: String,
    val description: String,
    val date: LocalDate?,
    val time: java.time.LocalTime?,
    val recurrence: com.example.habitapp.domain.model.TaskRecurrence,
    val remind: Boolean,
    val listId: String,
    val priority: com.example.habitapp.domain.model.TaskPriority,
    val colorKey: String?,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val habitRepository: HabitRepository,
    private val taskRepository: TaskRepository,
    private val preferences: UserPreferencesRepository,
    private val buildOverview: BuildDayOverviewUseCase,
    private val time: TimeProvider,
) : ViewModel() {

    private val viewKey = savedStateHandle.getStateFlow(KEY_VIEW, HomeView.Today.encode())
    private val dateKey = savedStateHandle.getStateFlow<Long?>(KEY_DATE, null)
    private val toggledKey = savedStateHandle.getStateFlow(KEY_TOGGLED, arrayListOf<String>())

    private val todayFlow = time.minuteTicks().map { it.toLocalDate() }.distinctUntilChanged()

    private data class UiKeys(val view: HomeView, val date: Long?, val toggled: Set<String>)

    private val keys = combine(viewKey, dateKey, toggledKey) { v, d, t -> UiKeys(HomeView.decode(v), d, t.toSet()) }

    val state: StateFlow<HomeUiState> = combine(
        keys,
        todayFlow,
        combine(habitRepository.observeHabits(), habitRepository.observeAllActivities()) { h, a -> h to a },
        combine(taskRepository.observeTaskData(), taskRepository.observeLists()) { t, l -> t to l },
        preferences.preferences,
    ) { k, today, (habits, activities), (tasks, lists), prefs ->
        val firstDay = resolveFirstDayOfWeek(prefs.firstDayOfWeek)
        val selected = k.date?.let(LocalDate::ofEpochDay) ?: today
        val activeHabits = habits.filter { !it.archived }.sortedBy { it.sortOrder }
        val byHabit = activities.groupBy { it.habitId }
        fun item(h: Habit, date: LocalDate) = buildOverview.item(h, byHabit[h.id].orEmpty(), date, firstDay)
        val listMap = lists.associateBy { it.id }

        fun collapsed(id: String, default: Boolean) = default xor (id in k.toggled)

        val sections = when (val view = k.view) {
            HomeView.Today -> todaySections(tasks, activeHabits, selected, today, firstDay, prefs.showOverdueOnToday, prefs.hideCompletedHabits, ::item, ::collapsed)
            HomeView.AllTasks -> allTaskSections(tasks, today, ::collapsed)
            HomeView.AllHabits -> listOf(habitSection("habits", SectionTitle.HABITS, activeHabits.map { item(it, selected) }, ::collapsed))
            is HomeView.InList -> listSections(view.listId, tasks, activeHabits, selected, today, ::item, ::collapsed)
            is HomeView.Search -> searchSections(view.query, tasks, activeHabits, selected, today, ::item, ::collapsed)
        }
        HomeUiState.Ready(
            view = k.view,
            listName = (k.view as? HomeView.InList)?.let { listMap[it.listId]?.name },
            today = today,
            selectedDate = selected,
            weekDays = selected.with(TemporalAdjusters.previousOrSame(firstDay)).let { start -> (0L until 7L).map { start.plusDays(it) } },
            firstDayOfWeek = firstDay,
            sections = sections,
            drawer = drawerCounts(tasks, lists, activeHabits, today),
            habitDisplay = prefs.habitDisplay,
            lists = listMap,
            defaultListId = lists.firstOrNull { it.isInbox }?.id ?: lists.firstOrNull()?.id,
            isEmpty = sections.all { it.tasks.isEmpty() && it.habits.isEmpty() },
        ) as HomeUiState
    }
        .catch { error ->
            Log.e(TAG, "Failed to build home", error)
            emit(HomeUiState.Error)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    private fun todaySections(
        tasks: TaskData,
        habits: List<Habit>,
        date: LocalDate,
        today: LocalDate,
        firstDay: DayOfWeek,
        showOverdue: Boolean,
        hideCompleted: Boolean,
        item: (Habit, LocalDate) -> HabitDayItem,
        collapsed: (String, Boolean) -> Boolean,
    ): List<HomeSection> {
        val occurrences = TaskQueries.occurrencesOn(tasks, date)
        val habitItems = habits.filter { HabitCalculator.isScheduledOn(it, date) }.map { item(it, date) }
        val visibleHabits = if (hideCompleted) habitItems.filter { !it.done } else habitItems
        return buildList {
            if (showOverdue && date == today) {
                val overdue = TaskQueries.overdue(tasks, today)
                if (overdue.isNotEmpty()) add(HomeSection("overdue", SectionKind.OVERDUE, SectionTitle.OVERDUE, tasks = overdue, collapsed = collapsed("overdue", false), total = overdue.size))
            }
            val open = occurrences.filter { !it.completed }
            add(
                HomeSection(
                    "tasks", SectionKind.TASKS, SectionTitle.TASKS, tasks = open, collapsed = collapsed("tasks", false),
                    done = occurrences.count { it.completed }, total = occurrences.size,
                ),
            )
            add(
                HomeSection(
                    "habits", SectionKind.HABITS, SectionTitle.HABITS, habits = visibleHabits, collapsed = collapsed("habits", false),
                    done = habitItems.count { it.done && it.countsTowardProgress }, total = habitItems.count { it.countsTowardProgress },
                ),
            )
            val completed = occurrences.filter { it.completed }
            if (completed.isNotEmpty()) {
                add(HomeSection("completed", SectionKind.COMPLETED, SectionTitle.COMPLETED_TASKS, tasks = completed, collapsed = collapsed("completed", true), total = completed.size))
            }
        }
    }

    private fun allTaskSections(tasks: TaskData, today: LocalDate, collapsed: (String, Boolean) -> Boolean): List<HomeSection> {
        val representative = TaskQueries.representative(tasks, today).filter { !it.completed }
        val overdue = TaskQueries.overdue(tasks, today)
        val todayTasks = TaskQueries.occurrencesOn(tasks, today).filter { !it.completed }
        val upcoming = representative.filter { it.date != null && it.date.isAfter(today) }.sortedWith(compareBy({ it.date }, { it.task.dueTime }))
        val noDate = representative.filter { it.date == null }.sortedByDescending { it.task.createdAt }
        val history = TaskQueries.completedHistory(tasks).take(COMPLETED_LIMIT)
        return listOf(
            HomeSection("all_overdue", SectionKind.OVERDUE, SectionTitle.OVERDUE, tasks = overdue, collapsed = collapsed("all_overdue", false), total = overdue.size),
            HomeSection("all_today", SectionKind.TASKS, SectionTitle.TODAY, tasks = todayTasks, collapsed = collapsed("all_today", false), total = todayTasks.size),
            HomeSection("all_upcoming", SectionKind.TASKS, SectionTitle.UPCOMING, tasks = upcoming, collapsed = collapsed("all_upcoming", false), total = upcoming.size),
            HomeSection("all_nodate", SectionKind.TASKS, SectionTitle.NO_DATE, tasks = noDate, collapsed = collapsed("all_nodate", false), total = noDate.size),
            HomeSection("all_completed", SectionKind.COMPLETED, SectionTitle.COMPLETED_TASKS, tasks = history, collapsed = collapsed("all_completed", true), total = history.size),
        ).filter { it.tasks.isNotEmpty() }
    }

    private fun listSections(
        listId: String,
        tasks: TaskData,
        habits: List<Habit>,
        date: LocalDate,
        today: LocalDate,
        item: (Habit, LocalDate) -> HabitDayItem,
        collapsed: (String, Boolean) -> Boolean,
    ): List<HomeSection> {
        val open = TaskQueries.representative(tasks, today).filter { !it.completed && it.task.listId == listId }
            .sortedWith(compareBy<TaskOccurrence>({ it.date == null }, { it.date }, { it.task.dueTime }))
        val listHabits = habits.filter { it.listId == listId }.map { item(it, date) }
        val history = TaskQueries.completedHistory(tasks).filter { it.task.listId == listId }.take(COMPLETED_LIMIT)
        return listOf(
            HomeSection("list_tasks", SectionKind.TASKS, SectionTitle.TASKS, tasks = open, collapsed = collapsed("list_tasks", false), total = open.size),
            HomeSection(
                "list_habits", SectionKind.HABITS, SectionTitle.HABITS, habits = listHabits, collapsed = collapsed("list_habits", false),
                done = listHabits.count { it.done }, total = listHabits.size,
            ),
            HomeSection("list_completed", SectionKind.COMPLETED, SectionTitle.COMPLETED_TASKS, tasks = history, collapsed = collapsed("list_completed", true), total = history.size),
        ).filter { it.tasks.isNotEmpty() || it.habits.isNotEmpty() }
    }

    private fun searchSections(
        query: String,
        tasks: TaskData,
        habits: List<Habit>,
        date: LocalDate,
        today: LocalDate,
        item: (Habit, LocalDate) -> HabitDayItem,
        collapsed: (String, Boolean) -> Boolean,
    ): List<HomeSection> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val matchingTasks = TaskQueries.representative(tasks, today)
            .filter { it.task.title.contains(q, ignoreCase = true) || it.task.description.contains(q, ignoreCase = true) }
        val matchingHabits = habits.filter { it.name.contains(q, ignoreCase = true) }.map { item(it, date) }
        return listOf(
            HomeSection("search_tasks", SectionKind.TASKS, SectionTitle.RESULTS_TASKS, tasks = matchingTasks, collapsed = collapsed("search_tasks", false), total = matchingTasks.size),
            HomeSection("search_habits", SectionKind.HABITS, SectionTitle.RESULTS_HABITS, habits = matchingHabits, collapsed = collapsed("search_habits", false), total = matchingHabits.size),
        ).filter { it.tasks.isNotEmpty() || it.habits.isNotEmpty() }
    }

    private fun habitSection(id: String, title: SectionTitle, items: List<HabitDayItem>, collapsed: (String, Boolean) -> Boolean) =
        HomeSection(id, SectionKind.HABITS, title, habits = items, collapsed = collapsed(id, false), done = items.count { it.done }, total = items.size)

    private fun drawerCounts(tasks: TaskData, lists: List<TaskList>, habits: List<Habit>, today: LocalDate): DrawerCounts {
        val open = TaskQueries.representative(tasks, today).filter { !it.completed }
        val todayCount = TaskQueries.occurrencesOn(tasks, today).count { !it.completed } +
            TaskQueries.overdue(tasks, today).size +
            habits.count { HabitCalculator.isScheduledOn(it, today) }
        return DrawerCounts(
            today = todayCount,
            allTasks = open.size,
            habits = habits.size,
            lists = lists.filter { !it.archived }.map { list ->
                DrawerList(list, open.count { it.task.listId == list.id } + habits.count { it.listId == list.id })
            },
        )
    }

    // --- UI state --------------------------------------------------------

    fun setView(view: HomeView) {
        savedStateHandle[KEY_VIEW] = view.encode()
    }

    fun selectDate(date: LocalDate) {
        savedStateHandle[KEY_DATE] = if (date == time.today()) null else date.toEpochDay()
    }

    fun shiftWeek(weeks: Long) {
        val current = dateKey.value?.let(LocalDate::ofEpochDay) ?: time.today()
        selectDate(current.plusWeeks(weeks))
    }

    fun toggleSection(id: String) {
        val current = toggledKey.value.toMutableSet()
        if (!current.add(id)) current.remove(id)
        savedStateHandle[KEY_TOGGLED] = ArrayList(current)
    }

    fun setHabitDisplay(display: HabitDisplay) = launch { preferences.setHabitDisplay(display) }

    // --- Actions ---------------------------------------------------------

    private fun selectedDate(): LocalDate = dateKey.value?.let(LocalDate::ofEpochDay) ?: time.today()

    fun toggleTask(occurrence: TaskOccurrence) =
        launch { taskRepository.setOccurrenceCompleted(occurrence.task.id, occurrence.date, !occurrence.completed) }

    fun addHabitProgress(habitId: String, amount: Double, date: LocalDate = selectedDate(), note: String? = null) =
        launch { habitRepository.addProgress(habitId, date, amount, note) }

    fun toggleHabitDay(habitId: String, date: LocalDate) = launch { habitRepository.toggleCompletion(habitId, date) }

    fun postpone(occurrences: List<TaskOccurrence>, to: LocalDate) = launch {
        occurrences.forEach { taskRepository.reschedule(it.task.id, it.date, to) }
    }

    fun deleteOccurrence(occurrence: TaskOccurrence) = launch {
        taskRepository.deleteTask(occurrence.task.id, occurrence.date, com.example.habitapp.domain.model.EditScope.THIS_OCCURRENCE)
    }

    fun createTask(draft: TaskDraft) = launch {
        if (draft.title.isBlank()) return@launch
        taskRepository.saveTask(
            Task(
                id = java.util.UUID.randomUUID().toString(),
                listId = draft.listId,
                title = draft.title,
                description = draft.description,
                dueDate = draft.date,
                dueTime = if (draft.date != null) draft.time else null,
                priority = draft.priority,
                colorKey = draft.colorKey,
                recurrence = if (draft.date != null) draft.recurrence else com.example.habitapp.domain.model.TaskRecurrence(),
                reminderMinutesBefore = if (draft.remind && draft.date != null) 0 else null,
                createdAt = time.nowInstant(),
            ),
        )
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Log.e(TAG, "Home action failed", error)
            }
        }
    }

    private companion object {
        const val TAG = "HomeViewModel"
        const val KEY_VIEW = "view"
        const val KEY_DATE = "date"
        const val KEY_TOGGLED = "toggled_sections"
        const val COMPLETED_LIMIT = 200
    }
}
