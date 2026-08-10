package com.atomichabits.tracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One completion record for a habit on a given day.
 * [dateEpochDay] is [java.time.LocalDate.toEpochDay], used so we can index/sort/query by date
 * without timezone-parsing headaches.
 * [synced] tracks whether this row has already been pushed to Google Sheets.
 */
@Entity(
    tableName = "habit_logs",
    indices = [Index(value = ["habitId", "dateEpochDay"], unique = true)]
)
data class HabitLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val dateEpochDay: Long,
    val completed: Boolean = true,
    val completedAtMillis: Long = System.currentTimeMillis(),
    val synced: Boolean = false
)
