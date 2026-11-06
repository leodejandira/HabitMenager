package com.example.habitapp.data.repository

import androidx.room.withTransaction
import com.example.habitapp.data.local.dao.HabitDao
import com.example.habitapp.data.local.database.AppDatabase
import com.example.habitapp.data.local.entity.HabitActivityEntity
import com.example.habitapp.data.local.entity.HabitGroupEntity
import com.example.habitapp.data.local.entity.RoutineEntity
import com.example.habitapp.data.local.entity.TimerSessionEntity
import com.example.habitapp.domain.model.GoalPeriod
import com.example.habitapp.domain.model.GoalType
import com.example.habitapp.domain.model.Habit
import com.example.habitapp.domain.model.HabitActivity
import com.example.habitapp.domain.model.HabitGroup
import com.example.habitapp.domain.model.HabitReminder
import com.example.habitapp.domain.model.HabitType
import com.example.habitapp.domain.model.Routine
import com.example.habitapp.domain.model.TimerMode
import com.example.habitapp.domain.model.TimerSession
import com.example.habitapp.domain.repository.HabitRepository
import com.example.habitapp.domain.repository.ReminderScheduler
import com.example.habitapp.domain.repository.RoutineRepository
import com.example.habitapp.domain.repository.TimerRepository
import com.example.habitapp.domain.util.HabitCalculator
import com.example.habitapp.domain.util.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class HabitRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val dao: HabitDao,
    private val time: TimeProvider,
    private val reminderScheduler: ReminderScheduler,
) : HabitRepository {

    override fun observeHabits(): Flow<List<Habit>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeHabit(id: String): Flow<Habit?> = dao.observeById(id).map { it?.toDomain() }

    override fun observeAllActivities(): Flow<List<HabitActivity>> =
        dao.observeAllActivities().map { list -> list.map { it.toDomain() } }

    override fun observeActivitiesForHabit(habitId: String): Flow<List<HabitActivity>> =
        dao.observeActivitiesForHabit(habitId).map { list -> list.map { it.toDomain() } }

    override fun observeReminders(habitId: String): Flow<List<HabitReminder>> =
        dao.observeReminders(habitId).map { list -> list.map { it.toDomain() } }

    override fun observeGroups(): Flow<List<HabitGroup>> = dao.observeGroups().map { list -> list.map { it.toDomain() } }

    override suspend fun getHabit(id: String): Habit? = dao.getById(id)?.toDomain()

    override suspend fun saveHabit(habit: Habit, reminders: List<HabitReminder>): String {
        require(habit.name.isNotBlank()) { "Habit name must not be blank" }
        val now = time.nowInstant().toEpochMilli()
        val existing = dao.getById(habit.id)
        val toSave = if (existing == null) {
            habit.copy(
                sortOrder = dao.maxSortOrder() + 1,
                quitStartedAt = if (habit.type == HabitType.QUIT) habit.quitStartedAt ?: time.nowInstant() else null,
            )
        } else {
            habit
        }
        db.withTransaction {
            dao.upsert(toSave.toEntity(updatedAt = now))
            dao.deleteRemindersFor(habit.id)
            dao.insertReminders(reminders.map { it.copy(habitId = habit.id).toEntity() })
        }
        reminderScheduler.rescheduleHabit(habit.id)
        return habit.id
    }

    override suspend fun duplicateHabit(id: String): String? {
        val source = dao.getById(id)?.toDomain() ?: return null
        val newId = UUID.randomUUID().toString()
        val reminders = dao.getReminders(id).map { it.toDomain().copy(id = UUID.randomUUID().toString(), habitId = newId) }
        return saveHabit(
            source.copy(id = newId, name = source.name + " (2)", createdAt = time.nowInstant(), quitStartedAt = null),
            reminders,
        )
    }

    override suspend fun setArchived(id: String, archived: Boolean) {
        dao.setArchived(id, archived, time.nowInstant().toEpochMilli())
        if (archived) reminderScheduler.cancelHabit(id) else reminderScheduler.rescheduleHabit(id)
    }

    override suspend fun deleteHabit(id: String) {
        reminderScheduler.cancelHabit(id)
        dao.delete(id) // Activities, reminders and routine steps cascade.
    }

    override suspend fun reorderHabits(idsInOrder: List<String>) = dao.reorder(idsInOrder)

    override suspend fun toggleCompletion(habitId: String, date: LocalDate) {
        val habit = dao.getById(habitId)?.toDomain() ?: return
        db.withTransaction {
            val entries = dao.getActivitiesOn(habitId, date.toEpochDay()).map { it.toDomain() }
            val progress = HabitCalculator.dayProgress(habit, date, entries)
            if (progress.total > 0 || progress.skipped) {
                dao.deleteActivitiesOn(habitId, date.toEpochDay())
            } else {
                // Day goals jump straight to the target; longer goals log one step for this day.
                val amount = if (habit.goalPeriod == GoalPeriod.DAY && habit.goalType == GoalType.AT_LEAST) habit.effectiveTarget else habit.step
                insert(habit, date, amount, note = null)
            }
        }
    }

    override suspend fun addProgress(habitId: String, date: LocalDate, amount: Double, note: String?) {
        if (amount == 0.0 && note.isNullOrBlank()) return
        val habit = dao.getById(habitId)?.toDomain() ?: return
        db.withTransaction {
            val entries = dao.getActivitiesOn(habitId, date.toEpochDay()).map { it.toDomain() }
            val currentTotal = entries.filter { !it.skipped }.sumOf { it.value }.coerceAtLeast(0.0)
            // Never let the day's total go negative.
            val applied = if (amount < 0) maxOf(amount, -currentTotal) else amount
            if (applied != 0.0 || !note.isNullOrBlank()) insert(habit, date, applied, note)
        }
    }

    override suspend fun completeDay(habitId: String, date: LocalDate) {
        val habit = dao.getById(habitId)?.toDomain() ?: return
        db.withTransaction {
            val entries = dao.getActivitiesOn(habitId, date.toEpochDay()).map { it.toDomain() }
            val progress = HabitCalculator.dayProgress(habit, date, entries)
            val missing = habit.dailyTarget - progress.total
            if (habit.goalType == GoalType.AT_LEAST && missing > 0) insert(habit, date, missing, null)
        }
    }

    override suspend fun clearDay(habitId: String, date: LocalDate) =
        dao.deleteActivitiesOn(habitId, date.toEpochDay())

    override suspend fun skipDay(habitId: String, date: LocalDate) {
        val habit = dao.getById(habitId)?.toDomain() ?: return
        db.withTransaction {
            dao.deleteActivitiesOn(habitId, date.toEpochDay())
            dao.insertActivity(
                HabitActivityEntity(
                    id = UUID.randomUUID().toString(),
                    habitId = habitId,
                    date = date.toEpochDay(),
                    value = 0.0,
                    targetSnapshot = habit.effectiveTarget,
                    skipped = true,
                    note = null,
                    loggedAt = time.nowInstant().toEpochMilli(),
                    zoneId = time.zone().id,
                ),
            )
        }
    }

    override suspend fun updateActivity(activityId: String, date: LocalDate, value: Double, note: String?) {
        val existing = dao.getActivity(activityId) ?: return
        dao.upsertActivity(existing.copy(date = date.toEpochDay(), value = value, note = note?.takeIf { it.isNotBlank() }))
    }

    override suspend fun deleteActivity(activityId: String) = dao.deleteActivity(activityId)

    override suspend fun recordRelapse(habitId: String, at: Instant, note: String?) {
        val date = at.atZone(time.zone()).toLocalDate()
        dao.insertActivity(
            HabitActivityEntity(
                id = UUID.randomUUID().toString(),
                habitId = habitId,
                date = date.toEpochDay(),
                value = 1.0,
                targetSnapshot = 1.0,
                skipped = false,
                note = note?.takeIf { it.isNotBlank() },
                loggedAt = at.toEpochMilli(),
                zoneId = time.zone().id,
            ),
        )
    }

    private suspend fun insert(habit: Habit, date: LocalDate, value: Double, note: String?) {
        dao.insertActivity(
            HabitActivityEntity(
                id = UUID.randomUUID().toString(),
                habitId = habit.id,
                date = date.toEpochDay(),
                value = value,
                targetSnapshot = habit.effectiveTarget,
                skipped = false,
                note = note?.takeIf { it.isNotBlank() },
                loggedAt = time.nowInstant().toEpochMilli(),
                zoneId = time.zone().id,
            ),
        )
    }

    override suspend fun saveGroup(group: HabitGroup) {
        require(group.name.isNotBlank()) { "Group name must not be blank" }
        dao.upsertGroup(
            HabitGroupEntity(
                id = group.id,
                name = group.name.trim(),
                sortOrder = group.sortOrder,
                collapsed = group.collapsed,
                createdAt = time.nowInstant().toEpochMilli(),
            ),
        )
    }

    override suspend fun deleteGroup(id: String) = dao.deleteGroup(id)

    override suspend fun setGroupCollapsed(id: String, collapsed: Boolean) = dao.setGroupCollapsed(id, collapsed)
}

