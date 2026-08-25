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
        AppSettingsEntity::class,
        BlacklistRuleEntity::class,
        CallerReputationEntity::class,
        SecurityEventEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class BlackListDatabase : RoomDatabase() {
    abstract fun blockedNumberDao(): BlockedNumberDao
    abstract fun whitelistedNumberDao(): WhitelistedNumberDao
    abstract fun blockedCallLogDao(): BlockedCallLogDao
    abstract fun scheduleRuleDao(): ScheduleRuleDao
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun blacklistRuleDao(): BlacklistRuleDao
    abstract fun callerReputationDao(): CallerReputationDao
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
    }
}
