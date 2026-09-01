package com.atomichabits.tracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * A trip/travel period during which specific habits are excused from
 * scheduling for statistics purposes - streaks, completion rate, and mastery
 * progress skip these days for the paused habits, exactly the same way a
 * non-scheduled day-of-week is already skipped (see HabitRepository.isActiveOn),
 * so a business trip doesn't silently break a streak or dent months of
 * mastery progress. A completion logged DURING a paused window still counts
 * as a normal win if it happens - pausing only forgives misses, it never
 * discounts a hit.
 *
 * [pausedHabitSyncIds] is a comma-separated list of Habit.syncId, matching the
 * existing pattern used for tag lists elsewhere in this app (see
 * ImpulseLog.triggerTags) - deliberately per-trip and opt-in (only habits
 * explicitly picked for THIS trip are paused; everything else keeps running
 * normally), since which habits actually stop during travel varies trip to
 * trip (a workout habit usually can't happen on the road, an audiobook habit
 * often still can).
 */
@Entity(tableName = "pause_periods", indices = [Index(value = ["syncId"], unique = true)])
data class PausePeriod(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = "",
    val startEpochDay: Long = 0,
    val endEpochDay: Long = 0,
    val label: String = "",
    val pausedHabitSyncIds: String = ""
)

/** True if [habitSyncId] is paused on [date] by any of [periods]. */
fun isHabitPausedOn(habitSyncId: String, date: LocalDate, periods: List<PausePeriod>): Boolean {
    val epochDay = date.toEpochDay()
    return periods.any { p ->
        epochDay in p.startEpochDay..p.endEpochDay &&
            p.pausedHabitSyncIds.split(",").map { it.trim() }.contains(habitSyncId)
    }
}
