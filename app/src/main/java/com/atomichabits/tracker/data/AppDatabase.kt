package com.atomichabits.tracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

@Database(
    entities = [Habit::class, HabitLog::class, AnchorHabit::class, ImpulseLog::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun habitDao(): HabitDao
    abstract fun habitLogDao(): HabitLogDao
    abstract fun anchorHabitDao(): AnchorHabitDao
    abstract fun impulseLogDao(): ImpulseLogDao

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
                            arrayOf<Any>(UUID.randomUUID().toString(), rowId)
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

        /** v3 -> v4: adds Habit.category (one of 7 fixed life-area tags, see util/Categories.kt). */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE habits ADD COLUMN category TEXT NOT NULL DEFAULT 'SELF_DEVELOPMENT'")
            }
        }

        /**
         * v4 -> v5: adds habit stacking. New anchor_habits table (the library of
         * already-established routines), plus three new columns on habits so a
         * habit can be chained after an anchor or after another tracked habit.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS anchor_habits (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        syncId TEXT NOT NULL DEFAULT '',
                        name TEXT NOT NULL,
                        type TEXT NOT NULL DEFAULT 'USEFUL',
                        createdAtEpochDay INTEGER NOT NULL DEFAULT 0,
                        archived INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_anchor_habits_syncId ON anchor_habits(syncId)"
                )
                db.execSQL("ALTER TABLE habits ADD COLUMN stackAnchorId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE habits ADD COLUMN stackAnchorType TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE habits ADD COLUMN stackAnchorLabel TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v5 -> v6: adds impulse_logs (the "Позыв" checkmark/cross urge-intercept log). */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS impulse_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        syncId TEXT NOT NULL DEFAULT '',
                        dateEpochDay INTEGER NOT NULL DEFAULT 0,
                        timestampMillis INTEGER NOT NULL DEFAULT 0,
                        outcome TEXT NOT NULL DEFAULT 'CHECK',
                        triggerTags TEXT NOT NULL DEFAULT '',
                        note TEXT NOT NULL DEFAULT ''
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_impulse_logs_syncId ON impulse_logs(syncId)"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "atomic_habits.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
