package com.blacklist.app.data.repository

import com.blacklist.app.data.local.BlackListDatabase
import com.blacklist.app.data.local.entity.*
import com.blacklist.app.domain.repository.BlackListRepository
import com.blacklist.app.util.PhoneNumberUtils
import kotlinx.coroutines.flow.Flow

class BlackListRepositoryImpl(
    private val db: BlackListDatabase
) : BlackListRepository {

    private val blockedDao get() = db.blockedNumberDao()
    private val whiteDao get() = db.whitelistedNumberDao()
    private val logDao get() = db.blockedCallLogDao()
    private val settingsDao get() = db.appSettingsDao()
    private val scheduleDao get() = db.scheduleRuleDao()

    override fun observeBlockedNumbers(): Flow<List<BlockedNumberEntity>> = blockedDao.observeAll()

    override suspend fun addBlockedNumber(raw: String, name: String?): Result<Long> {
        val normalized = PhoneNumberUtils.normalize(raw) ?: return Result.failure(IllegalArgumentException("Invalid number"))
        if (blockedDao.exists(normalized)) return Result.failure(IllegalStateException("Already exists"))
        val id = blockedDao.insert(BlockedNumberEntity(rawNumber = raw.trim(), normalizedNumber = normalized, displayName = name))
        return Result.success(id)
    }

    override suspend fun removeBlockedNumber(id: Long) = blockedDao.deleteById(id)
    override suspend fun isBlocked(normalized: String): Boolean = blockedDao.exists(normalized)
    override suspend fun findBlockedMatches(phone: String): BlockedNumberEntity? {
        val all = blockedDao.getAll()
        return all.firstOrNull { PhoneNumberUtils.matches(it.rawNumber, phone) || PhoneNumberUtils.matches(it.normalizedNumber, phone) }
    }

    override fun observeWhitelisted(): Flow<List<WhitelistedNumberEntity>> = whiteDao.observeAll()
    override suspend fun addWhitelisted(raw: String, name: String?): Result<Long> {
        val normalized = PhoneNumberUtils.normalize(raw) ?: return Result.failure(IllegalArgumentException("Invalid number"))
        if (whiteDao.exists(normalized)) return Result.failure(IllegalStateException("Already exists"))
        return Result.success(whiteDao.insert(WhitelistedNumberEntity(rawNumber = raw.trim(), normalizedNumber = normalized, displayName = name)))
    }
    override suspend fun removeWhitelisted(id: Long) = whiteDao.deleteById(id)
    override suspend fun isWhitelisted(phone: String): Boolean {
        val all = whiteDao.getAll()
        return all.any { PhoneNumberUtils.matches(it.rawNumber, phone) || PhoneNumberUtils.matches(it.normalizedNumber, phone) }
    }

    override fun observeBlockedLogs(): Flow<List<BlockedCallLogEntity>> = logDao.observeAll()
    override fun observeBlockedCount(): Flow<Int> = logDao.observeCount()
    override suspend fun countBlockedSince(since: Long): Int = logDao.countSince(since)
    override suspend fun logBlocked(phone: String?, reason: String, displayName: String?) {
        logDao.insert(BlockedCallLogEntity(phoneNumber = phone, reason = reason, displayName = displayName))
    }
    override suspend fun clearLogs() = logDao.clearAll()

    override fun observeSettings(): Flow<AppSettingsEntity?> = settingsDao.observe()
    override suspend fun getSettings(): AppSettingsEntity = settingsDao.get() ?: AppSettingsEntity().also { settingsDao.upsert(it) }
    override suspend fun updateSettings(transform: (AppSettingsEntity) -> AppSettingsEntity) {
        val current = getSettings()
        settingsDao.upsert(transform(current).copy(updatedAt = System.currentTimeMillis()))
    }

    override fun observeScheduleRules(): Flow<List<ScheduleRuleEntity>> = scheduleDao.observeAll()
    override suspend fun getEnabledRules(): List<ScheduleRuleEntity> = scheduleDao.getEnabled()
    override suspend fun addScheduleRule(rule: ScheduleRuleEntity): Long = scheduleDao.insert(rule)
    override suspend fun updateScheduleRule(rule: ScheduleRuleEntity) = scheduleDao.update(rule)
    override suspend fun deleteScheduleRule(rule: ScheduleRuleEntity) = scheduleDao.delete(rule)
}
