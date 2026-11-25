package com.atomichabits.tracker.data

import com.atomichabits.tracker.sync.FirestoreSyncManager
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.util.UUID

class HabitJournalRepository(
    private val dao: HabitJournalEntryDao,
    private val syncManager: FirestoreSyncManager? = null,
    private val currentUid: () -> String? = { null }
) {
    fun observeForHabit(habitId: Long): Flow<List<HabitJournalEntry>> = dao.observeForHabit(habitId)

    fun observeAll(): Flow<List<HabitJournalEntry>> = dao.observeAll()

    suspend fun getForToday(habitId: Long): HabitJournalEntry? =
        dao.getForHabitOnDate(habitId, LocalDate.now().toEpochDay())

    suspend fun save(entry: HabitJournalEntry) {
        val withId = if (entry.syncId.isBlank()) entry.copy(syncId = UUID.randomUUID().toString()) else entry
        dao.upsert(withId)
        val uid = currentUid()
        if (uid != null && syncManager != null) {
            syncManager.pushJournalEntry(uid, withId)
        }
    }
}

/**
 * "Days without" for the new diary model: a day counts as clean only when it
 * has an EXPLICIT entry with [HabitJournalEntry.hadSlip] false - the checkbox
 * is the sole source of truth, not whether [HabitJournalEntry.amount] happens
 * to be blank (typing "0" into Amount is a non-blank string, so inferring
 * from blankness silently miscounted every honestly-filled-in day as a slip -
 * see the class doc). A missing day breaks a confirmed consecutive series; it is unknown, not a slip.
 */
fun computeJournalDaysWithout(
    habitSyncId: String,
    habitCreatedAtEpochDay: Long,
    entries: List<HabitJournalEntry>
): DaysWithoutInfo {
    return HabitMetrics.diaryStreak(habitSyncId, habitCreatedAtEpochDay, entries, LocalDate.now().toEpochDay())
}
