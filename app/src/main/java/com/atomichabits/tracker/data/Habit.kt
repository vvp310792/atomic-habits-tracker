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
 * [stackAnchorId] is the syncId of whatever habit this one is chained after -
 * since every habit is now the same universal entity (tracked or not), this can
 * be any other habit. [stackAnchorType] is a legacy field kept for backward
 * compatibility with pre-unification data ("ANCHOR" or "HABIT", "" = not
 * stacked onto anything) but is no longer used to disambiguate lookups.
 * [stackAnchorLabel] is a cached copy of the target's display name, so the
 * chain still reads sensibly even if it's later renamed or removed.
 *
 * [identityId] optionally links this habit to an [Identity] (James Clear's
 * identity-based habits) - each completion of the habit counts as a "vote"
 * for that identity. [identityLabel] caches the statement text for display,
 * same pattern as [stackAnchorLabel].
 *
 * Every habit is one universal entity, classified by two independent flags:
 * [qualityType] ("USEFUL" | "HARMFUL" | "NEUTRAL" | "DESIRED") is the honest,
 * judgment-free tag from the Habit Scorecard exercise - what kind of thing this
 * is. [isTracked] is whether it's *also* being actively tracked (shows on the
 * Today screen, has logs/streaks/reminders) versus just existing as a library
 * reference (a stacking anchor, an Impulse-screen link target, or a backlog
 * idea). A habit can be USEFUL and tracked, USEFUL and not-yet-tracked, etc. -
 * the two are independent. [alternativeSuggestion]/[whyItMatters] are only
 * meaningful for HARMFUL entries (see FirestoreSyncManager/ImpulseScreen).
 */
@Entity(tableName = "habits", indices = [Index(value = ["syncId"], unique = true)])
data class Habit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val syncId: String = "",
    val name: String,
    val emoji: String = "\u2705",
    val colorHex: String = "#7C6CF0",
    val category: String = "SELF_DEVELOPMENT",
    val qualityType: String = "USEFUL", // "USEFUL" | "HARMFUL" | "NEUTRAL" | "DESIRED"
    val isTracked: Boolean = true,
    val activeDays: Int = 127,
    val timeOfDay: String = "MORNING",
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 9,
    val reminderMinute: Int = 0,
    val lawObvious: String = "",
    val lawAttractive: String = "",
    val lawEasy: String = "",
    val lawSatisfying: String = "",
    val alternativeSuggestion: String = "",
    val whyItMatters: String = "",
    val createdAtEpochDay: Long = 0,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val stackAnchorId: String = "",
    val stackAnchorType: String = "",
    val stackAnchorLabel: String = "",
    val identityId: String = "",
    val identityLabel: String = ""
)
