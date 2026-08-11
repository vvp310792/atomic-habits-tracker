package com.atomichabits.tracker.sync

import android.content.Context
import com.atomichabits.tracker.data.AnchorHabit
import com.atomichabits.tracker.data.AppDatabase
import com.atomichabits.tracker.data.Habit
import com.atomichabits.tracker.data.HabitLog
import com.atomichabits.tracker.data.ImpulseLog
import com.atomichabits.tracker.notifications.ReminderScheduler
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Every habit and completion log is stored under `users/{uid}/habits/{syncId}`
 * and `users/{uid}/logs/{syncId}_{dateEpochDay}` in Firestore. [Habit.syncId]
 * (a UUID generated once when the habit is created) is what makes this safe
 * across reinstalls: it never changes, unlike the local Room row id.
 *
 * Writes (push*) go straight to Firestore; Firestore's own SDK queues them
 * offline and retries automatically, so no separate WorkManager retry logic
 * is needed here. Realtime listeners (start/stop) merge whatever's in
 * Firestore into the local Room cache, so the Home screen keeps reading from
 * Room (fast, offline-first) while staying in sync with the cloud.
 */
class FirestoreSyncManager(private val database: AppDatabase, private val appContext: Context) {

    private val db = FirebaseFirestore.getInstance()
    private var habitsListener: ListenerRegistration? = null
    private var logsListener: ListenerRegistration? = null
    private var anchorsListener: ListenerRegistration? = null
    private var impulsesListener: ListenerRegistration? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private fun habitsRef(uid: String) = db.collection("users").document(uid).collection("habits")
    private fun logsRef(uid: String) = db.collection("users").document(uid).collection("logs")
    private fun anchorsRef(uid: String) = db.collection("users").document(uid).collection("anchors")
    private fun impulsesRef(uid: String) = db.collection("users").document(uid).collection("impulses")

    /** Attaches realtime listeners for [uid]'s data. Call on sign-in. Safe to call again to re-attach. */
    fun start(uid: String) {
        stop()
        habitsListener = habitsRef(uid).addSnapshotListener { snapshot, _ ->
            val changes = snapshot?.documentChanges ?: return@addSnapshotListener
            scope.launch { mergeHabitChanges(changes) }
        }
        logsListener = logsRef(uid).addSnapshotListener { snapshot, _ ->
            val changes = snapshot?.documentChanges ?: return@addSnapshotListener
            scope.launch { mergeLogChanges(changes) }
        }
        anchorsListener = anchorsRef(uid).addSnapshotListener { snapshot, _ ->
            val changes = snapshot?.documentChanges ?: return@addSnapshotListener
            scope.launch { mergeAnchorChanges(changes) }
        }
        impulsesListener = impulsesRef(uid).addSnapshotListener { snapshot, _ ->
            val changes = snapshot?.documentChanges ?: return@addSnapshotListener
            scope.launch { mergeImpulseChanges(changes) }
        }
    }

    /** Detaches listeners. Call on sign-out. */
    fun stop() {
        habitsListener?.remove()
        logsListener?.remove()
        anchorsListener?.remove()
        impulsesListener?.remove()
        habitsListener = null
        logsListener = null
        anchorsListener = null
        impulsesListener = null
    }

