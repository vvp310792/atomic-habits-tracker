package com.atomichabits.tracker

import android.app.Application
import com.atomichabits.tracker.data.AppDatabase
import com.atomichabits.tracker.data.HabitJournalRepository
import com.atomichabits.tracker.data.HabitRepository
import com.atomichabits.tracker.data.IdentityRepository
import com.atomichabits.tracker.data.ImpulseRepository
import com.atomichabits.tracker.data.PausePeriodRepository
import com.atomichabits.tracker.notifications.NotificationHelper
import com.atomichabits.tracker.sync.AuthManager
import com.atomichabits.tracker.sync.FirestoreSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class HabitTrackerApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    val authManager: AuthManager by lazy { AuthManager(BuildConfig.GOOGLE_WEB_CLIENT_ID) }

    val syncManager: FirestoreSyncManager by lazy { FirestoreSyncManager(database, this) }

    val repository: HabitRepository by lazy {
        HabitRepository(
            database.habitDao(),
            database.habitLogDao(),
            pausePeriodDao = database.pausePeriodDao(),
            syncManager = syncManager,
            currentUid = { authManager.currentUser?.uid ?: syncManager.localOwnerUid }
        )
    }

    val pausePeriodRepository: PausePeriodRepository by lazy {
        PausePeriodRepository(
            database.pausePeriodDao(),
            syncManager = syncManager,
            currentUid = { authManager.currentUser?.uid ?: syncManager.localOwnerUid }
        )
    }

    val impulseRepository: ImpulseRepository by lazy {
        ImpulseRepository(
            database.impulseLogDao(),
            syncManager = syncManager,
            currentUid = { authManager.currentUser?.uid ?: syncManager.localOwnerUid }
        )
    }

    val journalRepository: HabitJournalRepository by lazy {
        HabitJournalRepository(
            database.habitJournalEntryDao(),
            syncManager = syncManager,
            currentUid = { authManager.currentUser?.uid ?: syncManager.localOwnerUid }
        )
    }

    val identityRepository: IdentityRepository by lazy {
        IdentityRepository(
            database.identityDao(),
            syncManager = syncManager,
            currentUid = { authManager.currentUser?.uid ?: syncManager.localOwnerUid }
        )
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Launches persistence work (saves, reorders, moves) on an application-scoped
     * coroutine rather than a screen's rememberCoroutineScope(). Using a screen-scoped
     * coroutine for a DB write is a trap: navigating away cancels that scope, and if the
     * user switches screens right after a drag-to-reorder, an in-flight multi-row
     * sortOrder update (plus its Firestore push) can be cut off mid-write - the change
     * looks applied for a moment, then reverts because it was never actually persisted.
     * This outlives any single screen, so a launched write always finishes.
     */
    fun launchPersistent(block: suspend () -> Unit) {
        appScope.launch { block() }
    }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannel(this)
        // Preserve only a technical crash class and time for delivery after restart.
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                getSharedPreferences("sync_state_v2", MODE_PRIVATE).edit()
                    .putString("crash_type", error.javaClass.simpleName)
                    .putLong("crash_time", System.currentTimeMillis()).commit()
            }
            previousHandler?.uncaughtException(thread, error)
                ?: android.os.Process.killProcess(android.os.Process.myPid())
        }

        appScope.launch {
            authManager.authStateFlow().map { it?.uid }.distinctUntilChanged().collect { uid ->
                if (uid != null) syncManager.start(uid) else syncManager.stop()
            }
        }
        appScope.launch {
            while (isActive) {
                delay(30_000)
                syncManager.retryPending()
            }
        }
    }
}
