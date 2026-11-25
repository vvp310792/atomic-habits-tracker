package com.atomichabits.tracker.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.atomichabits.tracker.BuildConfig
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import com.atomichabits.tracker.data.AppDatabase
import com.atomichabits.tracker.data.Habit
import com.atomichabits.tracker.data.HabitJournalEntry
import com.atomichabits.tracker.data.HabitLog
import com.atomichabits.tracker.data.Identity
import com.atomichabits.tracker.data.ImpulseLog
import com.atomichabits.tracker.data.PausePeriod
import com.atomichabits.tracker.notifications.ReminderScheduler
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
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
 * Local edits enter an account-bound durable outbox; only server acknowledgments
 * remove them. Authoritative snapshots are applied serially, never from cache.
 */
class FirestoreSyncManager(private val database: AppDatabase, private val appContext: Context) {

    private val db = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val outbox = SyncOutbox(appContext)
    private val prefs = appContext.getSharedPreferences("sync_state_v2", Context.MODE_PRIVATE)
    private val _status = MutableStateFlow(SyncStatus())
    val status = _status.asStateFlow()
    @Volatile private var activeUid: String? = null
    private var sessionJob: Job? = null
    private val drainLock = Mutex()
    private val sessionLock = Mutex()
    @Volatile private var initializedUid: String? = null
    private data class InFlight(val uid: String, val op: QueuedWrite, val task: Task<Void>)
    private var inFlight: InFlight? = null
    private val eventTimes = mutableMapOf<String, Long>()
    private val deviceId = prefs.getString("device", null) ?: UUID.randomUUID().toString().also {
        prefs.edit().putString("device", it).apply()
    }
    private data class Change(val document: DocumentSnapshot, val type: DocumentChange.Type)
    private data class SnapshotEvent(val collection: String, val snapshot: QuerySnapshot)
    private val deferredLogs = linkedMapOf<String, Change>()
    private val deferredJournal = linkedMapOf<String, Change>()
    private val collections = listOf("habits", "logs", "impulses", "identities", "journal", "pausePeriods")

    private fun collection(uid: String, name: String) = db.collection("users").document(uid).collection(name)
    private fun code(e: Throwable): String = (e as? FirebaseFirestoreException)?.code?.name ?: e.javaClass.simpleName

    @Synchronized private fun telemetry(uid: String, event: String, error: String? = null) {
        if (activeUid != uid) return
        val now = System.currentTimeMillis()
        val key = "$uid/$event/" + (error ?: "OK")
        if (now - (eventTimes[key] ?: 0) < 600_000) return
        eventTimes[key] = now
        val fields = mapOf("event" to event, "code" to (error ?: "OK"), "appVersion" to BuildConfig.VERSION_NAME,
            "pending" to _status.value.pending, "at" to FieldValue.serverTimestamp())
        // One document per device/day/event/code, throttled. No exception messages or personal text.
        val eventId = deviceId + "_" + LocalDate.now().toEpochDay() + "_" + event + "_" + (error ?: "OK")
        collection(uid, "syncEvents").document(eventId).set(fields, SetOptions.merge())
        collection(uid, "syncStatus").document(deviceId).set(fields + mapOf(
            "lastServerReadMillis" to (_status.value.lastServerRead ?: 0L),
            "lastWriteAckMillis" to (_status.value.lastWriteAck ?: 0L)
        ), SetOptions.merge())
    }

    private fun failure(uid: String, event: String, e: Throwable) {
        if (activeUid != uid) return
        val c = code(e)
        _status.update { it.copy(checking = false, error = c) }
        Log.w("HabitsSync", "$event: $c")
        telemetry(uid, event, c)
    }
    private fun pending(uid: String) {
        if (activeUid == uid) _status.update { it.copy(pending = outbox.list(uid).size) }
    }

    private fun queue(uid: String, collection: String, id: String, fields: Map<String, Any>?, merge: Boolean = true) {
        if (id.isBlank()) return
        if (prefs.getString("owner", uid) != uid) return
        val op = QueuedWrite(collection, id, fields, merge)
        if (!outbox.put(uid, op)) {
            failure(uid, "outbox_save", IllegalStateException("outbox"))
            return
        }
        pending(uid)
        scope.launch { try { drain(uid) } catch (e: kotlinx.coroutines.TimeoutCancellationException) { failure(uid, "write_timeout", e) } catch (e: CancellationException) { throw e } catch (e: Exception) { failure(uid, "write", e) } }
    }

