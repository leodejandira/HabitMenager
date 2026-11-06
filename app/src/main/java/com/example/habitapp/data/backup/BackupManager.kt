package com.example.habitapp.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.example.habitapp.BuildConfig
import com.example.habitapp.data.local.dao.BackupDao
import com.example.habitapp.data.local.dao.HabitDao
import com.example.habitapp.data.local.dao.JournalDao
import com.example.habitapp.data.local.dao.TaskDao
import com.example.habitapp.data.local.database.AppDatabase
import com.example.habitapp.data.local.database.defaultTaskLists
import com.example.habitapp.data.local.entity.HabitActivityEntity
import com.example.habitapp.data.local.entity.HabitEntity
import com.example.habitapp.data.local.entity.HabitGroupEntity
import com.example.habitapp.data.local.entity.HabitReminderEntity
import com.example.habitapp.data.local.entity.JournalEntryEntity
import com.example.habitapp.data.local.entity.RoutineEntity
import com.example.habitapp.data.local.entity.RoutineHabitCrossRef
import com.example.habitapp.data.local.entity.TaskCompletionEntity
import com.example.habitapp.data.local.entity.TaskEntity
import com.example.habitapp.data.local.entity.TaskExceptionEntity
import com.example.habitapp.data.local.entity.TaskListEntity
import com.example.habitapp.data.local.entity.TimerSessionEntity
import com.example.habitapp.di.IoDispatcher
import com.example.habitapp.domain.model.CalendarView
import com.example.habitapp.domain.model.ThemeMode
import com.example.habitapp.domain.repository.DataManagementRepository
import com.example.habitapp.domain.repository.DataSummary
import com.example.habitapp.domain.repository.InvalidBackupException
import com.example.habitapp.domain.repository.ReminderScheduler
import com.example.habitapp.domain.repository.UserPreferencesRepository
import com.example.habitapp.domain.util.TimeProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.time.DayOfWeek
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class BackupPreferences(
    val themeMode: String = ThemeMode.SYSTEM.name,
    val useDynamicColor: Boolean = false,
    val firstDayOfWeek: Int? = null,
    val displayName: String = "",
)

/** Versioned, self-describing backup. Bump [CURRENT_VERSION] when the format changes. */
@Serializable
data class BackupFile(
    @SerialName("format") val format: String = FORMAT_ID,
    val version: Int = CURRENT_VERSION,
    val exportedAt: Long,
    val appVersion: String,
    val preferences: BackupPreferences = BackupPreferences(),
    val habitGroups: List<HabitGroupEntity> = emptyList(),
    val habits: List<HabitEntity> = emptyList(),
    val habitActivities: List<HabitActivityEntity> = emptyList(),
    val habitReminders: List<HabitReminderEntity> = emptyList(),
    val routines: List<RoutineEntity> = emptyList(),
    val routineSteps: List<RoutineHabitCrossRef> = emptyList(),
    val timerSessions: List<TimerSessionEntity> = emptyList(),
    val taskLists: List<TaskListEntity> = emptyList(),
    val tasks: List<TaskEntity> = emptyList(),
    val taskCompletions: List<TaskCompletionEntity> = emptyList(),
    val taskExceptions: List<TaskExceptionEntity> = emptyList(),
    val journalEntries: List<JournalEntryEntity> = emptyList(),
) {
    companion object {
        const val FORMAT_ID = "steady-backup"
        const val CURRENT_VERSION = 2
    }
}

