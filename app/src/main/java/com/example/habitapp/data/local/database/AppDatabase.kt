package com.example.habitapp.data.local.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
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
    autoMigrations = [AutoMigration(from = 1, to = 2, spec = Migration1To2::class)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun taskDao(): TaskDao
    abstract fun journalDao(): JournalDao
    abstract fun backupDao(): BackupDao

    companion object {
        const val VERSION = 2
        const val NAME = "steady.db"

        /**
         * Manual migrations (automatic ones are declared on @Database). Destructive
         * fallback is never enabled, so user data is never silently dropped.
         */
        val MIGRATIONS = emptyArray<Migration>()
    }
}

/**
 * v1 -> v2: adds period goals, limit goals, list/priority/timer on habits and list icons.
 * "N times per week/month" habits become weekly/monthly goals with the same total.
 */
class Migration1To2 : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "UPDATE habits SET goalPeriod = 'WEEK', targetValue = CASE WHEN type = 'BOOLEAN' THEN timesPerPeriod ELSE targetValue * timesPerPeriod END, " +
                "frequencyType = 'DAILY' WHERE frequencyType = 'TIMES_PER_WEEK'",
        )
        db.execSQL(
            "UPDATE habits SET goalPeriod = 'MONTH', targetValue = CASE WHEN type = 'BOOLEAN' THEN timesPerPeriod ELSE targetValue * timesPerPeriod END, " +
                "frequencyType = 'DAILY' WHERE frequencyType = 'TIMES_PER_MONTH'",
        )
        db.execSQL("UPDATE task_lists SET icon = 'checklist' WHERE icon = ''")
    }
}

/** The predefined task lists, localized with the current app language. */
fun defaultTaskLists(context: Context, now: Long): List<TaskListEntity> = listOf(
    DefaultList(R.string.list_inbox, "periwinkle", "checklist", true),
    DefaultList(R.string.list_personal, "teal", "person", false),
    DefaultList(R.string.list_work, "amber", "work", false),
    DefaultList(R.string.list_shopping, "coral", "shopping", false),
    DefaultList(R.string.list_errands, "sky", "run", false),
    DefaultList(R.string.list_other, "lilac", "star", false),
).mapIndexed { index, (nameRes, color, icon, isInbox) ->
    TaskListEntity(
        id = UUID.randomUUID().toString(),
        name = context.getString(nameRes),
        colorKey = color,
        sortOrder = index,
        archived = false,
        isInbox = isInbox,
        createdAt = now + index,
        icon = icon,
    )
}

private data class DefaultList(val nameRes: Int, val color: String, val icon: String, val isInbox: Boolean)

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
                put("icon", list.icon)
            }
            db.insert("task_lists", SQLiteDatabase.CONFLICT_ABORT, values)
        }
    }
}