    private fun cloudFields(name: String, fields: Map<String, Any>): Map<String, Any> =
        if (name == "habits" || name == "identities") fields + ("updatedAt" to FieldValue.serverTimestamp()) else fields

    private suspend fun drain(uid: String, wait: Boolean = false) {
        if (initializedUid != uid || activeUid != uid) return
        if (wait) drainLock.lock() else if (!drainLock.tryLock()) return
        try {
            while (activeUid == uid && initializedUid == uid) {
                val existingFlight = inFlight?.takeIf { it.uid == uid }
                val nextOp = if (existingFlight == null) outbox.list(uid).firstOrNull() else null
                if (existingFlight == null && nextOp == null) break
                val flight = existingFlight ?: run {
                    val op = nextOp!!
                    val ref = collection(uid, op.collection).document(op.id)
                    val task = if (op.fields == null) ref.delete()
                        else if (op.merge) ref.set(cloudFields(op.collection, op.fields), SetOptions.merge())
                        else ref.set(cloudFields(op.collection, op.fields))
                    InFlight(uid, op, task).also { inFlight = it }
                }
                try {
                    // A timeout does not cancel the SDK task. Reuse it, avoiding repeated
                    // writes to the SDK's offline queue until that operation settles.
                    withTimeout(25_000) { flight.task.await() }
                } catch (e: Exception) {
                    if (flight.task.isComplete) inFlight = null
                    throw e
                }
                inFlight = null
                outbox.acknowledge(uid, flight.op)
                if (activeUid == uid) {
                    val now = System.currentTimeMillis()
                    prefs.edit().putLong("ack_$uid", now).apply()
                    _status.update { it.copy(lastWriteAck = now, pending = outbox.list(uid).size, error = null) }
                    telemetry(uid, "write_ack")
                }
            }
        } finally {
            drainLock.unlock()
        }
    }

    /** One account owns this local Room database. Never mix accounts automatically. */
    @Synchronized fun start(uid: String) {
        stop()
        val owner = prefs.getString("owner", null)
        if (owner != null && owner != uid) {
            _status.value = SyncStatus(signedIn = true, error = "ACCOUNT_CHANGED")
            activeUid = null
            return
        }
        activeUid = uid
        prefs.edit().putString("owner", uid).apply()
        _status.value = SyncStatus(signedIn = true, checking = true, pending = outbox.list(uid).size,
            lastServerRead = prefs.getLong("read_$uid", 0).takeIf { it > 0 },
            lastWriteAck = prefs.getLong("ack_$uid", 0).takeIf { it > 0 })
        sessionJob = scope.launch {
            // Cancelled sessions finish disposing their own listeners before a new one starts.
            sessionLock.withLock {
                while (isActive && activeUid == uid) {
                    try {
                        _status.update { it.copy(checking = true) }
                        if (!prefs.getBoolean("recovered_$uid", false)) {
                            withTimeout(120_000) { recoverLocal(uid) }
                            check(prefs.edit().putBoolean("recovered_$uid", true).commit())
                        }
                        initializedUid = uid
                        drain(uid, wait = true)
                        listenSession(uid)
                    } catch (e: CancellationException) {
                        if (!isActive || activeUid != uid) throw e
                        failure(uid, "sync_timeout", e)
                    } catch (e: Exception) {
                        failure(uid, "sync", e)
                    }
                    delay(30_000)
                }
            }
        }
    }

    @Synchronized fun stop() {
        activeUid = null
        initializedUid = null
        sessionJob?.cancel()
        sessionJob = null
        _status.value = SyncStatus()
    }

    /** Only the bound account can own locally edited data during sign-out. */
    val localOwnerUid: String? get() = prefs.getString("owner", null)

