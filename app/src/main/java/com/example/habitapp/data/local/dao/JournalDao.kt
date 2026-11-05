package com.example.habitapp.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.habitapp.data.local.entity.JournalEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface JournalDao {

    /**
     * Database-backed search and filtering. [query] is matched against title and content
     * (an empty string matches everything); [habitFilter] = 1 keeps only habit notes,
     * 2 only daily entries, 0 both.
     */
    @Query(
        """
        SELECT * FROM journal_entries
        WHERE (:query = '' OR title LIKE '%' || :query || '%' ESCAPE '\' OR content LIKE '%' || :query || '%' ESCAPE '\')
          AND (:bookmarkedOnly = 0 OR bookmarked = 1)
          AND (:habitFilter = 0 OR (:habitFilter = 1 AND habitId IS NOT NULL) OR (:habitFilter = 2 AND habitId IS NULL))
          AND (:habitId IS NULL OR habitId = :habitId)
          AND (:fromDay IS NULL OR date >= :fromDay)
          AND (:toDay IS NULL OR date <= :toDay)
        ORDER BY date DESC, updatedAt DESC
        """,
    )
    fun search(
        query: String,
        bookmarkedOnly: Boolean,
        habitFilter: Int,
        habitId: String?,
        fromDay: Long?,
        toDay: Long?,
    ): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entries WHERE id = :id")
    suspend fun getById(id: String): JournalEntryEntity?

    @Query("SELECT * FROM journal_entries WHERE date = :day ORDER BY updatedAt DESC")
    fun observeForDay(day: Long): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entries WHERE habitId = :habitId ORDER BY date DESC")
    fun observeForHabit(habitId: String): Flow<List<JournalEntryEntity>>

    @Query("SELECT DISTINCT date FROM journal_entries WHERE date BETWEEN :fromDay AND :toDay")
    fun observeDaysWithEntries(fromDay: Long, toDay: Long): Flow<List<Long>>

    @Query("SELECT * FROM journal_entries")
    suspend fun getAll(): List<JournalEntryEntity>

    @Upsert
    suspend fun upsert(entry: JournalEntryEntity)

    @Query("UPDATE journal_entries SET bookmarked = :bookmarked WHERE id = :id")
    suspend fun setBookmarked(id: String, bookmarked: Boolean)

    @Query("DELETE FROM journal_entries WHERE id = :id")
    suspend fun delete(id: String)
}
