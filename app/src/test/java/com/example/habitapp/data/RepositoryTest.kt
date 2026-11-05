package com.example.habitapp.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.habitapp.data.backup.BackupManager
import com.example.habitapp.data.local.database.AppDatabase
import com.example.habitapp.data.local.database.defaultTaskLists
import com.example.habitapp.data.repository.HabitRepositoryImpl
import com.example.habitapp.data.repository.JournalRepositoryImpl
import com.example.habitapp.data.repository.TaskRepositoryImpl
import com.example.habitapp.domain.model.EditScope
import com.example.habitapp.domain.model.Habit
import com.example.habitapp.domain.model.HabitGroup
import com.example.habitapp.domain.model.HabitReminder
import com.example.habitapp.domain.model.HabitType
import com.example.habitapp.domain.model.JournalEntry
import com.example.habitapp.domain.model.JournalFilter
import com.example.habitapp.domain.model.RecurrenceType
import com.example.habitapp.domain.model.Task
import com.example.habitapp.domain.model.TaskRecurrence
import com.example.habitapp.domain.repository.InvalidBackupException
import com.example.habitapp.domain.repository.JournalQuery
import com.example.habitapp.domain.repository.ReminderScheduler
import com.example.habitapp.domain.usecase.TaskQueries
import com.example.habitapp.domain.util.HabitCalculator
import com.example.habitapp.domain.util.TimeProvider
import com.example.habitapp.testing.FakeUserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

private class FixedTime(var instant: Instant = Instant.parse("2026-11-05T15:00:00Z")) : TimeProvider {
    override fun zone(): ZoneId = ZoneOffset.UTC
    override fun nowInstant(): Instant = instant
}

private class RecordingScheduler : ReminderScheduler {
    val rescheduledHabits = mutableListOf<String>()
    val cancelledHabits = mutableListOf<String>()
    override suspend fun rescheduleHabit(habitId: String) { rescheduledHabits += habitId }
    override suspend fun cancelHabit(habitId: String) { cancelledHabits += habitId }
    override suspend fun rescheduleTask(taskId: String) = Unit
    override suspend fun cancelTask(taskId: String) = Unit
    override suspend fun rescheduleAll() = Unit
}

