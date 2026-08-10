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

/**
 * Sends completed habit log rows to a Google Apps Script Web App, which appends
 * them to a Google Sheet. See /apps-script/HabitSheetSync.gs and
 * /docs/SETUP_GOOGLE_SHEETS.md for the counterpart that must be deployed once,
 * under the user's own Google account, before this can succeed.
 */
class SheetsExporter(private val webAppUrl: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Posts [rows] to the Apps Script endpoint. An empty list still performs a real
     * request (used as a connectivity check), it just sends an empty "rows" array.
     * @return true on HTTP 2xx from the Apps Script endpoint.
     */
    fun export(rows: List<Pair<Habit, HabitLog>>): Boolean {
        if (webAppUrl.isBlank()) return false

        val array = JSONArray()
        rows.forEach { (habit, log) ->
            val row = JSONObject()
            row.put("habitName", habit.name)
            row.put("date", LocalDate.ofEpochDay(log.dateEpochDay).toString())
            row.put("completed", log.completed)
            row.put("completedAt", log.completedAtMillis)
            array.put(row)
        }
        val payload = JSONObject().put("rows", array)

        val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(webAppUrl).post(body).build()

        return try {
            client.newCall(request).execute().use { response -> response.isSuccessful }
        } catch (e: IOException) {
            false
        }
    }

    /** Connectivity check used by the "Test connection" button in Settings. */
    fun ping(): Boolean = export(emptyList())
}
