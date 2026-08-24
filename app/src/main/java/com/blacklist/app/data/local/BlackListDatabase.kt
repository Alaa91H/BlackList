package com.blacklist.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
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
    version = 3,
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
}
