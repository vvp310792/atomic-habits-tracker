package com.atomichabits.tracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

@Database(
    entities = [Habit::class, HabitLog::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun habitDao(): HabitDao
    abstract fun habitLogDao(): HabitLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * v1 -> v2: adds Habit.syncId (a stable UUID used for Google Sheets sync,
         * since the local Room [Habit.id] is not stable across a reinstall).
         * Existing rows get a freshly generated UUID backfilled in Kotlin (SQLite
         * has no built-in UUID function), and the unique index is created only
         * *after* backfilling so it doesn't reject the intermediate all-blank state.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE habits ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")

                val cursor = db.query("SELECT id FROM habits")
                cursor.use {
                    while (it.moveToNext()) {
                        val rowId = it.getLong(0)
                        db.execSQL(
                            "UPDATE habits SET syncId = ? WHERE id = ?",
                            arrayOf(UUID.randomUUID().toString(), rowId)
                        )
                    }
                }

                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_habits_syncId ON habits(syncId)"
                )
            }
        }

        /** v2 -> v3: adds Habit.timeOfDay ("MORNING"/"DAY"/"EVENING"), used to group the Home screen. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE habits ADD COLUMN timeOfDay TEXT NOT NULL DEFAULT 'MORNING'")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "atomic_habits.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
