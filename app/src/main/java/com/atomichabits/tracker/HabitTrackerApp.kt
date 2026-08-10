package com.atomichabits.tracker

import android.app.Application
import com.atomichabits.tracker.data.AppDatabase
import com.atomichabits.tracker.data.HabitRepository
import com.atomichabits.tracker.data.SettingsStore
import com.atomichabits.tracker.notifications.NotificationHelper
import com.atomichabits.tracker.sheets.SheetsSyncWorker

class HabitTrackerApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val repository: HabitRepository by lazy {
        HabitRepository(database.habitDao(), database.habitLogDao())
    }
    val settingsStore: SettingsStore by lazy { SettingsStore(this) }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannel(this)
        SheetsSyncWorker.enablePeriodicSync(this)
    }
}
