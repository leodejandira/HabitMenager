package com.example.habitapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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

/** Bulk operations used by backup/restore and reset. Always called inside a transaction. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM timer_sessions WHERE finishedAt IS NOT NULL")
    suspend fun finishedTimerSessions(): List<TimerSessionEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertGroups(items: List<HabitGroupEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertHabits(items: List<HabitEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertActivities(items: List<HabitActivityEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertReminders(items: List<HabitReminderEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertRoutines(items: List<RoutineEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertRoutineSteps(items: List<RoutineHabitCrossRef>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertTimerSessions(items: List<TimerSessionEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertTaskLists(items: List<TaskListEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertTasks(items: List<TaskEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertCompletions(items: List<TaskCompletionEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertExceptions(items: List<TaskExceptionEntity>)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertJournal(items: List<JournalEntryEntity>)

    // Children first so foreign keys never block a delete.
    @Query("DELETE FROM journal_entries") suspend fun deleteJournal()
    @Query("DELETE FROM task_exceptions") suspend fun deleteExceptions()
    @Query("DELETE FROM task_completions") suspend fun deleteCompletions()
    @Query("DELETE FROM tasks") suspend fun deleteTasks()
    @Query("DELETE FROM task_lists") suspend fun deleteTaskLists()
    @Query("DELETE FROM timer_sessions") suspend fun deleteTimerSessions()
    @Query("DELETE FROM routine_habits") suspend fun deleteRoutineSteps()
    @Query("DELETE FROM routines") suspend fun deleteRoutines()
    @Query("DELETE FROM habit_reminders") suspend fun deleteReminders()
    @Query("DELETE FROM habit_activities") suspend fun deleteActivities()
    @Query("DELETE FROM habits") suspend fun deleteHabits()
    @Query("DELETE FROM habit_groups") suspend fun deleteGroups()

    suspend fun deleteAll() {
        deleteJournal()
        deleteExceptions()
        deleteCompletions()
        deleteTasks()
        deleteTaskLists()
        deleteTimerSessions()
        deleteRoutineSteps()
        deleteRoutines()
        deleteReminders()
        deleteActivities()
        deleteHabits()
        deleteGroups()
    }
}
