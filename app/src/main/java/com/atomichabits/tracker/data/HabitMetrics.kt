package com.atomichabits.tracker.data

import java.time.LocalDate

/** One definition of planned opportunities for all screens. Current schedules
 * are estimates for historical dates; old schedule versions were not stored. */
object HabitMetrics {
    fun planned(habit: Habit, date: LocalDate): Boolean =
        date.toEpochDay() >= habit.createdAtEpochDay &&
            (habit.activeDays shr (date.dayOfWeek.value - 1)) and 1 == 1

    /** Paused misses are excused. A completed planned opportunity still counts. */
    fun due(habit: Habit, date: LocalDate, pauses: List<PausePeriod>, completed: Boolean = false): Boolean =
        planned(habit, date) && (completed || !isHabitPausedOn(habit.syncId, date, pauses))

    data class Daily(val completed: Int, val scheduled: Int) {
        val perfect get() = scheduled > 0 && completed == scheduled
    }
    fun day(habits: List<Habit>, date: LocalDate, completedIds: Set<Long>, pauses: List<PausePeriod>): Daily {
        val due = habits.filter { due(it, date, pauses, it.id in completedIds) }
        return Daily(due.count { it.id in completedIds }, due.size)
    }
    fun currentStreak(habit: Habit, done: Set<Long>, pauses: List<PausePeriod>, today: LocalDate, cap: Int = 3660): Int {
        // An unfinished current day cannot break yesterday's streak.
        var d = if (today.toEpochDay() in done) today else today.minusDays(1)
        var count = 0
        repeat(cap) {
            if (d.toEpochDay() < habit.createdAtEpochDay) return count
            val hit = d.toEpochDay() in done
            if (due(habit, d, pauses, hit)) {
                if (!hit) return count
                count++
            }
            d = d.minusDays(1)
        }
        return count
    }
    fun bestStreak(habit: Habit, done: Set<Long>, pauses: List<PausePeriod>, today: LocalDate): Int {
        if (done.isEmpty()) return 0
        var d = LocalDate.ofEpochDay(maxOf(habit.createdAtEpochDay, done.minOrNull()!!))
        var best = 0
        var running = 0
        while (!d.isAfter(today)) {
            val hit = d.toEpochDay() in done
            if (due(habit, d, pauses, hit)) {
                running = if (hit) running + 1 else 0
                best = maxOf(best, running)
            }
            d = d.plusDays(1)
        }
        return best
    }
    /** Explicit consecutive clean diary days. Unknown days never assert abstinence. */
    fun diaryStreak(habitSyncId: String, created: Long, entries: List<HabitJournalEntry>, today: Long): DaysWithoutInfo {
        val days = entries.filter { it.habitSyncId == habitSyncId && it.dateEpochDay in created..today }
            .groupBy { it.dateEpochDay }.toSortedMap()
        var best = 0
        var running = 0
        var previous: Long? = null
        days.forEach { (day, group) ->
            running = if (group.any { it.hadSlip }) 0 else if (previous == day - 1) running + 1 else 1
            best = maxOf(best, running)
            previous = day
        }
        val current = if (previous == today || previous == today - 1) running else 0
        return DaysWithoutInfo(current, best)
    }
}
