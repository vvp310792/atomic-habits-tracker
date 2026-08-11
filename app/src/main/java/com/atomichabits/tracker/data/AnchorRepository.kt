package com.atomichabits.tracker.data

import com.atomichabits.tracker.sync.FirestoreSyncManager
import kotlinx.coroutines.flow.Flow

class AnchorRepository(
    private val anchorDao: AnchorHabitDao,
    private val syncManager: FirestoreSyncManager? = null,
    private val currentUid: () -> String? = { null }
) {
    fun observeActive(): Flow<List<AnchorHabit>> = anchorDao.observeActive()

    suspend fun save(anchor: AnchorHabit): Long {
        val withSyncId = if (anchor.syncId.isBlank()) {
            anchor.copy(syncId = java.util.UUID.randomUUID().toString())
        } else {
            anchor
        }
        val id = anchorDao.upsert(withSyncId)
        val saved = if (withSyncId.id == 0L) withSyncId.copy(id = id) else withSyncId
        pushIfSignedIn(saved)
        return id
    }

    suspend fun archive(id: Long) {
        anchorDao.archive(id)
        anchorDao.getAllOnce().find { it.id == id }?.let { pushIfSignedIn(it.copy(archived = true)) }
    }

    /** Backfills a syncId for any anchor that ended up without one (data saved before a since-fixed bug) and pushes it. */
    suspend fun healBlankSyncIds() {
        anchorDao.getAllOnce().filter { it.syncId.isBlank() }.forEach { save(it) }
    }

    private fun pushIfSignedIn(anchor: AnchorHabit) {
        val uid = currentUid()
        if (uid != null && syncManager != null) {
            syncManager.pushAnchor(uid, anchor)
        }
    }
}
