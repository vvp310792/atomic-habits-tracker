package com.atomichabits.tracker.data

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class HabitRepository(
    private val habitDao: HabitDao,
    private val habitLogDao: HabitLogDao
) {
    fun observeActiveHabits(): Flow<List<Habit>> = habitDao.observeActiveHabits()

    fun observeHabit(habitId: Long): Flow<Habit?> = habitDao.observeHabit(habitId)

    fun observeLogsForDate(date: LocalDate): Flow<List<HabitLog>> =
        habitLogDao.observeLogsForDate(date.toEpochDay())

    fun observeLogsBetween(from: LocalDate, to: LocalDate): Flow<List<HabitLog>> =
        habitLogDao.observeLogsBetween(from.toEpochDay(), to.toEpochDay())

    fun observeLogsForHabit(habitId: Long): Flow<List<HabitLog>> =
        habitLogDao.observeLogsForHabit(habitId)

    fun observeLogsSince(habitId: Long, since: LocalDate): Flow<List<HabitLog>> =
        habitLogDao.observeLogsSince(habitId, since.toEpochDay())

    suspend fun saveHabit(habit: Habit): Long = habitDao.upsert(habit)

    suspend fun archiveHabit(habitId: Long) = habitDao.archive(habitId)

    suspend fun deleteHabit(habit: Habit) = habitDao.delete(habit)

    /** Toggles completion for [habitId] on [date]. Returns the new completed state. */
    suspend fun toggleCompletion(habitId: Long, date: LocalDate): Boolean {
        val epochDay = date.toEpochDay()
        val existing = habitLogDao.getForDate(habitId, epochDay)
        return if (existing != null) {
            habitLogDao.deleteForDate(habitId, epochDay)
            false
        } else {
            habitLogDao.upsert(
                HabitLog(habitId = habitId, dateEpochDay = epochDay, completed = true)
            )
            true
        }
    }

    /**
     * Computes (currentStreak, bestStreak, completionRatePercent over last 30 days)
     * from the full log history of a habit, respecting its active days-of-week.
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

        return HabitStats(currentStreak = current, bestStreak = best, completionRatePercent = rate)
    }

    private fun isActiveOn(habit: Habit, date: LocalDate): Boolean {
        val bit = date.dayOfWeek.value - 1 // Monday=0 .. Sunday=6
        return (habit.activeDays shr bit) and 1 == 1
    }
}

data class HabitStats(
    val currentStreak: Int,
    val bestStreak: Int,
    val completionRatePercent: Int
)
