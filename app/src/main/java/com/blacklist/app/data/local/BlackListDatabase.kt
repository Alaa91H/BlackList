package com.blacklist.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.blacklist.app.data.local.dao.*
import com.blacklist.app.data.local.entity.*

@Database(
    entities = [
        BlockedNumberEntity::class,
        WhitelistedNumberEntity::class,
        BlockedCallLogEntity::class,
        ScheduleRuleEntity::class,
        ScheduleExceptionEntity::class,
        AppSettingsEntity::class,
        BlacklistRuleEntity::class,
        CallerReputationEntity::class,
        OfflineReputationSourceEntity::class,
        OfflineReputationEntryEntity::class,
        SecurityEventEntity::class
    ],
    version = 12,
    exportSchema = true
)
abstract class BlackListDatabase : RoomDatabase() {
    abstract fun blockedNumberDao(): BlockedNumberDao
    abstract fun whitelistedNumberDao(): WhitelistedNumberDao
    abstract fun blockedCallLogDao(): BlockedCallLogDao
    abstract fun scheduleRuleDao(): ScheduleRuleDao
    abstract fun scheduleExceptionDao(): ScheduleExceptionDao
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun blacklistRuleDao(): BlacklistRuleDao
    abstract fun callerReputationDao(): CallerReputationDao
    abstract fun offlineReputationDao(): OfflineReputationDao
    abstract fun securityEventDao(): SecurityEventDao

    companion object {
        /** Preserves the existing singleton settings row while recording the selected local profile. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE app_settings ADD COLUMN activeProfileId TEXT NOT NULL DEFAULT 'custom'"
                )
            }
        }

        /** Preserves all local policy data while adding the emergency callback grace expiry. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE app_settings ADD COLUMN emergencyCallbackGraceUntil INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /** Keeps existing policy data while adding the optional private system-log preference. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE app_settings ADD COLUMN hideBlockedCallsFromSystemLog INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /** Preserves existing settings while enabling the bounded local callback recovery safeguard. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE app_settings ADD COLUMN allowOutboundCallbackGrace INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /** Adds local, exact-number allow exceptions scoped to individual schedule rules. */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS schedule_exceptions (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "scheduleRuleId INTEGER NOT NULL, " +
                        "normalizedNumber TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL, " +
                        "FOREIGN KEY(scheduleRuleId) REFERENCES schedule_rules(id) ON DELETE CASCADE)"
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_schedule_exceptions_scheduleRuleId ON schedule_exceptions(scheduleRuleId)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_schedule_exceptions_scheduleRuleId_normalizedNumber ON schedule_exceptions(scheduleRuleId, normalizedNumber)")
            }
        }

        /** Adds auditable, user-imported local reputation sources and their exact-number entries. */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS offline_reputation_sources (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "sourceName TEXT NOT NULL, " +
                        "sourceVersion TEXT, " +
                        "sourceUrl TEXT, " +
                        "fingerprintSha256 TEXT NOT NULL, " +
                        "entryCount INTEGER NOT NULL, " +
                        "importedAt INTEGER NOT NULL)"
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_offline_reputation_sources_importedAt ON offline_reputation_sources(importedAt)")
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS offline_reputation_entries (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "sourceId INTEGER NOT NULL, " +
                        "normalizedNumber TEXT NOT NULL, " +
                        "riskScore INTEGER NOT NULL, " +
                        "category TEXT, " +
                        "createdAt INTEGER NOT NULL, " +
                        "FOREIGN KEY(sourceId) REFERENCES offline_reputation_sources(id) ON DELETE CASCADE)"
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_offline_reputation_entries_sourceId ON offline_reputation_entries(sourceId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_offline_reputation_entries_normalizedNumber ON offline_reputation_entries(normalizedNumber)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_offline_reputation_entries_sourceId_normalizedNumber ON offline_reputation_entries(sourceId, normalizedNumber)")
            }
        }

        /** Adds opt-in quiet screening preferences without changing existing reject defaults. */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE app_settings ADD COLUMN silenceUnknown INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE app_settings ADD COLUMN silencePrivate INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Preserves existing local activity while adding user-controlled in-app history retention. */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE app_settings ADD COLUMN blockedLogRetentionDays INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Keeps all existing blacklist rules on their historical reject behavior. */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE blacklist_rules ADD COLUMN enforcement TEXT NOT NULL DEFAULT 'BLOCK'"
                )
            }
        }
    }
}
