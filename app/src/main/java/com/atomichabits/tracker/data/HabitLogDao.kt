package com.atomichabits.tracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: HabitLog): Long

    @Query("DELETE FROM habit_logs WHERE habitId = :habitId AND dateEpochDay = :dateEpochDay")
    suspend fun deleteForDate(habitId: Long, dateEpochDay: Long)

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId AND dateEpochDay = :dateEpochDay LIMIT 1")
    suspend fun getForDate(habitId: Long, dateEpochDay: Long): HabitLog?

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId ORDER BY dateEpochDay DESC")
    fun observeLogsForHabit(habitId: Long): Flow<List<HabitLog>>

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId AND dateEpochDay >= :fromEpochDay ORDER BY dateEpochDay ASC")
    fun observeLogsSince(habitId: Long, fromEpochDay: Long): Flow<List<HabitLog>>

    @Query("SELECT * FROM habit_logs WHERE dateEpochDay = :dateEpochDay")
    fun observeLogsForDate(dateEpochDay: Long): Flow<List<HabitLog>>

    @Query("SELECT * FROM habit_logs WHERE dateEpochDay BETWEEN :fromEpochDay AND :toEpochDay")
    fun observeLogsBetween(fromEpochDay: Long, toEpochDay: Long): Flow<List<HabitLog>>

    @Query("SELECT * FROM habit_logs WHERE synced = 0 ORDER BY dateEpochDay ASC")
    suspend fun getUnsyncedLogs(): List<HabitLog>

    @Query("UPDATE habit_logs SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId ORDER BY dateEpochDay DESC")
    suspend fun getAllForHabitOnce(habitId: Long): List<HabitLog>
}
