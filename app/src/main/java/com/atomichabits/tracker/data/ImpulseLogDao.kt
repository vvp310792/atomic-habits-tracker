package com.atomichabits.tracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ImpulseLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: ImpulseLog): Long

    @Query("SELECT * FROM impulse_logs WHERE dateEpochDay = :dateEpochDay ORDER BY timestampMillis DESC")
    fun observeForDate(dateEpochDay: Long): Flow<List<ImpulseLog>>

    @Query("SELECT * FROM impulse_logs WHERE dateEpochDay BETWEEN :fromEpochDay AND :toEpochDay")
    fun observeBetween(fromEpochDay: Long, toEpochDay: Long): Flow<List<ImpulseLog>>

    @Query("SELECT * FROM impulse_logs")
    fun observeAll(): Flow<List<ImpulseLog>>

    @Query("SELECT * FROM impulse_logs WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): ImpulseLog?

    @Query("SELECT * FROM impulse_logs")
    suspend fun getAllOnce(): List<ImpulseLog>
}
