package com.atomichabits.tracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface IdentityDao {

    @Query("SELECT * FROM identities WHERE archived = 0 ORDER BY id ASC")
    fun observeActive(): Flow<List<Identity>>

    @Query("SELECT * FROM identities WHERE syncId = :syncId LIMIT 1")
    suspend fun getBySyncId(syncId: String): Identity?

    @Query("SELECT * FROM identities")
    suspend fun getAllOnce(): List<Identity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(identity: Identity): Long

    @Query("UPDATE identities SET archived = 1 WHERE id = :id")
    suspend fun archive(id: Long)
}