    private suspend fun listenSession(uid: String) {
        val channel = Channel<SnapshotEvent>(Channel.UNLIMITED)
        val previous = mutableMapOf<String, Map<String, DocumentSnapshot>>()
        val seen = mutableSetOf<String>()
        deferredLogs.clear(); deferredJournal.clear()
        fun listen(name: String) = collection(uid, name).addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (activeUid == uid) {
                if (error != null) channel.close(error)
                else if (snapshot != null) {
                    channel.trySend(SnapshotEvent(name, snapshot))
                }
            }
        }
        val registrations = collections.map { listen(it) }
        try {
            // Serialized authoritative snapshots; metadata-only confirmation is not lost.
            for (event in channel) {
                if (activeUid != uid) break
                val name = event.collection
                if (event.snapshot.metadata.isFromCache) {
                    seen.remove(name)
                    _status.update { it.copy(checking = true) }
                    continue
                }
                if (event.snapshot.metadata.hasPendingWrites()) continue
                val before = previous[name].orEmpty()
                val after = event.snapshot.documents.associateBy { it.id }
                val next = before.toMutableMap()
                val changes = mutableListOf<Change>()
                // Across reconnects, remove only records previously confirmed by the
                // server. Never delete unknown local-only rows on the initial merge.
                val knownKey = "known_" + uid + "_" + name
                val known = if (name == "logs" || name == "pausePeriods")
                    prefs.getStringSet(knownKey, emptySet()).orEmpty().toMutableSet() else mutableSetOf()
                if (name == "logs" || name == "pausePeriods") {
                    for (id in known - after.keys - before.keys) {
                        if (!outbox.contains(uid, name, id)) {
                            removeKnownLocal(name, id)
                            known.remove(id)
                        }
                    }
                }
                after.forEach { (id, doc) ->
                    if (!outbox.contains(uid, name, id)) {
                        if (before[id]?.data != doc.data) changes.add(Change(doc, DocumentChange.Type.MODIFIED))
                        next[id] = doc
                    }
                }
                before.forEach { (id, doc) ->
                    if (id !in after && !outbox.contains(uid, name, id)) {
                        changes.add(Change(doc, DocumentChange.Type.REMOVED))
                        next.remove(id)
                    }
                }
                when (name) {
                    "habits" -> {
                        mergeHabitChanges(changes)
                        val dl = deferredLogs.values.filterNot { outbox.contains(uid, "logs", it.document.id) }; deferredLogs.clear(); mergeLogChanges(dl)
                        val dj = deferredJournal.values.filterNot { outbox.contains(uid, "journal", it.document.id) }; deferredJournal.clear(); mergeJournalChanges(dj)
                    }
                    "logs" -> mergeLogChanges(changes)
                    "impulses" -> mergeImpulseChanges(changes)
                    "identities" -> mergeIdentityChanges(changes)
                    "journal" -> mergeJournalChanges(changes)
                    "pausePeriods" -> mergePausePeriodChanges(changes)
                }
                previous[name] = next
                if (name == "logs" || name == "pausePeriods") {
                    // Keep keys of pending deletes until a server snapshot confirms them.
                    val retained = known.filter { outbox.contains(uid, name, it) }
                    prefs.edit().putStringSet(knownKey, (after.keys + retained).toSet()).apply()
                }
                seen.add(name)
                if (seen.containsAll(collections)) {
                    val now = System.currentTimeMillis()
                    prefs.edit().putLong("read_$uid", now).apply()
                    _status.update { it.copy(checking = false, lastServerRead = now, error = null, pending = outbox.list(uid).size) }
                    telemetry(uid, "server_read")
                    sendPreviousCrash(uid)
                }
                // A failed write is retried independently by a foreground timer below.
            }
        } finally { channel.cancel(); registrations.forEach { it.remove() } }
    }

    private suspend fun removeKnownLocal(name: String, id: String) {
        if (name == "pausePeriods") {
            database.pausePeriodDao().getBySyncId(id)?.let { database.pausePeriodDao().delete(it) }
        } else {
            val habit = database.habitDao().getBySyncId(id.substringBeforeLast('_')) ?: return
            val day = id.substringAfterLast('_').toLongOrNull() ?: return
            database.habitLogDao().deleteForDate(habit.id, day)
        }
    }

    private fun sendPreviousCrash(uid: String) {
        val time = prefs.getLong("crash_time", 0L)
        if (time <= 0L) return
        val crash = prefs.getString("crash_type", "Unknown") ?: "Unknown"
        val fields = mapOf("event" to "crash", "code" to crash, "appVersion" to BuildConfig.VERSION_NAME,
            "deviceTimeMillis" to time, "at" to FieldValue.serverTimestamp())
        collection(uid, "syncEvents").document(deviceId + "_crash_" + time).set(fields)
            .addOnSuccessListener {
                if (prefs.getLong("crash_time", 0L) == time) prefs.edit().remove("crash_time").remove("crash_type").apply()
            }
    }

    // Called from Application on a separate periodic coroutine. Pending writes are durable.
    suspend fun retryPending() {
        val uid = activeUid ?: return
        try { drain(uid) } catch (e: CancellationException) { if (!kotlin.coroutines.coroutineContext.isActive) throw e else failure(uid, "write_timeout", e) }
        catch (e: Exception) { failure(uid, "write", e) }
    }

    private fun habitFields(habit: Habit): Map<String, Any> = mapOf(
            "name" to habit.name,
            "emoji" to habit.emoji,
            "colorHex" to habit.colorHex,
            "category" to habit.category,
            "qualityType" to habit.qualityType,
            "isTracked" to habit.isTracked,
            "activeDays" to habit.activeDays,
            "timeOfDay" to habit.timeOfDay,
            "reminderEnabled" to habit.reminderEnabled,
            "reminderHour" to habit.reminderHour,
            "reminderMinute" to habit.reminderMinute,
            "lawObvious" to habit.lawObvious,
            "lawAttractive" to habit.lawAttractive,
            "lawEasy" to habit.lawEasy,
            "lawSatisfying" to habit.lawSatisfying,
            "alternativeSuggestion" to habit.alternativeSuggestion,
            "whyItMatters" to habit.whyItMatters,
            "createdAtEpochDay" to habit.createdAtEpochDay,
            "archived" to habit.archived,
            "stackAnchorId" to habit.stackAnchorId,
            "sortOrder" to habit.sortOrder,
            "stackAnchorType" to habit.stackAnchorType,
            "stackAnchorLabel" to habit.stackAnchorLabel,
            "identityId" to habit.identityId,
            "identityLabel" to habit.identityLabel,
            "difficultyNote" to habit.difficultyNote,
            "difficultyBumpedAtEpochDay" to habit.difficultyBumpedAtEpochDay,
            "temptationBundle" to habit.temptationBundle,
            "manuallyMastered" to habit.manuallyMastered,
            "selfBindingAction" to habit.selfBindingAction,
            "journalCycleStartEpochDay" to habit.journalCycleStartEpochDay
        )

    fun pushHabit(uid: String, habit: Habit) {
        queue(uid, "habits", habit.syncId, habitFields(habit))
    }

    private fun identityFields(identity: Identity): Map<String, Any> = mapOf(
            "statement" to identity.statement,
            "createdAtEpochDay" to identity.createdAtEpochDay,
            "archived" to identity.archived
        )

    fun pushIdentity(uid: String, identity: Identity) {
        queue(uid, "identities", identity.syncId, identityFields(identity))
    }

    private fun logFields(habit: Habit, log: HabitLog): Map<String, Any> = mapOf(
            "habitSyncId" to habit.syncId,
            "dateEpochDay" to log.dateEpochDay,
            "completed" to log.completed,
            "completedAtMillis" to log.completedAtMillis
        )

    fun pushLog(uid: String, habit: Habit, log: HabitLog) {
        queue(uid, "logs", logDocId(habit.syncId, log.dateEpochDay), logFields(habit, log), false)
    }

    /** Called when a completion is un-toggled locally, so the cloud copy doesn't linger as "completed". */
    fun deleteLog(uid: String, habitSyncId: String, dateEpochDay: Long) {
        queue(uid, "logs", logDocId(habitSyncId, dateEpochDay), null)
    }

    private fun impulseLogFields(log: ImpulseLog): Map<String, Any> = mapOf(
            "dateEpochDay" to log.dateEpochDay,
            "timestampMillis" to log.timestampMillis,
            "outcome" to log.outcome,
            "triggerTags" to log.triggerTags,
            "note" to log.note,
            "linkedHarmfulAnchorId" to log.linkedHarmfulAnchorId,
            "linkedHarmfulAnchorLabel" to log.linkedHarmfulAnchorLabel
        )

    fun pushImpulseLog(uid: String, log: ImpulseLog) {
        queue(uid, "impulses", log.syncId, impulseLogFields(log))
    }

    private fun journalEntryFields(entry: HabitJournalEntry): Map<String, Any> = mapOf(
            "habitSyncId" to entry.habitSyncId,
            "dateEpochDay" to entry.dateEpochDay,
            "todaysEvents" to entry.todaysEvents,
            "hadIncident" to entry.hadIncident,
            "hadSlip" to entry.hadSlip,
            "amount" to entry.amount,
            "whatIWanted" to entry.whatIWanted,
            "substituteBehavior" to entry.substituteBehavior,
            "substituteSucceeded" to entry.substituteSucceeded,
            "cycleStartEpochDay" to entry.cycleStartEpochDay
        )

    fun pushJournalEntry(uid: String, entry: HabitJournalEntry) {
        queue(uid, "journal", entry.syncId, journalEntryFields(entry))
    }

    private fun pausePeriodFields(period: PausePeriod): Map<String, Any> = mapOf(
            "startEpochDay" to period.startEpochDay,
            "endEpochDay" to period.endEpochDay,
            "label" to period.label,
            "pausedHabitSyncIds" to period.pausedHabitSyncIds
        )

    fun pushPausePeriod(uid: String, period: PausePeriod) {
        queue(uid, "pausePeriods", period.syncId, pausePeriodFields(period))
    }

    private fun logDocId(habitSyncId: String, dateEpochDay: Long) = "${habitSyncId}_$dateEpochDay"

    fun deletePausePeriod(uid: String, syncId: String) = queue(uid, "pausePeriods", syncId, null)

    /** On first upgrade/sign-in, keep a private pre-merge backup and recover
     * only missing definitions and records newer than the server history.
     * Never overwrite a server document or revive an older deleted log. */
    private suspend fun recoverLocal(uid: String) {
        db.waitForPendingWrites().await()
        val server = collections.associateWith { name ->
            collection(uid, name).get(Source.SERVER).await().documents.associateBy { it.id }
        }
        val habits = database.habitDao().getAllOnce()
        val local = mutableListOf<QueuedWrite>()
        habits.forEach { h ->
            if (h.syncId.isNotBlank()) {
                local.add(QueuedWrite("habits", h.syncId, habitFields(h), true))
                database.habitLogDao().getAllForHabitOnce(h.id).filter { it.completed }.forEach { l ->
                    local.add(QueuedWrite("logs", logDocId(h.syncId, l.dateEpochDay), logFields(h, l), false))
                }
            }
        }
        database.identityDao().getAllOnce().filter { it.syncId.isNotBlank() }.forEach {
            local.add(QueuedWrite("identities", it.syncId, identityFields(it), true))
        }
        database.impulseLogDao().getAllOnce().filter { it.syncId.isNotBlank() }.forEach {
            local.add(QueuedWrite("impulses", it.syncId, impulseLogFields(it), true))
        }
        database.habitJournalEntryDao().getAllOnce().filter { it.syncId.isNotBlank() }.forEach {
            local.add(QueuedWrite("journal", it.syncId, journalEntryFields(it), true))
        }
        database.pausePeriodDao().getAllOnce().filter { it.syncId.isNotBlank() }.forEach {
            local.add(QueuedWrite("pausePeriods", it.syncId, pausePeriodFields(it), true))
        }
        val backup = File(appContext.filesDir, "pre-sync-recovery-v2-$uid.json")
        if (!backup.exists()) {
            val tmp = File(backup.parentFile, backup.name + ".tmp")
            val arr = JSONArray()
            local.forEach { op -> arr.put(JSONObject().put("collection", op.collection).put("id", op.id).put("data", JSONObject(op.fields!!))) }
            tmp.writeText(arr.toString())
            check(tmp.renameTo(backup)) { "backup_failed" }
        }
        val lastDates = mapOf("logs" to "dateEpochDay", "journal" to "dateEpochDay", "impulses" to "dateEpochDay", "pausePeriods" to "startEpochDay")
            .mapValues { (name, field) -> server[name].orEmpty().values.mapNotNull { it.getLong(field) }.maxOrNull() }
        val newHabits = habits.filter { it.syncId !in server["habits"].orEmpty() }.map { it.syncId }.toSet()
        val existingJournalDays = server["journal"].orEmpty().values.map { it.getString("habitSyncId") to it.getLong("dateEpochDay") }.toSet()
        val planKey = "recovery_plan_$uid"
        fun key(op: QueuedWrite) = op.collection + "/" + op.id
        val plan = prefs.getStringSet(planKey, null)?.toMutableSet() ?: local.filter { op ->
            val fields = op.fields!!
            val habitId = fields["habitSyncId"] as? String
            val day = (fields[if (op.collection == "pausePeriods") "startEpochDay" else "dateEpochDay"] as? Number)?.toLong()
            val cutoff = lastDates[op.collection]
            val sameJournalDay = op.collection == "journal" && (habitId to day) in existingJournalDays
            op.id !in server[op.collection].orEmpty() && !sameJournalDay &&
                !outbox.contains(uid, op.collection, op.id) &&
                (op.collection == "habits" || op.collection == "identities" || habitId in newHabits ||
                    cutoff == null || (day != null && day > cutoff))
        }.map { key(it) }.toMutableSet()
        check(prefs.edit().putStringSet(planKey, plan.toSet()).commit())
        val recover = local.filter { key(it) in plan && !outbox.contains(uid, it.collection, it.id) }
        recover.chunked(50).forEach { chunk ->
            if (activeUid != uid) throw CancellationException("account changed")
            val refs = chunk.map { collection(uid, it.collection).document(it.id) }
            db.runTransaction { tx ->
                val existing = refs.map { tx.get(it).exists() }
                chunk.forEachIndexed { i, op ->
                    if (!existing[i] && !outbox.contains(uid, op.collection, op.id)) tx.set(refs[i], cloudFields(op.collection, op.fields!!))
                }
                Unit
            }.await()
            plan.removeAll(chunk.map { key(it) }.toSet())
            check(prefs.edit().putStringSet(planKey, plan.toSet()).commit())
        }
        Log.i("HabitsSync", "recovery completed; candidates=" + recover.size)
        telemetry(uid, "recovery")
    }

    private suspend fun mergeHabitChanges(changes: List<Change>) {
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
                qualityType = doc.getString("qualityType") ?: "USEFUL",
                isTracked = doc.getBoolean("isTracked") ?: true,
                activeDays = (doc.getLong("activeDays") ?: 127L).toInt(),
                timeOfDay = doc.getString("timeOfDay") ?: "MORNING",
                reminderEnabled = doc.getBoolean("reminderEnabled") ?: false,
                reminderHour = (doc.getLong("reminderHour") ?: 9L).toInt(),
                reminderMinute = (doc.getLong("reminderMinute") ?: 0L).toInt(),
                lawObvious = doc.getString("lawObvious") ?: "",
                lawAttractive = doc.getString("lawAttractive") ?: "",
                lawEasy = doc.getString("lawEasy") ?: "",
                lawSatisfying = doc.getString("lawSatisfying") ?: "",
                alternativeSuggestion = doc.getString("alternativeSuggestion") ?: "",
                whyItMatters = doc.getString("whyItMatters") ?: "",
                createdAtEpochDay = doc.getLong("createdAtEpochDay") ?: LocalDate.now().toEpochDay(),
                archived = doc.getBoolean("archived") ?: false,
                sortOrder = (doc.getLong("sortOrder"))?.toInt() ?: existing?.sortOrder ?: 0,
                stackAnchorId = doc.getString("stackAnchorId") ?: "",
                stackAnchorType = doc.getString("stackAnchorType") ?: "",
                stackAnchorLabel = doc.getString("stackAnchorLabel") ?: "",
                identityId = doc.getString("identityId") ?: "",
                identityLabel = doc.getString("identityLabel") ?: "",
                difficultyNote = doc.getString("difficultyNote") ?: "",
                difficultyBumpedAtEpochDay = doc.getLong("difficultyBumpedAtEpochDay") ?: 0L,
                temptationBundle = doc.getString("temptationBundle") ?: "",
                manuallyMastered = doc.getBoolean("manuallyMastered") ?: false,
                selfBindingAction = doc.getString("selfBindingAction") ?: "",
                journalCycleStartEpochDay = doc.getLong("journalCycleStartEpochDay") ?: 0L
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

    private suspend fun mergeLogChanges(changes: List<Change>) {
        val habitDao = database.habitDao()
        val logDao = database.habitLogDao()
        changes.forEach { change ->
            val doc = change.document
            val habitSyncId = doc.getString("habitSyncId") ?: return@forEach
            val dateEpochDay = doc.getLong("dateEpochDay") ?: return@forEach
            val localHabit = habitDao.getBySyncId(habitSyncId) ?: run { deferredLogs[doc.id] = change; return@forEach }

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

    private suspend fun mergeImpulseChanges(changes: List<Change>) {
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

    private suspend fun mergeIdentityChanges(changes: List<Change>) {
        val identityDao = database.identityDao()
        changes.forEach { change ->
            if (change.type == DocumentChange.Type.REMOVED) return@forEach
            val doc = change.document
            val syncId = doc.id
            val statement = doc.getString("statement") ?: return@forEach
            val existing = identityDao.getBySyncId(syncId)
            identityDao.upsert(
                Identity(
                    id = existing?.id ?: 0,
                    syncId = syncId,
                    statement = statement,
                    createdAtEpochDay = doc.getLong("createdAtEpochDay") ?: LocalDate.now().toEpochDay(),
                    archived = doc.getBoolean("archived") ?: false
                )
            )
        }
    }

    private suspend fun mergeJournalChanges(changes: List<Change>) {
        val habitDao = database.habitDao()
        val journalDao = database.habitJournalEntryDao()
        changes.forEach { change ->
            if (change.type == DocumentChange.Type.REMOVED) return@forEach
            val doc = change.document
            val syncId = doc.id
            val habitSyncId = doc.getString("habitSyncId") ?: return@forEach
            val localHabit = habitDao.getBySyncId(habitSyncId) ?: run { deferredJournal[doc.id] = change; return@forEach }
            val existing = journalDao.getBySyncId(syncId) ?: journalDao.getForHabitOnDate(localHabit.id, doc.getLong("dateEpochDay") ?: LocalDate.now().toEpochDay())
            journalDao.upsert(
                HabitJournalEntry(
                    id = existing?.id ?: 0,
                    syncId = syncId,
                    habitId = localHabit.id,
                    habitSyncId = habitSyncId,
                    dateEpochDay = doc.getLong("dateEpochDay") ?: LocalDate.now().toEpochDay(),
                    todaysEvents = doc.getString("todaysEvents") ?: "",
                    hadIncident = doc.getBoolean("hadIncident") ?: false,
                    hadSlip = doc.getBoolean("hadSlip") ?: (doc.getString("amount")?.isNotBlank() ?: false),
                    amount = doc.getString("amount") ?: "",
                    whatIWanted = doc.getString("whatIWanted") ?: "",
                    substituteBehavior = doc.getString("substituteBehavior") ?: "",
                    substituteSucceeded = doc.getBoolean("substituteSucceeded") ?: false,
                    cycleStartEpochDay = doc.getLong("cycleStartEpochDay") ?: 0L
                )
            )
        }
    }

    private suspend fun mergePausePeriodChanges(changes: List<Change>) {
        val periodDao = database.pausePeriodDao()
        changes.forEach { change ->
            val doc = change.document
            if (change.type == DocumentChange.Type.REMOVED) {
                periodDao.getBySyncId(doc.id)?.let { periodDao.delete(it) }
                return@forEach
            }
            val syncId = doc.id
            val existing = periodDao.getBySyncId(syncId)
            periodDao.upsert(
                PausePeriod(
                    id = existing?.id ?: 0,
                    syncId = syncId,
                    startEpochDay = doc.getLong("startEpochDay") ?: 0L,
                    endEpochDay = doc.getLong("endEpochDay") ?: 0L,
                    label = doc.getString("label") ?: "",
                    pausedHabitSyncIds = doc.getString("pausedHabitSyncIds") ?: ""
                )
            )
        }
    }
}