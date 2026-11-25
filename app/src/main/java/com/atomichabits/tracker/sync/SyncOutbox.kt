package com.atomichabits.tracker.sync

import android.content.Context
import org.json.JSONObject
import java.util.UUID

internal data class QueuedWrite(
    val collection: String, val id: String, val fields: Map<String, Any>?,
    val merge: Boolean, val token: String = UUID.randomUUID().toString(),
    val queuedAt: Long = System.currentTimeMillis()
)

internal class SyncOutbox(context: Context) {
    private val prefs = context.getSharedPreferences("sync_outbox_v2", Context.MODE_PRIVATE)
    private fun key(uid: String, collection: String, id: String) = "$uid/$collection/$id"
    @Synchronized fun put(uid: String, op: QueuedWrite): Boolean {
        val obj = JSONObject().put("collection", op.collection).put("id", op.id)
            .put("merge", op.merge).put("token", op.token).put("queuedAt", op.queuedAt)
            .put("fields", op.fields?.let { JSONObject(it) } ?: JSONObject.NULL)
        return prefs.edit().putString(key(uid, op.collection, op.id), obj.toString()).commit()
    }
    @Synchronized fun list(uid: String): List<QueuedWrite> = prefs.all.entries
        .filter { it.key.startsWith("$uid/") }.map { entry ->
            val o = JSONObject(entry.value as String)
            val fields = o.optJSONObject("fields")?.let { x -> x.keys().asSequence().associateWith { x.get(it) } }
            QueuedWrite(o.getString("collection"), o.getString("id"), fields, o.getBoolean("merge"), o.getString("token"), o.getLong("queuedAt"))
        }.sortedWith(compareBy<QueuedWrite> { it.queuedAt }.thenBy { it.token })
    @Synchronized fun contains(uid: String, collection: String, id: String) = prefs.contains(key(uid, collection, id))
    @Synchronized fun acknowledge(uid: String, op: QueuedWrite) {
        val k = key(uid, op.collection, op.id)
        val saved = prefs.getString(k, null) ?: return
        if (JSONObject(saved).getString("token") == op.token) prefs.edit().remove(k).commit()
    }
}