/** Repository and DAO behaviour against a real (in-memory) Room database. */
@RunWith(AndroidJUnit4::class)
class RepositoryTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private val time = FixedTime()
    private val scheduler = RecordingScheduler()
    private lateinit var habits: HabitRepositoryImpl
    private lateinit var tasks: TaskRepositoryImpl
    private lateinit var journal: JournalRepositoryImpl
    private lateinit var inboxId: String
    private val today = LocalDate.of(2026, 11, 5)

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        habits = HabitRepositoryImpl(db, db.habitDao(), time, scheduler)
        tasks = TaskRepositoryImpl(db, db.taskDao(), time, scheduler)
        journal = JournalRepositoryImpl(db.journalDao(), time)
        val lists = defaultTaskLists(context, 0)
        db.backupDao().insertTaskLists(lists)
        inboxId = lists.first { it.isInbox }.id
    }

    @After
    fun tearDown() = db.close()

    private fun habit(id: String = "h1", type: HabitType = HabitType.BOOLEAN, target: Double = 1.0) =
        Habit(id = id, name = "Habit $id", type = type, target = target, step = 250.0, startDate = today.minusDays(10), createdAt = time.instant)

    @Test
    fun saveEditAndReadHabitWithReminders() = runBlocking {
        habits.saveHabit(habit(), listOf(HabitReminder("r1", "h1", 8 * 60, emptySet())))
        habits.saveHabit(habit().copy(name = "Renamed"), listOf(HabitReminder("r2", "h1", 20 * 60, emptySet())))
        assertEquals("Renamed", habits.getHabit("h1")?.name)
        assertEquals(listOf(20 * 60), habits.observeReminders("h1").first().map { it.minuteOfDay })
        assertEquals(listOf("h1", "h1"), scheduler.rescheduledHabits)
    }

    @Test
    fun toggleCompletionIsReversible() = runBlocking {
        habits.saveHabit(habit(), emptyList())
        habits.toggleCompletion("h1", today)
        val h = habits.getHabit("h1")!!
        assertTrue(HabitCalculator.progressByDay(h, habits.observeAllActivities().first())[today]!!.completed)
        habits.toggleCompletion("h1", today)
        assertTrue(habits.observeAllActivities().first().isEmpty())
    }

    @Test
    fun numericProgressAccumulatesAndCannotGoNegative() = runBlocking {
        habits.saveHabit(habit(type = HabitType.NUMERIC, target = 2000.0), emptyList())
        habits.addProgress("h1", today, 500.0)
        habits.addProgress("h1", today, 250.0)
        habits.addProgress("h1", today, -1000.0)
        val h = habits.getHabit("h1")!!
        assertEquals(0.0, HabitCalculator.progressByDay(h, habits.observeAllActivities().first())[today]!!.total, 0.0)
    }

    @Test
    fun editingTheGoalPreservesHistory() = runBlocking {
        habits.saveHabit(habit(type = HabitType.NUMERIC, target = 1000.0), emptyList())
        habits.addProgress("h1", today.minusDays(1), 1000.0)
        habits.saveHabit(habits.getHabit("h1")!!.copy(target = 3000.0), emptyList())
        val h = habits.getHabit("h1")!!
        val progress = HabitCalculator.progressByDay(h, habits.observeAllActivities().first())
        assertTrue("yesterday stays complete under the old goal", progress[today.minusDays(1)]!!.completed)
    }

    @Test
    fun pastEntriesCanBeEditedAndDeleted() = runBlocking {
        habits.saveHabit(habit(type = HabitType.COUNT, target = 30.0), emptyList())
        habits.addProgress("h1", today.minusDays(2), 10.0)
        val entry = habits.observeActivitiesForHabit("h1").first().single()
        habits.updateActivity(entry.id, today.minusDays(3), 30.0, "fixed")
        val updated = habits.observeActivitiesForHabit("h1").first().single()
        assertEquals(today.minusDays(3), updated.date)
        assertEquals("fixed", updated.note)
        habits.deleteActivity(updated.id)
        assertTrue(habits.observeActivitiesForHabit("h1").first().isEmpty())
    }

    @Test
    fun deletingAHabitCascadesToItsHistoryAndRoutines() = runBlocking {
        habits.saveHabit(habit(), emptyList())
        habits.toggleCompletion("h1", today)
        journal.save(JournalEntry("j1", today, "note", "", null, false, "h1", time.instant, time.instant))
        habits.deleteHabit("h1")
        assertTrue(habits.observeAllActivities().first().isEmpty())
        assertNull("journal entries survive, unlinked", journal.get("j1")?.habitId)
        assertEquals(listOf("h1"), scheduler.cancelledHabits)
    }

    @Test
    fun deletingAGroupUngroupsItsHabits() = runBlocking {
        habits.saveGroup(HabitGroup("g1", "Morning", 0, false))
        habits.saveHabit(habit().copy(groupId = "g1"), emptyList())
        habits.deleteGroup("g1")
        assertNull(habits.getHabit("h1")?.groupId)
    }

    private fun recurring(id: String = "t1") = Task(
        id = id,
        listId = inboxId,
        title = "Water plants",
        dueDate = LocalDate.of(2026, 11, 2),
        recurrence = TaskRecurrence(RecurrenceType.DAILY),
        createdAt = time.instant,
    )

    @Test
    fun completingOneOccurrenceDoesNotCompleteTheSeries() = runBlocking {
        tasks.saveTask(recurring())
        tasks.setOccurrenceCompleted("t1", today, true)
        val data = tasks.observeTaskData().first()
        assertTrue(TaskQueries.occurrencesOn(data, today).single().completed)
        assertFalse(TaskQueries.occurrencesOn(data, today.plusDays(1)).single().completed)
    }

    @Test
    fun editOnlyThisOccurrenceDetachesIt() = runBlocking {
        tasks.saveTask(recurring())
        tasks.updateRecurringTask(recurring().copy(title = "Special"), today, EditScope.THIS_OCCURRENCE)
        val data = tasks.observeTaskData().first()
        assertEquals(listOf("Special"), TaskQueries.occurrencesOn(data, today).map { it.task.title })
        assertEquals(listOf("Water plants"), TaskQueries.occurrencesOn(data, today.plusDays(1)).map { it.task.title })
    }

    @Test
    fun editThisAndFutureSplitsTheSeries() = runBlocking {
        tasks.saveTask(recurring())
        tasks.setOccurrenceCompleted("t1", today.minusDays(1), true)
        tasks.updateRecurringTask(recurring().copy(title = "New name"), today, EditScope.THIS_AND_FUTURE)
        val data = tasks.observeTaskData().first()
        assertEquals(listOf("Water plants"), TaskQueries.occurrencesOn(data, today.minusDays(1)).map { it.task.title })
        assertTrue("past completion kept", TaskQueries.occurrencesOn(data, today.minusDays(1)).single().completed)
        assertEquals(listOf("New name"), TaskQueries.occurrencesOn(data, today).map { it.task.title })
        assertEquals(listOf("New name"), TaskQueries.occurrencesOn(data, today.plusDays(5)).map { it.task.title })
    }

    @Test
    fun rescheduleMovesASingleOccurrence() = runBlocking {
        tasks.saveTask(recurring())
        tasks.reschedule("t1", today, today.plusDays(1))
        val data = tasks.observeTaskData().first()
        assertTrue(TaskQueries.occurrencesOn(data, today).isEmpty())
        assertEquals(2, TaskQueries.occurrencesOn(data, today.plusDays(1)).size)
    }

    @Test
    fun deletingAListMovesItsTasksToTheInbox() = runBlocking {
        val work = tasks.observeLists().first().first { it.name == "Work" }
        tasks.saveTask(Task("t2", work.id, "Report", createdAt = time.instant))
        tasks.deleteList(work.id)
        assertEquals(inboxId, tasks.getTask("t2")?.listId)
        assertFalse(tasks.observeLists().first().any { it.id == work.id })
    }

    @Test
    fun theInboxCannotBeDeleted() = runBlocking {
        try {
            tasks.deleteList(inboxId)
            fail("expected an exception")
        } catch (expected: IllegalArgumentException) {
            assertTrue(tasks.observeLists().first().any { it.id == inboxId })
        }
    }

    @Test
    fun journalSearchFiltersByTextAndBookmark() = runBlocking {
        journal.save(JournalEntry("a", today, "Morning run", "Felt **great**", 4, true, null, time.instant, time.instant))
        journal.save(JournalEntry("b", today, "Work", "Long 100% day", null, false, null, time.instant, time.instant))
        assertEquals(listOf("a"), journal.search(JournalQuery(text = "great")).first().map { it.id })
        assertEquals(listOf("b"), journal.search(JournalQuery(text = "100%")).first().map { it.id })
        assertEquals(listOf("a"), journal.search(JournalQuery(filter = JournalFilter.BOOKMARKED)).first().map { it.id })
    }

    private fun backupManager(prefs: FakeUserPreferencesRepository = FakeUserPreferencesRepository()) = BackupManager(
        context, db, db.habitDao(), db.taskDao(), db.journalDao(), db.backupDao(), prefs, scheduler, time, Dispatchers.Unconfined,
    )

    @Test
    fun backupRoundTripRestoresEverything() = runBlocking {
        habits.saveHabit(habit(type = HabitType.NUMERIC, target = 2000.0), listOf(HabitReminder("r1", "h1", 480, emptySet())))
        habits.addProgress("h1", today, 750.0)
        tasks.saveTask(recurring())
        tasks.setOccurrenceCompleted("t1", today, true)
        journal.save(JournalEntry("j1", today, "Day", "Text", 3, true, "h1", time.instant, time.instant))

        val manager = backupManager()
        val json = manager.encode(manager.createBackup("test"))
        manager.resetAll()
        assertTrue(habits.observeHabits().first().isEmpty())

        manager.restore(manager.decodeAndValidate(json))
        assertEquals("Habit h1", habits.getHabit("h1")?.name)
        assertEquals(750.0, habits.observeAllActivities().first().single().value, 0.0)
        assertEquals(1, habits.observeReminders("h1").first().size)
        assertTrue(TaskQueries.occurrencesOn(tasks.observeTaskData().first(), today).single().completed)
        assertEquals("h1", journal.get("j1")?.habitId)
    }

    @Test
    fun invalidBackupIsRejectedAndDataIsKept() = runBlocking {
        habits.saveHabit(habit(), emptyList())
        val manager = backupManager()
        val broken = manager.encode(manager.createBackup("test")).replace("\"habitId\": \"h1\"", "\"habitId\": \"missing\"")
        habits.toggleCompletion("h1", today)
        val withActivity = manager.encode(manager.createBackup("test")).replace("\"habitId\": \"h1\"", "\"habitId\": \"missing\"")
        try {
            manager.decodeAndValidate(withActivity)
            fail("expected validation error")
        } catch (e: InvalidBackupException) {
            assertEquals(InvalidBackupException.Reason.INTEGRITY, e.reason)
        }
        try {
            manager.decodeAndValidate("{not json")
            fail("expected format error")
        } catch (e: InvalidBackupException) {
            assertEquals(InvalidBackupException.Reason.FORMAT, e.reason)
        }
        assertTrue(broken.isNotEmpty())
        assertEquals("Habit h1", habits.getHabit("h1")?.name)
        assertEquals(1, habits.observeAllActivities().first().size)
    }
}
