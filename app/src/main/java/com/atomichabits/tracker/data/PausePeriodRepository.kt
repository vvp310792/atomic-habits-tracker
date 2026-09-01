package com.atomichabits.tracker.data

import com.atomichabits.tracker.sync.FirestoreSyncManager
import java.util.UUID

class PausePeriodRepository(
    private val dao: PausePeriodDao,
    private val syncManager: FirestoreSyncManager? = null,
    private val currentUid: () -> String? = { null }
) {
    fun observeAll() = dao.observeAll()

    suspend fun save(period: PausePeriod) {
        val withId = if (period.syncId.isBlank()) period.copy(syncId = UUID.randomUUID().toString()) else period
        dao.upsert(withId)
        val uid = currentUid()
        if (uid != null && syncManager != null) {
            syncManager.pushPausePeriod(uid, withId)
        }
    }

    suspend fun delete(period: PausePeriod) = dao.delete(period)
}
