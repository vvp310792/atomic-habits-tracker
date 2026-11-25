package com.atomichabits.tracker.data

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class HabitMetricsTest {
    private val monday = LocalDate.of(2026, 9, 21)
    private val habit = Habit(id = 1, syncId = "h", name = "test", activeDays = 31, createdAtEpochDay = monday.toEpochDay())
    private val pause = PausePeriod(syncId = "p", startEpochDay = monday.plusDays(1).toEpochDay(),
        endEpochDay = monday.plusDays(1).toEpochDay(), pausedHabitSyncIds = "h")
    @Test fun beforeCreationAndWeekendNotDue() {
        assertFalse(HabitMetrics.due(habit, monday.minusDays(7), emptyList()))
        assertFalse(HabitMetrics.due(habit, monday.plusDays(5), emptyList()))
    }
    @Test fun pauseExcusesMissButCountsHit() {
        assertEquals(HabitMetrics.Daily(0,0), HabitMetrics.day(listOf(habit), monday.plusDays(1), emptySet(), listOf(pause)))
        assertEquals(HabitMetrics.Daily(1,1), HabitMetrics.day(listOf(habit), monday.plusDays(1), setOf(1L), listOf(pause)))
    }
    @Test fun unrelatedLogDoesNotInflate() {
        assertEquals(HabitMetrics.Daily(0,1), HabitMetrics.day(listOf(habit), monday, setOf(2L), emptyList()))
    }
    @Test fun currentAndBestSkipExcusedDaysAndToday() {
        val done = setOf(monday.toEpochDay(), monday.plusDays(2).toEpochDay())
        assertEquals(2, HabitMetrics.currentStreak(habit, done, listOf(pause), monday.plusDays(3)))
        assertEquals(2, HabitMetrics.bestStreak(habit, done, listOf(pause), monday.plusDays(3)))
    }
    @Test fun missingDiaryDoesNotCreateCleanDays() {
        val d = monday.toEpochDay()
        val entries = listOf(HabitJournalEntry(habitSyncId="h",dateEpochDay=d,hadSlip=false),
            HabitJournalEntry(habitSyncId="h",dateEpochDay=d+2,hadSlip=false))
        assertEquals(DaysWithoutInfo(0,0), HabitMetrics.diaryStreak("h", d, emptyList(), d+10))
        assertEquals(DaysWithoutInfo(0,1), HabitMetrics.diaryStreak("h",d,entries,d+10))
        assertEquals(DaysWithoutInfo(1,1), HabitMetrics.diaryStreak("h",d,entries,d+2))
    }
}
