package com.example.habitapp.data.local.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.habitapp.R
import com.example.habitapp.data.local.dao.BackupDao
import com.example.habitapp.data.local.dao.HabitDao
import com.example.habitapp.data.local.dao.JournalDao
import com.example.habitapp.data.local.dao.TaskDao
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
import java.util.UUID

@Database(
    entities = [
        HabitGroupEntity::class,
        HabitEntity::class,
        HabitActivityEntity::class,
        HabitReminderEntity::class,
        RoutineEntity::class,
        RoutineHabitCrossRef::class,
        TimerSessionEntity::class,
        TaskListEntity::class,
        TaskEntity::class,
        TaskCompletionEntity::class,
        TaskExceptionEntity::class,
        JournalEntryEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun taskDao(): TaskDao
    abstract fun journalDao(): JournalDao
    abstract fun backupDao(): BackupDao

    companion object {
        const val VERSION = 1
        const val NAME = "steady.db"

        /**
         * Schema migrations. Version 1 is the first released schema; every later
         * version must add a Migration here (destructive fallback is never enabled,
         * so user data is never silently dropped).
         */
        val MIGRATIONS = emptyArray<Migration>()
    }
}

/** The predefined task lists, localized with the current app language. */
fun defaultTaskLists(context: Context, now: Long): List<TaskListEntity> = listOf(
    Triple(R.string.list_inbox, "slate", true),
    Triple(R.string.list_personal, "evergreen", false),
    Triple(R.string.list_work, "indigo", false),
    Triple(R.string.list_shopping, "amber", false),
    Triple(R.string.list_errands, "coral", false),
    Triple(R.string.list_other, "violet", false),
).mapIndexed { index, (nameRes, color, isInbox) ->
    TaskListEntity(
        id = UUID.randomUUID().toString(),
        name = context.getString(nameRes),
        colorKey = color,
        sortOrder = index,
        archived = false,
        isInbox = isInbox,
        createdAt = now + index,
    )
}

/** Seeds the predefined task lists when the database is first created. */
class SeedDatabaseCallback(private val context: Context) : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        defaultTaskLists(context, System.currentTimeMillis()).forEach { list ->
            val values = ContentValues().apply {
                put("id", list.id)
                put("name", list.name)
                put("colorKey", list.colorKey)
                put("sortOrder", list.sortOrder)
                put("archived", 0)
                put("isInbox", if (list.isInbox) 1 else 0)
                put("createdAt", list.createdAt)
            }
            db.insert("task_lists", SQLiteDatabase.CONFLICT_ABORT, values)
        }
    }
}
