package com.atomichabits.tracker.data

import com.atomichabits.tracker.sync.FirestoreSyncManager
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class IdentityRepository(
    private val dao: IdentityDao,
    private val syncManager: FirestoreSyncManager? = null,
    private val currentUid: () -> String? = { null }
) {
    fun observeActive(): Flow<List<Identity>> = dao.observeActive()

    suspend fun save(identity: Identity): Long {
        val withSyncId = if (identity.syncId.isBlank()) identity.copy(syncId = UUID.randomUUID().toString()) else identity
        val id = dao.upsert(withSyncId)
        val saved = if (withSyncId.id == 0L) withSyncId.copy(id = id) else withSyncId
        val uid = currentUid()
        if (uid != null && syncManager != null) {
            syncManager.pushIdentity(uid, saved)
        }
        return id
    }

    suspend fun archive(id: Long) {
        dao.archive(id)
        dao.getAllOnce().find { it.id == id }?.let { archived ->
            val uid = currentUid()
            if (uid != null && syncManager != null) {
                syncManager.pushIdentity(uid, archived.copy(archived = true))
            }
        }
    }
}
