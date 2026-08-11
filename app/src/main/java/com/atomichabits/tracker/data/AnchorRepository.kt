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
        val id = anchorDao.upsert(anchor)
        val saved = if (anchor.id == 0L) anchor.copy(id = id) else anchor
        pushIfSignedIn(saved)
        return id
    }

    suspend fun archive(id: Long) {
        anchorDao.archive(id)
        anchorDao.getAllOnce().find { it.id == id }?.let { pushIfSignedIn(it.copy(archived = true)) }
    }

    private fun pushIfSignedIn(anchor: AnchorHabit) {
        val uid = currentUid()
        if (uid != null && syncManager != null) {
            syncManager.pushAnchor(uid, anchor)
        }
    }
}
