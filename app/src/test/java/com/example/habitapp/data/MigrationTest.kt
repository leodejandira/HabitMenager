package com.example.habitapp.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.habitapp.data.local.database.AppDatabase
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Builds a real schema-v1 database from the exported schema (app/schemas/.../1.json),
 * fills it with v1 data, then opens it with the current Room database so the
 * v1 -> v2 migration runs and Room validates the resulting schema.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "migration-test.db"

    private fun createV1Database() {
        val schemaFile = File("schemas/com.example.habitapp.data.local.database.AppDatabase/1.json")
        val schema = Json.parseToJsonElement(schemaFile.readText()).jsonObject["database"]!!.jsonObject
        context.deleteDatabase(dbName)
        val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(dbName).apply { parentFile?.mkdirs() }, null)
        schema["entities"]!!.jsonArray.forEach { entity ->
            val table = entity.jsonObject["tableName"]!!.jsonPrimitive.content
            db.execSQL(entity.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            entity.jsonObject["indices"]?.jsonArray?.forEach { index ->
                db.execSQL(index.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            }
        }
        schema["setupQueries"]!!.jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
        db.execSQL("INSERT INTO task_lists VALUES ('inbox', 'Inbox', 'slate', 0, 0, 1, 0)")
        db.execSQL(
            "INSERT INTO habits (id, name, description, emoji, colorKey, type, unit, targetValue, stepValue, frequencyType, weekdaysMask, " +
                "timesPerPeriod, startDate, endDate, groupId, sortOrder, archived, quitStartedAt, createdAt, updatedAt) VALUES " +
                "('gym', 'Gym', '', '💪', 'evergreen', 'BOOLEAN', '', 1.0, 1.0, 'TIMES_PER_WEEK', 127, 3, 20000, NULL, NULL, 0, 0, NULL, 0, 0)," +
                "('water', 'Water', '', '💧', 'ocean', 'NUMERIC', 'ml', 2000.0, 250.0, 'DAILY', 127, 1, 20000, NULL, NULL, 1, 0, NULL, 0, 0)",
        )
        db.execSQL("INSERT INTO habit_activities VALUES ('a1', 'water', 20400, 750.0, 2000.0, 0, NULL, 0, 'UTC')")
        db.version = 1
        db.close()
    }

    @Test
    fun migrateFrom1To2KeepsDataAndConvertsWeeklyGoals() = runBlocking {
        createV1Database()
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        val habits = db.habitDao().getAll().associateBy { it.id }
        val gym = habits.getValue("gym")
        assertEquals("WEEK", gym.goalPeriod)
        assertEquals(3.0, gym.targetValue, 0.0)
        assertEquals("DAILY", gym.frequencyType)
        val water = habits.getValue("water")
        assertEquals("DAY", water.goalPeriod)
        assertEquals("AT_LEAST", water.goalType)
        assertEquals(750.0, db.habitDao().getAllActivities().single().value, 0.0)
        assertEquals("checklist", db.taskDao().getAllLists().single().icon)
        db.close()
        context.deleteDatabase(dbName)
        Unit
    }
}
