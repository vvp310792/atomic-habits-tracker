package com.atomichabits.tracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AnchorHabitDao {

    @Query("SELECT * FROM anchor_habits WHERE archived = 0 ORDER BY type ASC, name ASC")
    fun observeActive(): Flow<List<AnchorHabit>>

    @Query("SELECT * FROM anchor_habits WHERE archived = 0")
    suspend fun getAllActiveOnce(): List<AnchorHabit>

    @Query("SELECT * FROM anchor_habits")
    suspend fun getAllOnce(): List<AnchorHabit>

    @Query("SELECT * FROM anchor_habits WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): AnchorHabit?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(anchor: AnchorHabit): Long

    @Query("UPDATE anchor_habits SET archived = 1 WHERE id = :id")
    suspend fun archive(id: Long)
}