    fun pushHabit(uid: String, habit: Habit) {
        val data = mapOf(
            "name" to habit.name,
            "emoji" to habit.emoji,
            "colorHex" to habit.colorHex,
            "category" to habit.category,
            "activeDays" to habit.activeDays,
            "timeOfDay" to habit.timeOfDay,
            "reminderEnabled" to habit.reminderEnabled,
            "reminderHour" to habit.reminderHour,
            "reminderMinute" to habit.reminderMinute,
            "lawObvious" to habit.lawObvious,
            "lawAttractive" to habit.lawAttractive,
            "lawEasy" to habit.lawEasy,
            "lawSatisfying" to habit.lawSatisfying,
            "createdAtEpochDay" to habit.createdAtEpochDay,
            "archived" to habit.archived,
            "stackAnchorId" to habit.stackAnchorId,
            "sortOrder" to habit.sortOrder,
            "stackAnchorType" to habit.stackAnchorType,
            "stackAnchorLabel" to habit.stackAnchorLabel,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        habitsRef(uid).document(habit.syncId).set(data, SetOptions.merge())
    }

    fun pushAnchor(uid: String, anchor: AnchorHabit) {
        if (anchor.syncId.isBlank()) return // guards against a bad pre-existing local row; see AnchorRepository.save
        val data = mapOf(
            "name" to anchor.name,
            "type" to anchor.type,
            "createdAtEpochDay" to anchor.createdAtEpochDay,
            "archived" to anchor.archived,
            "alternativeSuggestion" to anchor.alternativeSuggestion,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        anchorsRef(uid).document(anchor.syncId).set(data, SetOptions.merge())
    }

    fun pushLog(uid: String, habit: Habit, log: HabitLog) {
        val docId = logDocId(habit.syncId, log.dateEpochDay)
        val data = mapOf(
            "habitSyncId" to habit.syncId,
            "dateEpochDay" to log.dateEpochDay,
            "completed" to log.completed,
            "completedAtMillis" to log.completedAtMillis
        )
        logsRef(uid).document(docId).set(data)
    }

    /** Called when a completion is un-toggled locally, so the cloud copy doesn't linger as "completed". */
    fun deleteLog(uid: String, habitSyncId: String, dateEpochDay: Long) {
        logsRef(uid).document(logDocId(habitSyncId, dateEpochDay)).delete()
    }

    fun pushImpulseLog(uid: String, log: ImpulseLog) {
        if (log.syncId.isBlank()) return
        val data = mapOf(
            "dateEpochDay" to log.dateEpochDay,
            "timestampMillis" to log.timestampMillis,
            "outcome" to log.outcome,
            "triggerTags" to log.triggerTags,
            "note" to log.note,
            "linkedHarmfulAnchorId" to log.linkedHarmfulAnchorId,
            "linkedHarmfulAnchorLabel" to log.linkedHarmfulAnchorLabel
        )
        impulsesRef(uid).document(log.syncId).set(data, SetOptions.merge())
    }

    private fun logDocId(habitSyncId: String, dateEpochDay: Long) = "${habitSyncId}_$dateEpochDay"

    /** Pushes everything currently local - used right after first sign-in, and as a manual "sync now". */
    suspend fun pushAll(uid: String) {
        val habitDao = database.habitDao()
        val logDao = database.habitLogDao()
        val habits = habitDao.getAllOnce()
        habits.forEach { habit ->
            pushHabit(uid, habit)
            logDao.getAllForHabitOnce(habit.id).forEach { log -> pushLog(uid, habit, log) }
        }
        database.anchorHabitDao().getAllOnce().forEach { anchor -> pushAnchor(uid, anchor) }
        database.impulseLogDao().getAllOnce().forEach { log -> pushImpulseLog(uid, log) }
    }

    private suspend fun mergeHabitChanges(changes: List<DocumentChange>) {
        val habitDao = database.habitDao()
        changes.forEach { change ->
            val doc = change.document
            // Habits are archived, never hard-deleted from Firestore in normal use, so
            // a REMOVED change here is unexpected - ignore it rather than risk deleting
            // local data based on what might just be a transient hiccup.
            if (change.type == DocumentChange.Type.REMOVED) return@forEach

            val syncId = doc.id
            val name = doc.getString("name") ?: return@forEach
            val existing = habitDao.getBySyncId(syncId)
            val habit = Habit(
                id = existing?.id ?: 0,
                syncId = syncId,
                name = name,
                emoji = doc.getString("emoji") ?: "\u2705",
                colorHex = doc.getString("colorHex") ?: "#7C6CF0",
                category = doc.getString("category") ?: "SELF_DEVELOPMENT",
                activeDays = (doc.getLong("activeDays") ?: 127L).toInt(),
                timeOfDay = doc.getString("timeOfDay") ?: "MORNING",
                reminderEnabled = doc.getBoolean("reminderEnabled") ?: false,
                reminderHour = (doc.getLong("reminderHour") ?: 9L).toInt(),
                reminderMinute = (doc.getLong("reminderMinute") ?: 0L).toInt(),
                lawObvious = doc.getString("lawObvious") ?: "",
                lawAttractive = doc.getString("lawAttractive") ?: "",
                lawEasy = doc.getString("lawEasy") ?: "",
                lawSatisfying = doc.getString("lawSatisfying") ?: "",
                createdAtEpochDay = doc.getLong("createdAtEpochDay") ?: LocalDate.now().toEpochDay(),
                archived = doc.getBoolean("archived") ?: false,
                sortOrder = (doc.getLong("sortOrder"))?.toInt() ?: existing?.sortOrder ?: 0,
                stackAnchorId = doc.getString("stackAnchorId") ?: "",
                stackAnchorType = doc.getString("stackAnchorType") ?: "",
                stackAnchorLabel = doc.getString("stackAnchorLabel") ?: ""
            )
            val newLocalId = habitDao.upsert(habit)
            val localId = existing?.id ?: newLocalId

            if (habit.reminderEnabled && !habit.archived) {
                ReminderScheduler.schedule(appContext, habit.copy(id = localId))
            } else {
                ReminderScheduler.cancel(appContext, localId)
            }
        }
    }

    private suspend fun mergeLogChanges(changes: List<DocumentChange>) {
        val habitDao = database.habitDao()
        val logDao = database.habitLogDao()
        changes.forEach { change ->
            val doc = change.document
            val habitSyncId = doc.getString("habitSyncId") ?: return@forEach
            val dateEpochDay = doc.getLong("dateEpochDay") ?: return@forEach
            val localHabit = habitDao.getBySyncId(habitSyncId) ?: return@forEach

            if (change.type == DocumentChange.Type.REMOVED) {
                logDao.deleteForDate(localHabit.id, dateEpochDay)
                return@forEach
            }

            val completed = doc.getBoolean("completed") ?: true
            val completedAt = doc.getLong("completedAtMillis") ?: System.currentTimeMillis()
            logDao.upsert(
                HabitLog(
                    habitId = localHabit.id,
                    dateEpochDay = dateEpochDay,
                    completed = completed,
                    completedAtMillis = completedAt,
                    synced = true
                )
            )
        }
    }

    private suspend fun mergeAnchorChanges(changes: List<DocumentChange>) {
        val anchorDao = database.anchorHabitDao()
        changes.forEach { change ->
            if (change.type == DocumentChange.Type.REMOVED) return@forEach
            val doc = change.document
            val syncId = doc.id
            val name = doc.getString("name") ?: return@forEach
            val existing = anchorDao.getBySyncId(syncId)
            anchorDao.upsert(
                AnchorHabit(
                    id = existing?.id ?: 0,
                    syncId = syncId,
                    name = name,
                    type = doc.getString("type") ?: "USEFUL",
                    createdAtEpochDay = doc.getLong("createdAtEpochDay") ?: LocalDate.now().toEpochDay(),
                    archived = doc.getBoolean("archived") ?: false,
                    alternativeSuggestion = doc.getString("alternativeSuggestion") ?: ""
                )
            )
        }
    }

    private suspend fun mergeImpulseChanges(changes: List<DocumentChange>) {
        val impulseDao = database.impulseLogDao()
        changes.forEach { change ->
            if (change.type == DocumentChange.Type.REMOVED) return@forEach
            val doc = change.document
            val syncId = doc.id
            val existing = impulseDao.getBySyncId(syncId)
            impulseDao.upsert(
                ImpulseLog(
                    id = existing?.id ?: 0,
                    syncId = syncId,
                    dateEpochDay = doc.getLong("dateEpochDay") ?: LocalDate.now().toEpochDay(),
                    timestampMillis = doc.getLong("timestampMillis") ?: System.currentTimeMillis(),
                    outcome = doc.getString("outcome") ?: "CHECK",
                    triggerTags = doc.getString("triggerTags") ?: "",
                    note = doc.getString("note") ?: "",
                    linkedHarmfulAnchorId = doc.getString("linkedHarmfulAnchorId") ?: "",
                    linkedHarmfulAnchorLabel = doc.getString("linkedHarmfulAnchorLabel") ?: ""
                )
            )
        }
    }
}