@Singleton
class RoutineRepositoryImpl @Inject constructor(
    private val dao: HabitDao,
    private val time: TimeProvider,
) : RoutineRepository {

    override fun observeRoutines(): Flow<List<Routine>> =
        combine(dao.observeRoutines(), dao.observeRoutineSteps()) { routines, steps ->
            val byRoutine = steps.groupBy { it.routineId }
            routines.map { routine ->
                Routine(
                    id = routine.id,
                    name = routine.name,
                    emoji = routine.emoji,
                    estimatedMinutes = routine.estimatedMinutes,
                    sortOrder = routine.sortOrder,
                    habitIds = byRoutine[routine.id].orEmpty().sortedBy { it.position }.map { it.habitId },
                )
            }
        }

    override suspend fun saveRoutine(routine: Routine): String {
        require(routine.name.isNotBlank()) { "Routine name must not be blank" }
        val existing = dao.getAllRoutines().firstOrNull { it.id == routine.id }
        dao.saveRoutine(
            RoutineEntity(
                id = routine.id,
                name = routine.name.trim(),
                emoji = routine.emoji,
                estimatedMinutes = routine.estimatedMinutes,
                sortOrder = existing?.sortOrder ?: (dao.maxRoutineSortOrder() + 1),
                createdAt = existing?.createdAt ?: time.nowInstant().toEpochMilli(),
            ),
            routine.habitIds.distinct(),
        )
        return routine.id
    }

    override suspend fun deleteRoutine(id: String) = dao.deleteRoutine(id)
}

