package com.example.habitapp.data.repository

import androidx.room.withTransaction
import com.example.habitapp.data.local.dao.TaskDao
import com.example.habitapp.data.local.database.AppDatabase
import com.example.habitapp.data.local.entity.TaskCompletionEntity
import com.example.habitapp.data.local.entity.TaskExceptionEntity
import com.example.habitapp.data.local.entity.TaskListEntity
import com.example.habitapp.domain.model.EditScope
import com.example.habitapp.domain.model.Task
import com.example.habitapp.domain.model.TaskList
import com.example.habitapp.domain.model.TaskRecurrence
import com.example.habitapp.domain.repository.ReminderScheduler
import com.example.habitapp.domain.repository.TaskData
import com.example.habitapp.domain.repository.TaskRepository
import com.example.habitapp.domain.util.TaskRecurrenceCalculator
import com.example.habitapp.domain.util.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val dao: TaskDao,
    private val time: TimeProvider,
    private val reminderScheduler: ReminderScheduler,
) : TaskRepository {

    override fun observeLists(): Flow<List<TaskList>> = dao.observeLists().map { list -> list.map { it.toDomain() } }

    override fun observeTaskData(): Flow<TaskData> = combine(
        dao.observeTasks(),
        dao.observeCompletions(),
        dao.observeExceptions(),
    ) { tasks, completions, exceptions ->
        TaskData(
            tasks = tasks.map { it.toDomain() },
            completions = completions.groupBy { it.taskId }.mapValues { (_, list) ->
                list.associate { LocalDate.ofEpochDay(it.occurrenceDate) to Instant.ofEpochMilli(it.completedAt) }
            },
            exceptions = exceptions.groupBy { it.taskId }.mapValues { (_, list) ->
                list.map { LocalDate.ofEpochDay(it.occurrenceDate) }.toSet()
            },
        )
    }

    override suspend fun getTask(id: String): Task? = dao.getTask(id)?.toDomain()

    override suspend fun saveTask(task: Task): String {
        require(task.title.isNotBlank()) { "Task title must not be blank" }
        val normalized = if (task.dueDate == null) task.copy(recurrence = TaskRecurrence(), dueTime = null) else task
        dao.upsertTask(normalized.toEntity(time.nowInstant().toEpochMilli()))
        reminderScheduler.rescheduleTask(task.id)
        return task.id
    }

    override suspend fun updateRecurringTask(updated: Task, occurrenceDate: LocalDate, scope: EditScope) {
        val original = dao.getTask(updated.id)?.toDomain() ?: return
        if (!original.recurrence.isRecurring) {
            saveTask(updated)
            return
        }
        val now = time.nowInstant().toEpochMilli()
        when (scope) {
            EditScope.ALL -> saveTask(updated)
            EditScope.THIS_OCCURRENCE -> {
                // The editor shows the series' anchor date; an unchanged date means "keep this occurrence's day".
                val newDate = if (updated.dueDate == original.dueDate) occurrenceDate else updated.dueDate ?: occurrenceDate
                detachOccurrence(original, updated, occurrenceDate, newDate)
            }
            EditScope.THIS_AND_FUTURE -> {
                if (occurrenceDate == original.dueDate) {
                    saveTask(updated)
                    return
                }
                // End the old series the day before, start a new series at this occurrence.
                val newSeries = updated.copy(
                    id = UUID.randomUUID().toString(),
                    dueDate = if (updated.dueDate == original.dueDate) occurrenceDate else updated.dueDate,
                    createdAt = time.nowInstant(),
                    completedAt = null,
                )
                db.withTransaction {
                    dao.upsertTask(
                        original.copy(recurrence = original.recurrence.copy(until = occurrenceDate.minusDays(1)))
                            .toEntity(now),
                    )
                    dao.deleteCompletionsFrom(original.id, occurrenceDate.toEpochDay())
                    dao.upsertTask(newSeries.toEntity(now))
                }
                reminderScheduler.rescheduleTask(original.id)
                reminderScheduler.rescheduleTask(newSeries.id)
            }
        }
    }

    /** Removes one occurrence from its series and recreates it as a one-off task on [newDate]. */
    private suspend fun detachOccurrence(original: Task, updated: Task, occurrenceDate: LocalDate, newDate: LocalDate) {
        val now = time.nowInstant().toEpochMilli()
        val wasCompleted = dao.getAllCompletions().any { it.taskId == original.id && it.occurrenceDate == occurrenceDate.toEpochDay() }
        val detached = updated.copy(
            id = UUID.randomUUID().toString(),
            recurrence = TaskRecurrence(),
            dueDate = newDate,
            completedAt = if (wasCompleted) time.nowInstant() else null,
            createdAt = time.nowInstant(),
        )
        db.withTransaction {
            dao.insertException(TaskExceptionEntity(original.id, occurrenceDate.toEpochDay()))
            dao.deleteCompletion(original.id, occurrenceDate.toEpochDay())
            dao.upsertTask(detached.toEntity(now))
        }
        reminderScheduler.rescheduleTask(detached.id)
    }

    override suspend fun setOccurrenceCompleted(taskId: String, occurrenceDate: LocalDate?, completed: Boolean) {
        val task = dao.getTask(taskId)?.toDomain() ?: return
        val now = time.nowInstant().toEpochMilli()
        if (task.recurrence.isRecurring && occurrenceDate != null) {
            if (completed) {
                dao.insertCompletion(TaskCompletionEntity(taskId, occurrenceDate.toEpochDay(), now))
            } else {
                dao.deleteCompletion(taskId, occurrenceDate.toEpochDay())
            }
        } else {
            dao.upsertTask(task.copy(completedAt = if (completed) Instant.ofEpochMilli(now) else null).toEntity(now))
        }
        reminderScheduler.rescheduleTask(taskId)
    }

    override suspend fun deleteTask(taskId: String, occurrenceDate: LocalDate?, scope: EditScope) {
        val task = dao.getTask(taskId)?.toDomain() ?: return
        if (!task.recurrence.isRecurring || occurrenceDate == null || scope == EditScope.ALL ||
            (scope == EditScope.THIS_AND_FUTURE && occurrenceDate == task.dueDate)
        ) {
            reminderScheduler.cancelTask(taskId)
            dao.deleteTask(taskId)
            return
        }
        val now = time.nowInstant().toEpochMilli()
        when (scope) {
            EditScope.THIS_OCCURRENCE -> db.withTransaction {
                dao.insertException(TaskExceptionEntity(taskId, occurrenceDate.toEpochDay()))
                dao.deleteCompletion(taskId, occurrenceDate.toEpochDay())
            }
            EditScope.THIS_AND_FUTURE -> db.withTransaction {
                dao.upsertTask(task.copy(recurrence = task.recurrence.copy(until = occurrenceDate.minusDays(1))).toEntity(now))
                dao.deleteCompletionsFrom(taskId, occurrenceDate.toEpochDay())
            }
            EditScope.ALL -> Unit
        }
        reminderScheduler.rescheduleTask(taskId)
    }

    override suspend fun reschedule(taskId: String, fromDate: LocalDate?, toDate: LocalDate) {
        val task = dao.getTask(taskId)?.toDomain() ?: return
        if (task.recurrence.isRecurring && fromDate != null) {
            if (fromDate == toDate) return
            val exceptions = dao.getAllExceptions().filter { it.taskId == taskId }
                .map { LocalDate.ofEpochDay(it.occurrenceDate) }.toSet()
            if (!TaskRecurrenceCalculator.occursOn(task, fromDate, exceptions)) return
            detachOccurrence(task, task, fromDate, toDate)
        } else {
            saveTask(task.copy(dueDate = toDate))
        }
    }

    override suspend fun saveList(list: TaskList) {
        require(list.name.isNotBlank()) { "List name must not be blank" }
        val existing = dao.getAllLists().firstOrNull { it.id == list.id }
        dao.upsertList(
            TaskListEntity(
                id = list.id,
                name = list.name.trim(),
                colorKey = list.colorKey,
                sortOrder = existing?.sortOrder ?: (dao.maxListSortOrder() + 1),
                archived = list.archived,
                isInbox = existing?.isInbox ?: false,
                createdAt = existing?.createdAt ?: time.nowInstant().toEpochMilli(),
            ),
        )
    }

    override suspend fun deleteList(id: String) {
        val inbox = dao.getInbox() ?: error("Inbox list is missing")
        require(inbox.id != id) { "The inbox cannot be deleted" }
        dao.deleteListMovingTasks(id, inbox.id)
    }

    override suspend fun reorderLists(idsInOrder: List<String>) = dao.reorderLists(idsInOrder)

    override suspend fun moveTask(taskId: String, listId: String) {
        val task = dao.getTask(taskId) ?: return
        dao.upsertTask(task.copy(listId = listId, updatedAt = time.nowInstant().toEpochMilli()))
    }
}
