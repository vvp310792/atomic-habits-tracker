package com.atomichabits.tracker.data

import com.atomichabits.tracker.sync.FirestoreSyncManager
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import kotlin.math.roundToInt

class HabitRepository(
    private val habitDao: HabitDao,
    private val habitLogDao: HabitLogDao,
    private val pausePeriodDao: PausePeriodDao? = null,
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
     * active days-of-week AND any active travel pause (see PausePeriod.kt) -
     * a paused day is excused from scheduling entirely, exactly like a
     * non-scheduled day-of-week, so a business trip doesn't dent the streak,
     * rate, or mastery progress. A completion logged during a paused window
     * still counts normally - pausing only forgives misses.
     */
    suspend fun computeStats(habit: Habit): HabitStats {
        val logs = habitLogDao.getAllForHabitOnce(habit.id)
        val completedDays = logs.filter { it.completed }.map { it.dateEpochDay }.toSet()
        val pausePeriods = pausePeriodDao?.getAllOnce().orEmpty()

        var current = 0
        var cursor = LocalDate.now()
        while (true) {
            if (!isActiveOn(habit, cursor, pausePeriods)) {
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
            if (isActiveOn(habit, day, pausePeriods)) {
                scheduled++
                if (completedDays.contains(day.toEpochDay())) done++
            }
            day = day.plusDays(1)
        }
        val rate = if (scheduled == 0) 0 else (done * 100 / scheduled)

        val mastery = computeMastery(habit, completedDays, pausePeriods)
        // A habit can also be mastered by self-declaration (Habit.manuallyMastered),
        // independent of tracked history - see the class doc on that field.
        val isMastered = mastery.isMastered || habit.manuallyMastered
        val masteryProgress = if (habit.manuallyMastered) 100 else mastery.progressPercent

        return HabitStats(
            currentStreak = current,
            bestStreak = best,
            completionRatePercent = rate,
            masteryProgressPercent = masteryProgress,
            masteryScheduledDays = mastery.scheduledDays,
            isMastered = isMastered
        )
    }

    /**
     * Habit-formation "mastery" progress, per the neuroscience discussion this is
     * modelled on (Lally et al. 2010: median ~66 days to automaticity, range
     * 18-254; a single missed day barely moves the curve, but the overall
     * completion RATE over a long window is what predicts automaticity - not an
     * unbroken streak). A habit counts as mastered once it's hit at least
     * [MASTERY_THRESHOLD_PERCENT]% of its own scheduled days (only days it was
     * actually due, per [isActiveOn]) over its last [MASTERY_WINDOW_DAYS]
     * *scheduled* days - not 90 calendar days. A habit that only runs, say,
     * Monday-Friday needs its window stretched further back in real time to
     * gather 90 actual occasions to perform it; comparing it against a flat
     * 90-CALENDAR-day span would silently give it an easier target (fewer
     * required repetitions) than a daily habit gets over the same stretch,
     * which isn't a fair reading of "90 days of practice". Provided there's been
     * enough opportunity to judge it at all ([MASTERY_MIN_SCHEDULED_DAYS]
     * scheduled days minimum, so a habit can't be declared "mastered" after 3
     * lucky days). A Goldilocks difficulty bump ([Habit.difficultyBumpedAtEpochDay])
     * restarts this the same way a fresh [Habit.createdAtEpochDay] would.
     *
     * [MasteryInfo.progressPercent] is literally "how many of the completions
     * you'd need for mastery have you banked so far": the target is 80% of a
     * full 90-*scheduled*-day window (e.g. 72 for a daily habit, also 72 for a
     * Mon-Fri habit - just spread across ~126 calendar days instead of 90), and
     * progress is completions-so-far divided by that target. Once the habit is
     * actually mastered the bar reads a flat 100%, regardless of how much of
     * the nominal window has elapsed (a simple habit can legitimately master in
     * 18 days per Lally, well short of 90).
     *
     * This only reflects the *computed* path to mastery - a habit can also be
     * mastered by self-declaration (see [Habit.manuallyMastered]), which callers
     * combine with this result themselves (see [computeStats]) since it isn't
     * a function of log history at all.
     *
     * This is a plain (non-suspend) function, callable both from [computeStats]
     * (which already has the log history) and directly from a UI layer that has
     * already loaded all logs itself (e.g. for showing progress across a whole
     * list of habits without a DB round-trip per row). [pausePeriods] is
     * likewise optional for the same reason - pass in whatever's already loaded
     * as UI state; a paused day is excused from scheduling exactly like a
     * non-scheduled day-of-week, so it counts toward neither the target nor
     * actual performance (see PausePeriod.kt).
     */
    fun computeMastery(
        habit: Habit,
        completedEpochDays: Set<Long>,
        pausePeriods: List<PausePeriod> = emptyList()
    ): MasteryInfo {
        val today = LocalDate.now()

        // Walk backward from today collecting only *scheduled* days until we
        // have MASTERY_WINDOW_DAYS of them (or hit the sanity cap, for a habit
        // scheduled so rarely this would otherwise search back for years).
        var windowStart = today
        var nominalScheduled = 0
        var calendarStep = 0
        while (nominalScheduled < MASTERY_WINDOW_DAYS && calendarStep < MASTERY_MAX_CALENDAR_LOOKBACK_DAYS) {
            if (isActiveOn(habit, windowStart, pausePeriods)) nominalScheduled++
            if (nominalScheduled < MASTERY_WINDOW_DAYS) windowStart = windowStart.minusDays(1)
            calendarStep++
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
            if (isActiveOn(habit, day, pausePeriods)) {
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

    /**
     * True if [habit] is due on [date]: its own day-of-week schedule says so,
     * AND it isn't currently excused by a travel pause (see PausePeriod.kt) -
     * a paused day is treated identically to a non-scheduled day-of-week
     * everywhere this is used (streaks, completion rate, mastery), so it's
     * simply skipped rather than counted as a miss.
     */
    private fun isActiveOn(habit: Habit, date: LocalDate, pausePeriods: List<PausePeriod> = emptyList()): Boolean {
        val bit = date.dayOfWeek.value - 1 // Monday=0 .. Sunday=6
        val scheduledByWeekday = (habit.activeDays shr bit) and 1 == 1
        return scheduledByWeekday && !isHabitPausedOn(habit.syncId, date, pausePeriods)
    }
}

private const val MASTERY_WINDOW_DAYS = 90
private const val MASTERY_THRESHOLD_PERCENT = 80
private const val MASTERY_MIN_SCHEDULED_DAYS = 14
// Safety bound for walking backward to find 90 *scheduled* days for a habit
// scheduled very rarely (e.g. once a week needs ~630 calendar days for 90
// occasions) - generous enough for any realistic weekly pattern, but bounded
// so the search can't run away.
private const val MASTERY_MAX_CALENDAR_LOOKBACK_DAYS = 1095

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
