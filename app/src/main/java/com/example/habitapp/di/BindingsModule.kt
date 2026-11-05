package com.example.habitapp.di

import com.example.habitapp.data.preferences.DataStoreUserPreferencesRepository
import com.example.habitapp.data.repository.HabitRepositoryImpl
import com.example.habitapp.data.repository.JournalRepositoryImpl
import com.example.habitapp.data.repository.RoutineRepositoryImpl
import com.example.habitapp.data.repository.TaskRepositoryImpl
import com.example.habitapp.data.repository.TimerRepositoryImpl
import com.example.habitapp.data.scheduler.AlarmReminderScheduler
import com.example.habitapp.domain.repository.HabitRepository
import com.example.habitapp.domain.repository.JournalRepository
import com.example.habitapp.domain.repository.ReminderScheduler
import com.example.habitapp.domain.repository.RoutineRepository
import com.example.habitapp.domain.repository.TaskRepository
import com.example.habitapp.domain.repository.TimerRepository
import com.example.habitapp.domain.repository.UserPreferencesRepository
import com.example.habitapp.domain.util.SystemTimeProvider
import com.example.habitapp.domain.util.TimeProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {

    @Binds
    @Singleton
    abstract fun bindUserPreferencesRepository(
        impl: DataStoreUserPreferencesRepository,
    ): UserPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindTimeProvider(impl: SystemTimeProvider): TimeProvider

    @Binds
    @Singleton
    abstract fun bindHabitRepository(impl: HabitRepositoryImpl): HabitRepository

    @Binds
    @Singleton
    abstract fun bindRoutineRepository(impl: RoutineRepositoryImpl): RoutineRepository

    @Binds
    @Singleton
    abstract fun bindTimerRepository(impl: TimerRepositoryImpl): TimerRepository

    @Binds
    @Singleton
    abstract fun bindTaskRepository(impl: TaskRepositoryImpl): TaskRepository

    @Binds
    @Singleton
    abstract fun bindJournalRepository(impl: JournalRepositoryImpl): JournalRepository

    @Binds
    @Singleton
    abstract fun bindReminderScheduler(impl: AlarmReminderScheduler): ReminderScheduler
}
