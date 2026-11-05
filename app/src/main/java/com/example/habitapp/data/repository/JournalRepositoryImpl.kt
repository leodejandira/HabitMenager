package com.example.habitapp.data.repository

import com.example.habitapp.data.local.dao.JournalDao
import com.example.habitapp.data.local.entity.JournalEntryEntity
import com.example.habitapp.domain.model.JournalEntry
import com.example.habitapp.domain.model.JournalFilter
import com.example.habitapp.domain.repository.JournalQuery
import com.example.habitapp.domain.repository.JournalRepository
import com.example.habitapp.domain.util.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JournalRepositoryImpl @Inject constructor(
    private val dao: JournalDao,
    private val time: TimeProvider,
) : JournalRepository {

    override fun search(query: JournalQuery): Flow<List<JournalEntry>> {
        val escaped = query.text.trim()
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        return dao.search(
            query = escaped,
            bookmarkedOnly = query.filter == JournalFilter.BOOKMARKED,
            habitFilter = when (query.filter) {
                JournalFilter.HABIT_NOTES -> 1
                JournalFilter.DAILY -> 2
                else -> 0
            },
            habitId = query.habitId,
            fromDay = query.from?.toEpochDay(),
            toDay = query.to?.toEpochDay(),
        ).map { list -> list.map { it.toDomain() } }
    }

    override fun observeForDay(date: LocalDate): Flow<List<JournalEntry>> =
        dao.observeForDay(date.toEpochDay()).map { list -> list.map { it.toDomain() } }

    override fun observeForHabit(habitId: String): Flow<List<JournalEntry>> =
        dao.observeForHabit(habitId).map { list -> list.map { it.toDomain() } }

    override fun observeDaysWithEntries(from: LocalDate, to: LocalDate): Flow<Set<LocalDate>> =
        dao.observeDaysWithEntries(from.toEpochDay(), to.toEpochDay())
            .map { days -> days.map(LocalDate::ofEpochDay).toSet() }

    override suspend fun get(id: String): JournalEntry? = dao.getById(id)?.toDomain()

    override suspend fun save(entry: JournalEntry): String {
        require(entry.title.isNotBlank() || entry.content.isNotBlank()) { "Journal entry is empty" }
        val now = time.nowInstant().toEpochMilli()
        val existing = dao.getById(entry.id)
        dao.upsert(
            JournalEntryEntity(
                id = entry.id,
                date = entry.date.toEpochDay(),
                title = entry.title.trim(),
                content = entry.content,
                mood = entry.mood,
                bookmarked = entry.bookmarked,
                habitId = entry.habitId,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
        return entry.id
    }

    override suspend fun setBookmarked(id: String, bookmarked: Boolean) = dao.setBookmarked(id, bookmarked)

    override suspend fun delete(id: String) = dao.delete(id)
}
