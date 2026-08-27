package com.blacklist.app.domain.repository

import com.blacklist.app.data.local.entity.*
import com.blacklist.app.domain.backup.EncryptedBackupService
import kotlinx.coroutines.flow.Flow
import java.io.InputStream
import java.io.OutputStream

interface BlackListRepository {
    // Blocked numbers
    fun observeBlockedNumbers(): Flow<List<BlockedNumberEntity>>
    suspend fun addBlockedNumber(raw: String, name: String?): Result<Long>
    suspend fun removeBlockedNumber(id: Long)
    suspend fun isBlocked(normalized: String): Boolean
    suspend fun findBlockedMatches(phone: String): BlockedNumberEntity?
    suspend fun setBlockedNotificationEnabled(id: Long, enabled: Boolean)
    suspend fun setAllBlockedNotificationsEnabled(enabled: Boolean)

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
    fun observeScheduleExceptions(scheduleRuleId: Long): Flow<List<ScheduleExceptionEntity>>
    suspend fun addScheduleException(scheduleRuleId: Long, rawNumber: String): Result<Long>
    suspend fun deleteScheduleException(id: Long)

    // Blacklist pattern rules (EXACT/PREFIX/SUFFIX/CONTAINS/RANGE/COUNTRY)
    fun observeBlacklistRules(): Flow<List<BlacklistRuleEntity>>
    suspend fun addBlacklistRule(rule: BlacklistRuleEntity): Result<Long>
    suspend fun deleteBlacklistRule(id: Long)
    suspend fun setBlacklistRuleEnabled(id: Long, enabled: Boolean)

    // Temporary firewall (auto-expiring; stored as internal TEMP_* rules)
    suspend fun enableTemporaryBlockAll(durationMs: Long)
    suspend fun cancelTemporaryBlockAll()
    suspend fun isTemporaryBlockAllActive(): Boolean
    suspend fun addTemporaryAllow(rawNumber: String, durationMs: Long): Result<Long>
    /** Records a short callback allowance only after a definite user-initiated outgoing call. */
    suspend fun recordOutboundCallbackGrace(rawNumber: String): Result<Long>
    suspend fun cleanupExpiredTemporaryRules(): Int

    // User-controlled local backup. The passphrase is never persisted by the app.
    suspend fun exportEncryptedBackup(output: OutputStream, passphrase: CharArray): Result<EncryptedBackupService.ExportResult>
    suspend fun restoreEncryptedBackup(input: InputStream, passphrase: CharArray): Result<EncryptedBackupService.RestoreResult>
}
