package com.atomichabits.tracker.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitJournalEntryDao {

    @Upsert
    suspend fun upsert(entry: HabitJournalEntry): Long

    @Query("SELECT * FROM habit_journal_entries WHERE habitId = :habitId ORDER BY dateEpochDay DESC")
    fun observeForHabit(habitId: Long): Flow<List<HabitJournalEntry>>

    @Query("SELECT * FROM habit_journal_entries WHERE habitId = :habitId AND dateEpochDay = :dateEpochDay LIMIT 1")
    suspend fun getForHabitOnDate(habitId: Long, dateEpochDay: Long): HabitJournalEntry?

    @Query("SELECT * FROM habit_journal_entries WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): HabitJournalEntry?

    @Query("SELECT * FROM habit_journal_entries")
    fun observeAll(): Flow<List<HabitJournalEntry>>

    @Query("SELECT * FROM habit_journal_entries")
    suspend fun getAllOnce(): List<HabitJournalEntry>
}
