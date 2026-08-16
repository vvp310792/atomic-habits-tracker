package com.atomichabits.tracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

@Database(
    entities = [Habit::class, HabitLog::class, ImpulseLog::class, Identity::class],
    version = 15,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun habitDao(): HabitDao
    abstract fun habitLogDao(): HabitLogDao
    abstract fun impulseLogDao(): ImpulseLogDao
    abstract fun identityDao(): IdentityDao

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

        /** v6 -> v7: adds optional link from an impulse log to a specific harmful anchor habit. */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE impulse_logs ADD COLUMN linkedHarmfulAnchorId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE impulse_logs ADD COLUMN linkedHarmfulAnchorLabel TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v7 -> v8: adds AnchorHabit.alternativeSuggestion ("do this instead" for harmful habits). */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE anchor_habits ADD COLUMN alternativeSuggestion TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v8 -> v9: adds identities table + Habit.identityId/identityLabel (identity-based habits). */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS identities (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        syncId TEXT NOT NULL DEFAULT '',
                        statement TEXT NOT NULL,
                        createdAtEpochDay INTEGER NOT NULL DEFAULT 0,
                        archived INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_identities_syncId ON identities(syncId)"
                )
                db.execSQL("ALTER TABLE habits ADD COLUMN identityId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE habits ADD COLUMN identityLabel TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v9 -> v10: adds AnchorHabit.whyItMatters (McGonigal's "I want" power - deeper motivation). */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE anchor_habits ADD COLUMN whyItMatters TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v10 -> v11: adds AnchorHabit.timeOfDay (time-of-day sub-grouping for Полезные привычки). */
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE anchor_habits ADD COLUMN timeOfDay TEXT NOT NULL DEFAULT 'ALL_DAY'")
            }
        }

        /**
         * v11 -> v12: unifies Habit and AnchorHabit into one universal entity.
         * Adds qualityType/isTracked/alternativeSuggestion/whyItMatters to habits,
         * then copies every existing anchor_habits row across as an untracked
         * (isTracked=0) habit carrying its old type as qualityType. The old
         * anchor_habits table is deliberately NOT dropped - it's just an orphaned,
         * unused table from here on - so a bug in this one-shot copy can't destroy
         * data with no way back.
         */
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE habits ADD COLUMN qualityType TEXT NOT NULL DEFAULT 'USEFUL'")
                db.execSQL("ALTER TABLE habits ADD COLUMN isTracked INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE habits ADD COLUMN alternativeSuggestion TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE habits ADD COLUMN whyItMatters TEXT NOT NULL DEFAULT ''")

                db.execSQL(
                    """
                    INSERT INTO habits (
                        syncId, name, emoji, colorHex, category, qualityType, isTracked,
                        activeDays, timeOfDay, reminderEnabled, reminderHour, reminderMinute,
                        lawObvious, lawAttractive, lawEasy, lawSatisfying,
                        alternativeSuggestion, whyItMatters,
                        createdAtEpochDay, archived, sortOrder,
                        stackAnchorId, stackAnchorType, stackAnchorLabel,
                        identityId, identityLabel
                    )
                    SELECT
                        syncId, name, '⭐', '#7C6CF0', 'SELF_DEVELOPMENT', type, 0,
                        127, timeOfDay, 0, 9, 0,
                        '', '', '', '',
                        alternativeSuggestion, whyItMatters,
                        createdAtEpochDay, archived, 0,
                        '', '', '',
                        '', ''
                    FROM anchor_habits
                    """.trimIndent()
                )
            }
        }

        /**
         * v12 -> v13: every row copied over by MIGRATION_11_12 (and, before that,
         * every habit that was never manually reordered) shares sortOrder=0 - a
         * degenerate mass collision that confused the drag-reorder math. Give each
         * such row a distinct value based on its stable id, without touching any
         * row that already has a genuinely different (deliberately reordered)
         * sortOrder.
         */
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE habits SET sortOrder = id WHERE sortOrder = 0")
            }
        }

        /**
         * v13 -> v14: adds the Goldilocks Rule (difficultyNote + when it was last
         * bumped, so computeMastery can restart the automaticity clock for a
         * harder version of the same habit) and temptation bundling
         * (temptationBundle) as its own field, split out from the lawAttractive
         * free-text prose it used to be folded into.
         */
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE habits ADD COLUMN difficultyNote TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE habits ADD COLUMN difficultyBumpedAtEpochDay INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE habits ADD COLUMN temptationBundle TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * v14 -> v15: adds manuallyMastered (self-declared "already automatic"
         * status, independent of isTracked/computed mastery - see Habit.kt).
         */
        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE habits ADD COLUMN manuallyMastered INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "atomic_habits.db"
                )
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
                        MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12,
                        MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15
                    )
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