@Singleton
class TimerRepositoryImpl @Inject constructor(
    private val dao: HabitDao,
    private val habits: HabitRepository,
    private val time: TimeProvider,
) : TimerRepository {

    override fun observeActiveTimer(): Flow<TimerSession?> = dao.observeActiveTimer().map { it?.toDomain() }

    override suspend fun start(habitId: String, mode: TimerMode, countdownMillis: Long?) {
        dao.getActiveTimer()?.let { dao.deleteTimer(it) }
        val now = time.nowInstant().toEpochMilli()
        dao.upsertTimer(
            TimerSessionEntity(
                id = UUID.randomUUID().toString(),
                habitId = habitId,
                mode = mode.name,
                countdownMillis = if (mode == TimerMode.COUNTDOWN) countdownMillis else null,
                runningSince = now,
                accumulatedMillis = 0,
                startedAt = now,
                finishedAt = null,
            ),
        )
    }

    override suspend fun pause() {
        val active = dao.getActiveTimer() ?: return
        val since = active.runningSince ?: return
        val now = time.nowInstant().toEpochMilli()
        var accumulated = active.accumulatedMillis + (now - since).coerceAtLeast(0)
        active.countdownMillis?.let { accumulated = accumulated.coerceAtMost(it) }
        dao.upsertTimer(active.copy(runningSince = null, accumulatedMillis = accumulated))
    }

    override suspend fun resume() {
        val active = dao.getActiveTimer() ?: return
        if (active.runningSince != null) return
        dao.upsertTimer(active.copy(runningSince = time.nowInstant().toEpochMilli()))
    }

    override suspend fun reset() {
        val active = dao.getActiveTimer() ?: return
        dao.upsertTimer(active.copy(runningSince = null, accumulatedMillis = 0))
    }

    override suspend fun discard() {
        dao.getActiveTimer()?.let { dao.deleteTimer(it) }
    }

    override suspend fun finishAndSave(): Int {
        val active = dao.getActiveTimer() ?: return 0
        val session = active.toDomain()
        val now = time.nowInstant()
        var elapsed = session.elapsedMillis(now)
        session.countdownMillis?.let { elapsed = elapsed.coerceAtMost(it) }
        val minutes = (elapsed / 60_000.0).roundToInt()
        dao.upsertTimer(
            active.copy(
                runningSince = null,
                accumulatedMillis = elapsed,
                finishedAt = now.toEpochMilli(),
            ),
        )
        if (minutes > 0) {
            habits.addProgress(session.habitId, now.atZone(time.zone()).toLocalDate(), minutes.toDouble(), null)
        }
        return minutes
    }
}
