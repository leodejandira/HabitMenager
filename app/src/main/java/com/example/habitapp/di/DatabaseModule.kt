package com.example.habitapp.di

import android.content.Context
import androidx.room.Room
import com.example.habitapp.data.local.dao.BackupDao
import com.example.habitapp.data.local.dao.HabitDao
import com.example.habitapp.data.local.dao.JournalDao
import com.example.habitapp.data.local.dao.TaskDao
import com.example.habitapp.data.local.database.AppDatabase
import com.example.habitapp.data.local.database.SeedDatabaseCallback
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .addCallback(SeedDatabaseCallback(context))
            .build()

    @Provides
    fun provideHabitDao(db: AppDatabase): HabitDao = db.habitDao()

    @Provides
    fun provideTaskDao(db: AppDatabase): TaskDao = db.taskDao()

    @Provides
    fun provideBackupDao(db: AppDatabase): BackupDao = db.backupDao()

    @Provides
    fun provideJournalDao(db: AppDatabase): JournalDao = db.journalDao()
}
