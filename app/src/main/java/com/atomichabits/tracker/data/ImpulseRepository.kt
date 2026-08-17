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

    /** See the top-level [computeDaysWithout] - kept as a method too so existing
     * call sites (`app.impulseRepository.computeDaysWithout(...)`) don't need to
     * change; this just delegates. */
    fun computeDaysWithout(habit: Habit, allLogs: List<ImpulseLog>): DaysWithoutInfo =
        computeDaysWithout(habit.syncId, habit.createdAtEpochDay, allLogs)
}

data class DaysWithoutInfo(val currentDays: Int, val bestDays: Int)

/**
 * Dopamine-balance "days without" (Anna Lembke, "Dopamine Nation"): every
 * dopamine hit is followed by an equal-and-opposite dip below baseline as
 * the brain re-balances - frequent hits keep pushing the baseline down,
 * raising the tolerance needed for the same hit next time. Abstinence lets
 * the baseline recover, which is what this counts: days since the last
 * "CROSS" (gave in) linked to this specific harmful habit - not since the
 * habit was catalogued, and NOT the same thing as the CHECK:CROSS tally
 * shown elsewhere, which is intentionally kept because a single slip
 * shouldn't feel like "the whole day/run is ruined" (see ImpulseLog.kt).
 * This number is additional context, not a replacement for that - it's
 * framed calmly (a shield, not a fire streak) and never shown with any
 * "you broke it" messaging on a fresh CROSS: it just quietly reads 0 next
 * time, the same way the CHECK:CROSS tally doesn't scold either.
 *
 * [DaysWithoutInfo.bestDays] is the longest such gap in this habit's whole
 * history (from creation to first slip, between any two slips, or the
 * current gap if it's the longest one) - so a fresh slip doesn't erase the
 * fact that a longer stretch was once achieved.
 *
 * A top-level (not a class member) function so it's usable anywhere that
 * already has the log list in memory without needing an [ImpulseRepository]
 * instance - e.g. [com.atomichabits.tracker.export.DataExporter], which works
 * directly off DAOs.
 */
fun computeDaysWithout(habitSyncId: String, habitCreatedAtEpochDay: Long, allLogs: List<ImpulseLog>): DaysWithoutInfo {
    val today = LocalDate.now().toEpochDay()
    val crossDays = allLogs
        .filter { it.linkedHarmfulAnchorId == habitSyncId && it.outcome == "CROSS" }
        .map { it.dateEpochDay }
        .distinct()
        .sorted()
    val start = if (habitCreatedAtEpochDay > 0) habitCreatedAtEpochDay else (crossDays.firstOrNull() ?: today)

    if (crossDays.isEmpty()) {
        val days = (today - start).toInt().coerceAtLeast(0)
        return DaysWithoutInfo(currentDays = days, bestDays = days)
    }

    val currentDays = (today - crossDays.last()).toInt().coerceAtLeast(0)
    var best = (crossDays.first() - start).toInt().coerceAtLeast(0)
    for (i in 1 until crossDays.size) {
        val gap = (crossDays[i] - crossDays[i - 1]).toInt()
        if (gap > best) best = gap
    }
    if (currentDays > best) best = currentDays

    return DaysWithoutInfo(currentDays = currentDays, bestDays = best)
}
