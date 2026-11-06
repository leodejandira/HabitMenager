package com.example.habitapp.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.example.habitapp.data.local.entity.HabitActivityEntity
import com.example.habitapp.data.local.entity.HabitEntity
import com.example.habitapp.data.local.entity.HabitGroupEntity
import com.example.habitapp.data.local.entity.HabitReminderEntity
import com.example.habitapp.data.local.entity.RoutineEntity
import com.example.habitapp.data.local.entity.RoutineHabitCrossRef
import com.example.habitapp.data.local.entity.TimerSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {

    @Query("SELECT * FROM habits ORDER BY sortOrder, createdAt")
    fun observeAll(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE id = :id")
    fun observeById(id: String): Flow<HabitEntity?>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun getById(id: String): HabitEntity?

    @Query("SELECT * FROM habits")
    suspend fun getAll(): List<HabitEntity>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM habits")
    suspend fun maxSortOrder(): Int

    @Upsert
    suspend fun upsert(habit: HabitEntity)

    @Query("UPDATE habits SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: String, sortOrder: Int)

    @Query("UPDATE habits SET archived = :archived, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean, updatedAt: Long)

    @Query("UPDATE habits SET quitStartedAt = :quitStartedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setQuitStartedAt(id: String, quitStartedAt: Long, updatedAt: Long)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun delete(id: String)

    @Transaction
    suspend fun reorder(idsInOrder: List<String>) {
        idsInOrder.forEachIndexed { index, id -> updateSortOrder(id, index) }
    }

    // --- Activities -------------------------------------------------------

    @Query("SELECT * FROM habit_activities ORDER BY date, loggedAt")
    fun observeAllActivities(): Flow<List<HabitActivityEntity>>

    @Query("SELECT * FROM habit_activities WHERE habitId = :habitId ORDER BY date DESC, loggedAt DESC")
    fun observeActivitiesForHabit(habitId: String): Flow<List<HabitActivityEntity>>

    @Query("SELECT * FROM habit_activities WHERE date BETWEEN :fromDay AND :toDay ORDER BY date, loggedAt")
    fun observeActivitiesBetween(fromDay: Long, toDay: Long): Flow<List<HabitActivityEntity>>

    @Query("SELECT * FROM habit_activities WHERE habitId = :habitId AND date = :day")
    suspend fun getActivitiesOn(habitId: String, day: Long): List<HabitActivityEntity>

    @Query("SELECT * FROM habit_activities WHERE id = :id")
    suspend fun getActivity(id: String): HabitActivityEntity?

    @Query("SELECT * FROM habit_activities")
    suspend fun getAllActivities(): List<HabitActivityEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertActivity(activity: HabitActivityEntity)

    @Upsert
    suspend fun upsertActivity(activity: HabitActivityEntity)

    @Query("DELETE FROM habit_activities WHERE id = :id")
    suspend fun deleteActivity(id: String)

    @Query("DELETE FROM habit_activities WHERE habitId = :habitId AND date = :day")
    suspend fun deleteActivitiesOn(habitId: String, day: Long)

    // --- Groups -----------------------------------------------------------

    @Query("SELECT * FROM habit_groups ORDER BY sortOrder, createdAt")
    fun observeGroups(): Flow<List<HabitGroupEntity>>

    @Query("SELECT * FROM habit_groups")
    suspend fun getAllGroups(): List<HabitGroupEntity>

    @Upsert
    suspend fun upsertGroup(group: HabitGroupEntity)

    @Query("UPDATE habit_groups SET collapsed = :collapsed WHERE id = :id")
    suspend fun setGroupCollapsed(id: String, collapsed: Boolean)

    @Query("DELETE FROM habit_groups WHERE id = :id")
    suspend fun deleteGroup(id: String)

    // --- Reminders --------------------------------------------------------

    @Query("SELECT * FROM habit_reminders WHERE habitId = :habitId ORDER BY minuteOfDay")
    fun observeReminders(habitId: String): Flow<List<HabitReminderEntity>>

    @Query("SELECT * FROM habit_reminders WHERE habitId = :habitId")
    suspend fun getReminders(habitId: String): List<HabitReminderEntity>

    @Query("SELECT * FROM habit_reminders")
    suspend fun getAllReminders(): List<HabitReminderEntity>

    @Query("SELECT * FROM habit_reminders WHERE id = :id")
    suspend fun getReminder(id: String): HabitReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminders(reminders: List<HabitReminderEntity>)

    @Query("DELETE FROM habit_reminders WHERE habitId = :habitId")
    suspend fun deleteRemindersFor(habitId: String)

    // --- Routines ---------------------------------------------------------

    @Query("SELECT * FROM routines ORDER BY sortOrder, createdAt")
    fun observeRoutines(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routine_habits ORDER BY routineId, position")
    fun observeRoutineSteps(): Flow<List<RoutineHabitCrossRef>>

    @Query("SELECT * FROM routines")
    suspend fun getAllRoutines(): List<RoutineEntity>

    @Query("SELECT * FROM routine_habits")
    suspend fun getAllRoutineSteps(): List<RoutineHabitCrossRef>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM routines")
    suspend fun maxRoutineSortOrder(): Int

    @Upsert
    suspend fun upsertRoutine(routine: RoutineEntity)

    @Query("DELETE FROM routine_habits WHERE routineId = :routineId")
    suspend fun deleteRoutineSteps(routineId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutineSteps(steps: List<RoutineHabitCrossRef>)

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun deleteRoutine(id: String)

    @Transaction
    suspend fun saveRoutine(routine: RoutineEntity, habitIds: List<String>) {
        upsertRoutine(routine)
        deleteRoutineSteps(routine.id)
        insertRoutineSteps(habitIds.mapIndexed { index, habitId -> RoutineHabitCrossRef(routine.id, habitId, index) })
    }

    // --- Timer sessions ---------------------------------------------------

    @Query("SELECT * FROM timer_sessions WHERE finishedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeActiveTimer(): Flow<TimerSessionEntity?>

    @Query("SELECT * FROM timer_sessions WHERE finishedAt IS NOT NULL AND accumulatedMillis > 0 ORDER BY finishedAt DESC LIMIT :limit")
    fun observeFinishedTimers(limit: Int): Flow<List<TimerSessionEntity>>

    @Query("SELECT * FROM timer_sessions WHERE finishedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun getActiveTimer(): TimerSessionEntity?

    @Upsert
    suspend fun upsertTimer(session: TimerSessionEntity)

    @Delete
    suspend fun deleteTimer(session: TimerSessionEntity)
}
