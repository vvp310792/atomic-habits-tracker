package com.atomichabits.tracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PausePeriodDao {

    @Upsert
    suspend fun upsert(period: PausePeriod): Long

    @Delete
    suspend fun delete(period: PausePeriod)

    @Query("SELECT * FROM pause_periods ORDER BY startEpochDay DESC")
    fun observeAll(): Flow<List<PausePeriod>>

    @Query("SELECT * FROM pause_periods")
    suspend fun getAllOnce(): List<PausePeriod>

    @Query("SELECT * FROM pause_periods WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): PausePeriod?
}