@Singleton
class BackupManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val habitDao: HabitDao,
    private val taskDao: TaskDao,
    private val journalDao: JournalDao,
    private val backupDao: BackupDao,
    private val preferences: UserPreferencesRepository,
    private val reminderScheduler: ReminderScheduler,
    private val time: TimeProvider,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : DataManagementRepository {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun createBackup(appVersion: String): BackupFile = withContext(io) {
        val prefs = preferences.preferences.first()
        db.withTransaction {
            BackupFile(
                exportedAt = time.nowInstant().toEpochMilli(),
                appVersion = appVersion,
                preferences = BackupPreferences(prefs.themeMode.name, prefs.useDynamicColor, prefs.firstDayOfWeek?.value, prefs.displayName),
                habitGroups = habitDao.getAllGroups(),
                habits = habitDao.getAll(),
                habitActivities = habitDao.getAllActivities(),
                habitReminders = habitDao.getAllReminders(),
                routines = habitDao.getAllRoutines(),
                routineSteps = habitDao.getAllRoutineSteps(),
                timerSessions = backupDao.finishedTimerSessions(),
                taskLists = taskDao.getAllLists(),
                tasks = taskDao.getAllTasks(),
                taskCompletions = taskDao.getAllCompletions(),
                taskExceptions = taskDao.getAllExceptions(),
                journalEntries = journalDao.getAll(),
            )
        }
    }

    fun encode(backup: BackupFile): String = json.encodeToString(BackupFile.serializer(), backup)

    override suspend fun exportTo(uri: String): DataSummary = withContext(io) {
        val backup = createBackup(BuildConfig.VERSION_NAME)
        val stream = context.contentResolver.openOutputStream(Uri.parse(uri), "wt") ?: throw IOException("Cannot open $uri")
        stream.bufferedWriter().use { it.write(encode(backup)) }
        backup.summary()
    }

    /** Parses and validates without touching the database. */
    fun decodeAndValidate(text: String): BackupFile {
        val backup = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: SerializationException) {
            throw InvalidBackupException(InvalidBackupException.Reason.FORMAT, e.message)
        } catch (e: IllegalArgumentException) {
            throw InvalidBackupException(InvalidBackupException.Reason.FORMAT, e.message)
        }
        validate(backup)
        return backup
    }

    override suspend fun importFrom(uri: String): DataSummary = withContext(io) {
        val text = context.contentResolver.openInputStream(Uri.parse(uri))?.bufferedReader()?.use { it.readText() }
            ?: throw IOException("Cannot open $uri")
        restore(decodeAndValidate(text))
    }

    /**
     * Replaces all data with the backup inside one transaction: if anything fails the
     * transaction rolls back and the existing data is kept.
     */
    suspend fun restore(backup: BackupFile): DataSummary = withContext(io) {
        db.withTransaction {
            backupDao.deleteAll()
            backupDao.insertGroups(backup.habitGroups)
            backupDao.insertHabits(backup.habits)
            backupDao.insertActivities(backup.habitActivities)
            backupDao.insertReminders(backup.habitReminders)
            backupDao.insertRoutines(backup.routines)
            backupDao.insertRoutineSteps(backup.routineSteps)
            backupDao.insertTimerSessions(backup.timerSessions)
            backupDao.insertTaskLists(backup.taskLists)
            backupDao.insertTasks(backup.tasks)
            backupDao.insertCompletions(backup.taskCompletions)
            backupDao.insertExceptions(backup.taskExceptions)
            backupDao.insertJournal(backup.journalEntries)
        }
        val p = backup.preferences
        ThemeMode.entries.firstOrNull { it.name == p.themeMode }?.let { preferences.setThemeMode(it) }
        preferences.setUseDynamicColor(p.useDynamicColor)
        preferences.setFirstDayOfWeek(p.firstDayOfWeek?.takeIf { it in 1..7 }?.let(DayOfWeek::of))
        preferences.setDisplayName(p.displayName)
        reminderScheduler.rescheduleAll()
        backup.summary()
    }

    /** Deletes every record and restores the default task lists. */
    override suspend fun resetAll() = withContext(io) {
        val existing = habitDao.getAll().map { it.id } to taskDao.getAllTasks().map { it.id }
        existing.first.forEach { reminderScheduler.cancelHabit(it) }
        existing.second.forEach { reminderScheduler.cancelTask(it) }
        db.withTransaction {
            backupDao.deleteAll()
            backupDao.insertTaskLists(defaultTaskLists(context, time.nowInstant().toEpochMilli()))
        }
        preferences.setThemeMode(ThemeMode.SYSTEM)
        preferences.setUseDynamicColor(false)
        preferences.setFirstDayOfWeek(null)
        preferences.setDisplayName("")
        preferences.setDefaultCalendarView(CalendarView.MONTH)
        preferences.setHideCompletedHabits(false)
        preferences.setShowOverdueOnToday(true)
    }

    private fun validate(b: BackupFile) {
        if (b.format != BackupFile.FORMAT_ID) throw InvalidBackupException(InvalidBackupException.Reason.FORMAT, "format=${b.format}")
        if (b.version < 1 || b.version > BackupFile.CURRENT_VERSION) throw InvalidBackupException(InvalidBackupException.Reason.VERSION, "version=${b.version}")

        fun requireUnique(ids: List<String>, what: String) {
            if (ids.any { it.isBlank() }) throw InvalidBackupException(InvalidBackupException.Reason.INTEGRITY, "blank id in $what")
            if (ids.size != ids.toSet().size) throw InvalidBackupException(InvalidBackupException.Reason.INTEGRITY, "duplicate id in $what")
        }
        fun requireRef(ok: Boolean, what: String) {
            if (!ok) throw InvalidBackupException(InvalidBackupException.Reason.INTEGRITY, "broken reference in $what")
        }

        requireUnique(b.habitGroups.map { it.id }, "groups")
        requireUnique(b.habits.map { it.id }, "habits")
        requireUnique(b.habitActivities.map { it.id }, "activities")
        requireUnique(b.habitReminders.map { it.id }, "reminders")
        requireUnique(b.routines.map { it.id }, "routines")
        requireUnique(b.timerSessions.map { it.id }, "timer sessions")
        requireUnique(b.taskLists.map { it.id }, "task lists")
        requireUnique(b.tasks.map { it.id }, "tasks")
        requireUnique(b.journalEntries.map { it.id }, "journal")

        val groupIds = b.habitGroups.map { it.id }.toSet()
        val habitIds = b.habits.map { it.id }.toSet()
        val routineIds = b.routines.map { it.id }.toSet()
        val listIds = b.taskLists.map { it.id }.toSet()
        val taskIds = b.tasks.map { it.id }.toSet()

        requireRef(b.habits.all { it.groupId == null || it.groupId in groupIds } && b.habits.all { it.name.isNotBlank() }, "habits")
        requireRef(b.habitActivities.all { it.habitId in habitIds }, "activities")
        requireRef(b.habitReminders.all { it.habitId in habitIds && it.minuteOfDay in 0 until 1440 }, "reminders")
        requireRef(b.routineSteps.all { it.routineId in routineIds && it.habitId in habitIds }, "routine steps")
        requireRef(b.timerSessions.all { it.habitId in habitIds }, "timer sessions")
        requireRef(b.tasks.all { it.listId in listIds && it.title.isNotBlank() }, "tasks")
        requireRef(b.taskCompletions.all { it.taskId in taskIds }, "task completions")
        requireRef(b.taskExceptions.all { it.taskId in taskIds }, "task exceptions")
        requireRef(b.journalEntries.all { it.habitId == null || it.habitId in habitIds }, "journal")
        if (b.taskLists.count { it.isInbox } != 1) throw InvalidBackupException(InvalidBackupException.Reason.INTEGRITY, "expected exactly one inbox")
    }

    private fun BackupFile.summary() = DataSummary(habits.size, habitActivities.size, tasks.size, journalEntries.size)
}
