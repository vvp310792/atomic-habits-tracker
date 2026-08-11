package com.atomichabits.tracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single habit definition.
 *
 * [id] is the local Room row id - it is NOT stable across a reinstall (a fresh
 * install starts autoGenerate back at 1). [syncId] is a UUID generated once when
 * the habit is first created and never changes; it's what ties a habit to its
 * row in Google Sheets and to its logs, so a reinstall can be matched back up
 * correctly even though [id] itself is different afterwards.
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
 *
 * [timeOfDay] groups the habit into one of the Home screen's three sections:
 * "MORNING", "DAY", or "EVENING".
 *
 * [category] tags the habit with one of 7 fixed life areas (CAREER, INTELLECT,
 * SELF_DEVELOPMENT, FINANCE, SOCIETY, HEALTH, FAMILY) - each has its own fixed
 * color (see util/Categories.kt), which is what [colorHex] is derived from.
 *
 * Habit stacking (Clear's "After [CURRENT HABIT], I will [NEW HABIT]"):
 * [stackAnchorId] is the syncId of whatever this habit is chained after - either
 * an [AnchorHabit] (an already-established routine, not itself tracked) or
 * another tracked [Habit] (so the chain can keep growing as habits mature).
 * [stackAnchorType] disambiguates which table [stackAnchorId] refers to:
 * "ANCHOR" or "HABIT" ("" = not stacked onto anything). [stackAnchorLabel] is a
 * cached copy of the anchor's display name, so the chain still reads sensibly
 * even if the anchor is later renamed or removed.
 */
@Entity(tableName = "habits", indices = [Index(value = ["syncId"], unique = true)])
data class Habit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = "",
    val name: String,
    val emoji: String = "\u2705",
    val colorHex: String = "#7C6CF0",
    val category: String = "SELF_DEVELOPMENT",
    val activeDays: Int = 127,
    val timeOfDay: String = "MORNING",
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 9,
    val reminderMinute: Int = 0,
    val lawObvious: String = "",
    val lawAttractive: String = "",
    val lawEasy: String = "",
    val lawSatisfying: String = "",
    val createdAtEpochDay: Long = 0,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val stackAnchorId: String = "",
    val stackAnchorType: String = "",
    val stackAnchorLabel: String = ""
)
