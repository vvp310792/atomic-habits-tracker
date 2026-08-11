package com.atomichabits.tracker.data

import com.atomichabits.tracker.sync.FirestoreSyncManager
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.util.UUID

class ImpulseRepository(
    private val dao: ImpulseLogDao,
    private val syncManager: FirestoreSyncManager? = null,
    private val currentUid: () -> String? = { null }
) {
    fun observeForDate(date: LocalDate): Flow<List<ImpulseLog>> = dao.observeForDate(date.toEpochDay())

    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<ImpulseLog>> =
        dao.observeBetween(from.toEpochDay(), to.toEpochDay())

    fun observeAll(): Flow<List<ImpulseLog>> = dao.observeAll()

    suspend fun logCheck(linkedAnchorId: String = "", linkedAnchorLabel: String = "") {
        save(
            ImpulseLog(
                outcome = "CHECK",
                dateEpochDay = LocalDate.now().toEpochDay(),
                linkedHarmfulAnchorId = linkedAnchorId,
                linkedHarmfulAnchorLabel = linkedAnchorLabel
            )
        )
    }

    suspend fun logCross(
        triggerTags: List<String>,
        note: String,
        linkedAnchorId: String = "",
        linkedAnchorLabel: String = ""
    ) {
        save(
            ImpulseLog(
                outcome = "CROSS",
                dateEpochDay = LocalDate.now().toEpochDay(),
                triggerTags = triggerTags.joinToString(","),
                note = note,
                linkedHarmfulAnchorId = linkedAnchorId,
                linkedHarmfulAnchorLabel = linkedAnchorLabel
            )
        )
    }

    private suspend fun save(log: ImpulseLog) {
        val withId = log.copy(syncId = UUID.randomUUID().toString())
        dao.upsert(withId)
        val uid = currentUid()
        if (uid != null && syncManager != null) {
            syncManager.pushImpulseLog(uid, withId)
        }
    }
}
