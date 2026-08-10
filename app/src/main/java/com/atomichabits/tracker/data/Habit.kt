package com.atomichabits.tracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single habit definition.
 *
 * [activeDays] is a bitmask, bit 0 = Monday ... bit 6 = Sunday (matches [java.time.DayOfWeek].value - 1).
 * A value of 127 (0b1111111) means "every day".
 *
 * The four `law*` fields let the user fill in James Clear's 4 Laws of Behavior Change
 * (from "Atomic Habits") for this specific habit:
 *   1. lawObvious     -> Make it Obvious   (cue: where/when)
 *   2. lawAttractive  -> Make it Attractive (habit stacking / temptation bundling)
 *   3. lawEasy        -> Make it Easy      (two-minute version / friction reduction)
 *   4. lawSatisfying  -> Make it Satisfying (immediate reward / tracking)
 */
@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String = "\u2705",
    val colorHex: String = "#7C6CF0",
    val activeDays: Int = 127,
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 9,
    val reminderMinute: Int = 0,
    val lawObvious: String = "",
    val lawAttractive: String = "",
    val lawEasy: String = "",
    val lawSatisfying: String = "",
    val createdAtEpochDay: Long = 0,
    val archived: Boolean = false,
    val sortOrder: Int = 0
)
