package com.blacklist.app.domain.repository

import com.blacklist.app.data.local.entity.*
import kotlinx.coroutines.flow.Flow

interface BlackListRepository {
    // Blocked numbers
    fun observeBlockedNumbers(): Flow<List<BlockedNumberEntity>>
    suspend fun addBlockedNumber(raw: String, name: String?): Result<Long>
    suspend fun removeBlockedNumber(id: Long)
    suspend fun isBlocked(normalized: String): Boolean
    suspend fun findBlockedMatches(phone: String): BlockedNumberEntity?

    // Whitelist
    fun observeWhitelisted(): Flow<List<WhitelistedNumberEntity>>
    suspend fun addWhitelisted(raw: String, name: String?): Result<Long>
    suspend fun removeWhitelisted(id: Long)
    suspend fun isWhitelisted(phone: String): Boolean

    // Logs
    fun observeBlockedLogs(): Flow<List<BlockedCallLogEntity>>
    fun observeBlockedCount(): Flow<Int>
    suspend fun countBlockedSince(since: Long): Int
    suspend fun logBlocked(phone: String?, reason: String, displayName: String? = null)
    suspend fun clearLogs()

    // Settings
    fun observeSettings(): Flow<AppSettingsEntity?>
    suspend fun getSettings(): AppSettingsEntity
    suspend fun updateSettings(transform: (AppSettingsEntity) -> AppSettingsEntity)

    // Schedule
    fun observeScheduleRules(): Flow<List<ScheduleRuleEntity>>
    suspend fun getEnabledRules(): List<ScheduleRuleEntity>
    suspend fun addScheduleRule(rule: ScheduleRuleEntity): Long
    suspend fun updateScheduleRule(rule: ScheduleRuleEntity)
    suspend fun deleteScheduleRule(rule: ScheduleRuleEntity)
}
