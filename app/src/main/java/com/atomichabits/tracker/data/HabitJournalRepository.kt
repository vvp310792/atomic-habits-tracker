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
 * see the class doc). Days with no entry are skipped (neither extend nor
 * break the count), same live-and-let-live treatment as non-scheduled days
 * get elsewhere in this app, since missing a diary entry isn't the same claim
 * as an explicit "nothing happened today".
 */
fun computeJournalDaysWithout(
    habitSyncId: String,
    habitCreatedAtEpochDay: Long,
    entries: List<HabitJournalEntry>
): DaysWithoutInfo {
    val today = LocalDate.now().toEpochDay()
    val relevant = entries.filter { it.habitSyncId == habitSyncId }
    val slipDays = relevant.filter { it.hadSlip }.map { it.dateEpochDay }.distinct().sorted()
    val start = if (habitCreatedAtEpochDay > 0) habitCreatedAtEpochDay else (relevant.minOfOrNull { it.dateEpochDay } ?: today)

    if (slipDays.isEmpty()) {
        val days = (today - start).toInt().coerceAtLeast(0)
        return DaysWithoutInfo(currentDays = days, bestDays = days)
    }

    val currentDays = (today - slipDays.last()).toInt().coerceAtLeast(0)
    var best = (slipDays.first() - start).toInt().coerceAtLeast(0)
    for (i in 1 until slipDays.size) {
        val gap = (slipDays[i] - slipDays[i - 1]).toInt()
        if (gap > best) best = gap
    }
    if (currentDays > best) best = currentDays

    return DaysWithoutInfo(currentDays = currentDays, bestDays = best)
}
