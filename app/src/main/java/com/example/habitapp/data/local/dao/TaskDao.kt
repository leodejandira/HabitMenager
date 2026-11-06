package com.example.habitapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.example.habitapp.data.local.entity.TaskCompletionEntity
import com.example.habitapp.data.local.entity.TaskEntity
import com.example.habitapp.data.local.entity.TaskExceptionEntity
import com.example.habitapp.data.local.entity.TaskListEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    // --- Lists ------------------------------------------------------------

    @Query("SELECT * FROM task_lists ORDER BY sortOrder, createdAt")
    fun observeLists(): Flow<List<TaskListEntity>>

    @Query("SELECT * FROM task_lists")
    suspend fun getAllLists(): List<TaskListEntity>

    @Query("SELECT * FROM task_lists WHERE isInbox = 1 LIMIT 1")
    suspend fun getInbox(): TaskListEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM task_lists")
    suspend fun maxListSortOrder(): Int

    @Upsert
    suspend fun upsertList(list: TaskListEntity)

    @Query("UPDATE task_lists SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateListSortOrder(id: String, sortOrder: Int)

    @Query("DELETE FROM task_lists WHERE id = :id AND isInbox = 0")
    suspend fun deleteList(id: String)

    @Query("UPDATE tasks SET listId = :toListId WHERE listId = :fromListId")
    suspend fun moveAllTasks(fromListId: String, toListId: String)

    @Query("UPDATE habits SET listId = NULL WHERE listId = :listId")
    suspend fun detachHabitsFromList(listId: String)

    /** Moves the list's tasks to the inbox, unlinks its habits, then deletes the list, atomically. */
    @Transaction
    suspend fun deleteListMovingTasks(listId: String, inboxId: String) {
        moveAllTasks(listId, inboxId)
        detachHabitsFromList(listId)
        deleteList(listId)
    }

    @Transaction
    suspend fun reorderLists(idsInOrder: List<String>) {
        idsInOrder.forEachIndexed { index, id -> updateListSortOrder(id, index) }
    }

    // --- Tasks ------------------------------------------------------------

    @Query("SELECT * FROM tasks ORDER BY createdAt")
    fun observeTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTask(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observeTask(id: String): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks")
    suspend fun getAllTasks(): List<TaskEntity>

    @Upsert
    suspend fun upsertTask(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTask(id: String)

    // --- Recurring occurrences --------------------------------------------

    @Query("SELECT * FROM task_completions")
    fun observeCompletions(): Flow<List<TaskCompletionEntity>>

    @Query("SELECT * FROM task_completions")
    suspend fun getAllCompletions(): List<TaskCompletionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompletion(completion: TaskCompletionEntity)

    @Query("DELETE FROM task_completions WHERE taskId = :taskId AND occurrenceDate = :day")
    suspend fun deleteCompletion(taskId: String, day: Long)

    @Query("DELETE FROM task_completions WHERE taskId = :taskId AND occurrenceDate >= :fromDay")
    suspend fun deleteCompletionsFrom(taskId: String, fromDay: Long)

    @Query("SELECT * FROM task_exceptions")
    fun observeExceptions(): Flow<List<TaskExceptionEntity>>

    @Query("SELECT * FROM task_exceptions")
    suspend fun getAllExceptions(): List<TaskExceptionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertException(exception: TaskExceptionEntity)
}
