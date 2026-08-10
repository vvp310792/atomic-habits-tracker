package com.atomichabits.tracker.sheets

import com.atomichabits.tracker.data.Habit
import com.atomichabits.tracker.data.HabitLog
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** A habit definition as parsed back from the sheet on restore. */
data class RemoteHabit(
    val syncId: String,
    val name: String,
    val emoji: String,
    val colorHex: String,
    val category: String,
    val activeDays: Int,
    val timeOfDay: String,
    val reminderEnabled: Boolean,
    val reminderHour: Int,
    val reminderMinute: Int,
    val lawObvious: String,
    val lawAttractive: String,
    val lawEasy: String,
    val lawSatisfying: String,
    val createdAtEpochDay: Long,
    val archived: Boolean
)

/** A completion log entry as parsed back from the sheet on restore. */
data class RemoteLog(
    val syncId: String,
    val dateEpochDay: Long,
    val completed: Boolean,
    val completedAtMillis: Long
)

data class RemoteData(val habits: List<RemoteHabit>, val logs: List<RemoteLog>)

/**
 * Talks to a Google Apps Script Web App (see /apps-script/HabitSheetSync.gs) that
 * stores habit definitions + completion logs in the user's own Google Sheet, keyed
 * by [Habit.syncId] (stable across reinstalls, unlike the local Room [Habit.id]).
 */
class SheetsExporter(private val webAppUrl: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * Pushes the full current set of habit definitions (so edits/archiving round-trip)
     * plus any not-yet-synced logs. Safe to call repeatedly - the Apps Script side
     * upserts by syncId, so re-sending the same data is harmless.
     */
    fun push(habits: List<Habit>, logs: List<Pair<Habit, HabitLog>>): Boolean {
        if (webAppUrl.isBlank()) return false

        val habitsArray = JSONArray()
        habits.forEach { h ->
            habitsArray.put(
                JSONObject().apply {
                    put("syncId", h.syncId)
                    put("name", h.name)
                    put("emoji", h.emoji)
                    put("colorHex", h.colorHex)
                    put("category", h.category)
                    put("activeDays", h.activeDays)
                    put("timeOfDay", h.timeOfDay)
                    put("reminderEnabled", h.reminderEnabled)
                    put("reminderHour", h.reminderHour)
                    put("reminderMinute", h.reminderMinute)
                    put("lawObvious", h.lawObvious)
                    put("lawAttractive", h.lawAttractive)
                    put("lawEasy", h.lawEasy)
                    put("lawSatisfying", h.lawSatisfying)
                    put("createdAtEpochDay", h.createdAtEpochDay)
                    put("archived", h.archived)
                }
            )
        }

        val logsArray = JSONArray()
        logs.forEach { (habit, log) ->
            logsArray.put(
                JSONObject().apply {
                    put("syncId", habit.syncId)
                    put("date", LocalDate.ofEpochDay(log.dateEpochDay).toString())
                    put("completed", log.completed)
                    put("completedAt", log.completedAtMillis)
                }
            )
        }

        val payload = JSONObject().put("habits", habitsArray).put("logs", logsArray)
        val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(webAppUrl).post(body).build()

        return try {
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: IOException) {
            false
        }
    }

    /** Connectivity check used by the "Test connection" button in Settings. */
    fun ping(): Boolean = push(emptyList(), emptyList())

    /** Fetches everything currently stored remotely, for restoring after a reinstall. */
    fun pull(): RemoteData? {
        if (webAppUrl.isBlank()) return null
        val request = Request.Builder().url(webAppUrl).get().build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val json = JSONObject(response.body?.string().orEmpty())
                if (!json.optBoolean("ok", false)) return null

                val habits = mutableListOf<RemoteHabit>()
                val habitsJson = json.optJSONArray("habits") ?: JSONArray()
                for (i in 0 until habitsJson.length()) {
                    val h = habitsJson.getJSONObject(i)
                    val syncId = h.optString("syncId")
                    if (syncId.isBlank()) continue
                    habits.add(
                        RemoteHabit(
                            syncId = syncId,
                            name = h.optString("name"),
                            emoji = h.optString("emoji", "\u2705"),
                            colorHex = h.optString("colorHex", "#7C6CF0"),
                            category = h.optString("category", "SELF_DEVELOPMENT"),
                            activeDays = h.optInt("activeDays", 127),
                            timeOfDay = h.optString("timeOfDay", "MORNING"),
                            reminderEnabled = h.optBoolean("reminderEnabled", false),
                            reminderHour = h.optInt("reminderHour", 9),
                            reminderMinute = h.optInt("reminderMinute", 0),
                            lawObvious = h.optString("lawObvious"),
                            lawAttractive = h.optString("lawAttractive"),
                            lawEasy = h.optString("lawEasy"),
                            lawSatisfying = h.optString("lawSatisfying"),
                            createdAtEpochDay = h.optLong("createdAtEpochDay", LocalDate.now().toEpochDay()),
                            archived = h.optBoolean("archived", false)
                        )
                    )
                }

                val logs = mutableListOf<RemoteLog>()
                val logsJson = json.optJSONArray("logs") ?: JSONArray()
                for (i in 0 until logsJson.length()) {
                    val l = logsJson.getJSONObject(i)
                    val syncId = l.optString("syncId")
                    val dateStr = l.optString("date")
                    if (syncId.isBlank() || dateStr.isBlank()) continue
                    logs.add(
                        RemoteLog(
                            syncId = syncId,
                            dateEpochDay = runCatching { LocalDate.parse(dateStr).toEpochDay() }
                                .getOrDefault(LocalDate.now().toEpochDay()),
                            completed = l.optBoolean("completed", true),
                            completedAtMillis = l.optLong("completedAt", System.currentTimeMillis())
                        )
                    )
                }

                RemoteData(habits, logs)
            }
        } catch (e: Exception) {
            null
        }
    }
}
