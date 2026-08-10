package com.atomichabits.tracker.sheets

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.atomichabits.tracker.HabitTrackerApp
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class SheetsSyncWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as HabitTrackerApp
        val url = app.settingsStore.sheetsUrl.first()
        if (url.isBlank()) return Result.success() // nothing configured yet

        val logDao = app.database.habitLogDao()
        val habitDao = app.database.habitDao()

        val unsynced = logDao.getUnsyncedLogs()
        if (unsynced.isEmpty()) return Result.success()

        val rows = unsynced.mapNotNull { log ->
            habitDao.getHabit(log.habitId)?.let { habit -> habit to log }
        }

        val exporter = SheetsExporter(url)
        val ok = exporter.export(rows)

        return if (ok) {
            logDao.markSynced(unsynced.map { it.id })
            Result.success()
        } else {
            Result.retry()
        }
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "sheets_periodic_sync"
        private const val ONE_TIME_WORK_NAME = "sheets_manual_sync"

        fun enablePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<SheetsSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        /** Triggered by the "Export now" button in Settings, or right after marking a habit done. */
        fun syncNow(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<SheetsSyncWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
