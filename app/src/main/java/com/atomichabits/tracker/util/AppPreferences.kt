package com.atomichabits.tracker.util

import android.content.Context

/**
 * App-wide settings that aren't tied to a specific habit, so don't belong in
 * Room. Plain SharedPreferences is the right tool here rather than a DB table -
 * there's currently just one setting.
 */
object AppPreferences {
    private const val PREFS_NAME = "app_prefs"
    private const val KEY_BREATHING_PHASE_SECONDS = "breathing_phase_seconds"

    /** Allowed values for the box-breathing phase duration, shown as a picker in Settings. */
    val BREATHING_PHASE_OPTIONS = listOf(3, 4, 5, 6)
    const val DEFAULT_BREATHING_PHASE_SECONDS = 3

    fun getBreathingPhaseSeconds(context: Context): Int {
        val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_BREATHING_PHASE_SECONDS, DEFAULT_BREATHING_PHASE_SECONDS)
        return if (stored in BREATHING_PHASE_OPTIONS) stored else DEFAULT_BREATHING_PHASE_SECONDS
    }

    fun setBreathingPhaseSeconds(context: Context, seconds: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_BREATHING_PHASE_SECONDS, seconds)
            .apply()
    }
}
