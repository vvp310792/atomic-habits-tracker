package com.atomichabits.tracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {

    @Query("SELECT * FROM habits WHERE archived = 0 ORDER BY sortOrder ASC, id ASC")
    fun observeActiveHabits(): Flow<List<Habit>>

    @Query("SELECT * FROM habits WHERE id = :habitId")
    fun observeHabit(habitId: Long): Flow<Habit?>

    @Query("SELECT * FROM habits WHERE id = :habitId")
    suspend fun getHabit(habitId: Long): Habit?

    @Query("SELECT * FROM habits WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): Habit?

    @Query("SELECT * FROM habits WHERE archived = 0")
    suspend fun getAllActiveOnce(): List<Habit>

    /** Includes archived habits - used for the full definitions push so archived state round-trips too. */
    @Query("SELECT * FROM habits")
    suspend fun getAllOnce(): List<Habit>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(habit: Habit): Long

    @Update
    suspend fun update(habit: Habit)

    @Query("UPDATE habits SET archived = 1 WHERE id = :habitId")
    suspend fun archive(habitId: Long)

    @Delete
    suspend fun delete(habit: Habit)
}
