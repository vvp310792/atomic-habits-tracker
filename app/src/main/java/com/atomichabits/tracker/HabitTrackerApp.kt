package com.atomichabits.tracker

import android.app.Application
import com.atomichabits.tracker.data.AppDatabase
import com.atomichabits.tracker.data.HabitRepository
import com.atomichabits.tracker.notifications.NotificationHelper
import com.atomichabits.tracker.sync.AuthManager
import com.atomichabits.tracker.sync.FirestoreSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HabitTrackerApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    val authManager: AuthManager by lazy { AuthManager(BuildConfig.GOOGLE_WEB_CLIENT_ID) }

    val syncManager: FirestoreSyncManager by lazy { FirestoreSyncManager(database, this) }

    val repository: HabitRepository by lazy {
        HabitRepository(
            database.habitDao(),
            database.habitLogDao(),
            syncManager = syncManager,
            currentUid = { authManager.currentUser?.uid }
        )
    }

    private val appScope = CoroutineScope(Dispatchers.IO)

    /** True once we've done the one-time "push everything local" for the current sign-in. */
    private var didInitialPush = false

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannel(this)

        appScope.launch {
            authManager.authStateFlow().collect { user ->
                if (user != null) {
                    syncManager.start(user.uid)
                    if (!didInitialPush) {
                        didInitialPush = true
                        // Pushes any habits/logs that existed locally before this
                        // sign-in (e.g. the user tried the app before signing in).
                        // Harmless/idempotent if there's nothing local yet.
                        syncManager.pushAll(user.uid)
                    }
                } else {
                    syncManager.stop()
                    didInitialPush = false
                }
            }
        }
    }
}
