package com.atomichabits.tracker.data

import com.atomichabits.tracker.sync.FirestoreSyncManager
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import kotlin.math.roundToInt

class HabitRepository(
    private val habitDao: HabitDao,
    private val habitLogDao: HabitLogDao,
    private val syncManager: FirestoreSyncManager? = null,
    private val currentUid: () -> String? = { null }
) {
    fun observeActiveHabits(): Flow<List<Habit>> = habitDao.observeActiveHabits()

    fun observeHabit(habitId: Long): Flow<Habit?> = habitDao.observeHabit(habitId)

    fun observeLogsForDate(date: LocalDate): Flow<List<HabitLog>> =
        habitLogDao.observeLogsForDate(date.toEpochDay())

    fun observeLogsBetween(from: LocalDate, to: LocalDate): Flow<List<HabitLog>> =
        habitLogDao.observeLogsBetween(from.toEpochDay(), to.toEpochDay())

    fun observeAllLogs(): Flow<List<HabitLog>> = habitLogDao.observeAllLogs()

    fun observeLogsForHabit(habitId: Long): Flow<List<HabitLog>> =
        habitLogDao.observeLogsForHabit(habitId)

    fun observeLogsSince(habitId: Long, since: LocalDate): Flow<List<HabitLog>> =
        habitLogDao.observeLogsSince(habitId, since.toEpochDay())

    suspend fun saveHabit(habit: Habit): Long {
        val id = habitDao.upsert(habit)
        pushHabitIfSignedIn(if (habit.id == 0L) habit.copy(id = id) else habit)
        return id
    }

    suspend fun archiveHabit(habitId: Long) {
        habitDao.archive(habitId)
        habitDao.getHabit(habitId)?.let { pushHabitIfSignedIn(it) }
    }

    /** Persists a new manual order for tracked habits (drag-to-reorder on the Habits screen). */
    suspend fun reorder(orderedHabitIds: List<Long>) {
        orderedHabitIds.forEachIndexed { index, id ->
            habitDao.updateSortOrder(id, index)
        }
        val uid = currentUid()
        if (uid != null && syncManager != null) {
            orderedHabitIds.forEach { id -> habitDao.getHabit(id)?.let { pushHabitIfSignedIn(it) } }
        }
    }

    suspend fun deleteHabit(habit: Habit) = habitDao.delete(habit)

    /**
     * Keeps every habit's cached [Habit.identityLabel] in sync when the linked
     * [Identity]'s statement text is renamed - same "cached copy" pattern as
     * [Habit.stackAnchorLabel], but proactively refreshed here since a rename is
     * exactly the moment that cache would otherwise go stale.
     */
    suspend fun renameIdentityLabel(identityId: String, newLabel: String) {
        if (identityId.isBlank()) return
        habitDao.updateIdentityLabel(identityId, newLabel)
        val uid = currentUid()
        if (uid != null && syncManager != null) {
            habitDao.getByIdentityId(identityId).forEach { pushHabitIfSignedIn(it) }
        }
    }

    /** Toggles completion for [habitId] on [date]. Returns the new completed state. */
    suspend fun toggleCompletion(habitId: Long, date: LocalDate): Boolean {
        val epochDay = date.toEpochDay()
        val existing = habitLogDao.getForDate(habitId, epochDay)
        return if (existing != null) {
            habitLogDao.deleteForDate(habitId, epochDay)
            val uid = currentUid()
            if (uid != null && syncManager != null) {
                habitDao.getHabit(habitId)?.let { syncManager.deleteLog(uid, it.syncId, epochDay) }
            }
            false
        } else {
            val log = HabitLog(habitId = habitId, dateEpochDay = epochDay, completed = true)
            habitLogDao.upsert(log)
            val uid = currentUid()
            if (uid != null && syncManager != null) {
                habitDao.getHabit(habitId)?.let { syncManager.pushLog(uid, it, log) }
            }
            true
        }
    }

    private fun pushHabitIfSignedIn(habit: Habit) {
        val uid = currentUid()
        if (uid != null && syncManager != null) {
            syncManager.pushHabit(uid, habit)
        }
    }

    /**
     * Computes (currentStreak, bestStreak, completionRatePercent over last 30 days,
     * mastery progress) from the full log history of a habit, respecting its
     * active days-of-week.
     */
    suspend fun computeStats(habit: Habit): HabitStats {
        val logs = habitLogDao.getAllForHabitOnce(habit.id)
        val completedDays = logs.filter { it.completed }.map { it.dateEpochDay }.toSet()

        var current = 0
        var cursor = LocalDate.now()
        while (true) {
            if (!isActiveOn(habit, cursor)) {
                cursor = cursor.minusDays(1)
                continue
            }
            if (completedDays.contains(cursor.toEpochDay())) {
                current++
                cursor = cursor.minusDays(1)
            } else {
                break
            }
        }

        var best = 0
        var running = 0
        val sortedDays = completedDays.sorted()
        for (i in sortedDays.indices) {
            if (i == 0 || sortedDays[i] == sortedDays[i - 1] + 1) {
                running++
            } else {
                running = 1
            }
            if (running > best) best = running
        }

        val since = LocalDate.now().minusDays(29)
        var scheduled = 0
        var done = 0
        var day = since
        while (!day.isAfter(LocalDate.now())) {
            if (isActiveOn(habit, day)) {
                scheduled++
                if (completedDays.contains(day.toEpochDay())) done++
            }
            day = day.plusDays(1)
        }
        val rate = if (scheduled == 0) 0 else (done * 100 / scheduled)

        val mastery = computeMastery(habit, completedDays)

        return HabitStats(
            currentStreak = current,
            bestStreak = best,
            completionRatePercent = rate,
            masteryProgressPercent = mastery.progressPercent,
            masteryScheduledDays = mastery.scheduledDays,
            isMastered = mastery.isMastered
        )
    }

    /**
     * Habit-formation "mastery" progress, per the neuroscience discussion this is
     * modelled on (Lally et al. 2010: median ~66 days to automaticity, range
     * 18-254; a single missed day barely moves the curve, but the overall
     * completion RATE over a long window is what predicts automaticity - not an
     * unbroken streak). A habit counts as mastered once it's hit at least
     * [MASTERY_THRESHOLD_PERCENT]% of its own scheduled days (only days it was
     * actually due, per [isActiveOn]) over the last [MASTERY_WINDOW_DAYS] days -
     * or its whole lifetime if younger than that window - provided there's been
     * enough opportunity to judge it ([MASTERY_MIN_SCHEDULED_DAYS] scheduled days
     * minimum, so a habit can't be declared "mastered" after 3 lucky days).
     * A Goldilocks difficulty bump ([Habit.difficultyBumpedAtEpochDay]) restarts
     * this the same way a fresh [Habit.createdAtEpochDay] would.
     *
     * [MasteryInfo.progressPercent] is literally "how many of the completions
     * you'd need for mastery have you banked so far": the target is 80% of a
     * full 90-day window's worth of scheduled days (e.g. 72 for a daily habit),
     * and progress is completions-so-far divided by that target. 5 successful
     * days out of a 72-day target is ~7%, not some inflated fraction relative to
     * the much smaller 14-day minimum-evidence gate - that gate only decides
     * *whether* mastery can be judged yet, it isn't the yardstick for progress.
     * Once the habit is actually mastered the bar reads a flat 100%, regardless
     * of how much of the nominal 90-day window has elapsed (a simple habit can
     * legitimately master in 18 days per Lally, well short of 90).
     *
     * This is a plain (non-suspend) function, callable both from [computeStats]
     * (which already has the log history) and directly from a UI layer that has
     * already loaded all logs itself (e.g. for showing progress across a whole
     * list of habits without a DB round-trip per row).
     */
    fun computeMastery(habit: Habit, completedEpochDays: Set<Long>): MasteryInfo {
        val today = LocalDate.now()
        val windowStart = today.minusDays((MASTERY_WINDOW_DAYS - 1).toLong())

        // The long-run target: 80% of however many days this habit's own
        // active-days pattern would schedule across a FULL 90-day window,
        // regardless of how young the habit actually is.
        var nominalScheduled = 0
        var probe = windowStart
        while (!probe.isAfter(today)) {
            if (isActiveOn(habit, probe)) nominalScheduled++
            probe = probe.plusDays(1)
        }
        val target = (nominalScheduled * MASTERY_THRESHOLD_PERCENT / 100).coerceAtLeast(1)

        // Actual performance so far (from creation date, or from the last
        // Goldilocks difficulty bump if that's more recent - a harder version
        // of the habit honestly hasn't earned its automaticity yet, even though
        // the row and its pre-bump history are the same habit).
        val effectiveStart = maxOf(habit.createdAtEpochDay, habit.difficultyBumpedAtEpochDay)
        val createdDate = if (effectiveStart > 0) LocalDate.ofEpochDay(effectiveStart) else windowStart
        val since = if (createdDate.isAfter(windowStart)) createdDate else windowStart
        var scheduled = 0
        var done = 0
        var day = since
        while (!day.isAfter(today)) {
            if (isActiveOn(habit, day)) {
                scheduled++
                if (completedEpochDays.contains(day.toEpochDay())) done++
            }
            day = day.plusDays(1)
        }
        val actualRatePercent = if (scheduled == 0) 0 else (done * 100 / scheduled)
        val mastered = actualRatePercent >= MASTERY_THRESHOLD_PERCENT && scheduled >= MASTERY_MIN_SCHEDULED_DAYS

        val progress = if (mastered) 100 else ((done * 100f / target).roundToInt()).coerceIn(0, 99)

        return MasteryInfo(progressPercent = progress, scheduledDays = scheduled, isMastered = mastered)
    }

    private fun isActiveOn(habit: Habit, date: LocalDate): Boolean {
        val bit = date.dayOfWeek.value - 1 // Monday=0 .. Sunday=6
        return (habit.activeDays shr bit) and 1 == 1
    }
}

private const val MASTERY_WINDOW_DAYS = 90
private const val MASTERY_THRESHOLD_PERCENT = 80
private const val MASTERY_MIN_SCHEDULED_DAYS = 14

data class HabitStats(
    val currentStreak: Int,
    val bestStreak: Int,
    val completionRatePercent: Int,
    val masteryProgressPercent: Int,
    val masteryScheduledDays: Int,
    val isMastered: Boolean
)

data class MasteryInfo(
    val progressPercent: Int,
    val scheduledDays: Int,
    val isMastered: Boolean
)
