package com.blacklist.app.data.repository

import com.blacklist.app.data.local.BlackListDatabase
import com.blacklist.app.data.local.entity.*
import com.blacklist.app.domain.backup.EncryptedBackupService
import com.blacklist.app.domain.repository.BlackListRepository
import com.blacklist.app.util.PhoneNumberUtils
import kotlinx.coroutines.flow.Flow
import java.io.InputStream
import java.io.OutputStream

class BlackListRepositoryImpl(
    private val db: BlackListDatabase
) : BlackListRepository {

    private val blockedDao get() = db.blockedNumberDao()
    private val whiteDao get() = db.whitelistedNumberDao()
    private val logDao get() = db.blockedCallLogDao()
    private val settingsDao get() = db.appSettingsDao()
    private val scheduleDao get() = db.scheduleRuleDao()
    private val ruleDao get() = db.blacklistRuleDao()
    private val backupService by lazy { EncryptedBackupService(db) }

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
    override suspend fun setBlockedNotificationEnabled(id: Long, enabled: Boolean) = blockedDao.setNotificationEnabled(id, enabled)
    override suspend fun setAllBlockedNotificationsEnabled(enabled: Boolean) = blockedDao.setAllNotificationsEnabled(enabled)

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

    override fun observeBlacklistRules(): Flow<List<BlacklistRuleEntity>> = ruleDao.observeAll()

    override suspend fun addBlacklistRule(rule: BlacklistRuleEntity): Result<Long> {
        return try {
            // TEMP_* types are internal-only (created via temporary firewall APIs)
            if (rule.ruleType == BlacklistRuleEntity.TYPE_TEMP_BLOCK_ALL ||
                rule.ruleType == BlacklistRuleEntity.TYPE_TEMP_ALLOW ||
                rule.ruleType == BlacklistRuleEntity.TYPE_TEMP_OUTBOUND_CALLBACK
            ) {
                return Result.failure(IllegalArgumentException("Internal rule type"))
            }
            when (rule.ruleType) {
                BlacklistRuleEntity.TYPE_EXACT,
                BlacklistRuleEntity.TYPE_PREFIX,
                BlacklistRuleEntity.TYPE_SUFFIX,
                BlacklistRuleEntity.TYPE_CONTAINS -> {
                    val pattern = rule.pattern?.trim() ?: return Result.failure(IllegalArgumentException("Empty pattern"))
                    if (pattern.isEmpty()) return Result.failure(IllegalArgumentException("Empty pattern"))
                    val minLen = if (rule.ruleType == BlacklistRuleEntity.TYPE_EXACT) 3 else 2
                    val digits = pattern.filter { it.isDigit() }
                    if (rule.ruleType == BlacklistRuleEntity.TYPE_EXACT && digits.length < minLen) {
                        return Result.failure(IllegalArgumentException("Pattern too short"))
                    }
                    if (rule.ruleType != BlacklistRuleEntity.TYPE_EXACT &&
                        rule.pattern!!.any { !it.isDigit() && it != '+' && it != '*' && it != '#' && it != ' ' && it != '-' }) {
                        return Result.failure(IllegalArgumentException("Invalid characters in pattern"))
                    }
                }
                BlacklistRuleEntity.TYPE_RANGE -> {
                    val start = rule.startNumber?.trim()?.filter { it.isDigit() } ?: ""
                    val end = rule.endNumber?.trim()?.filter { it.isDigit() } ?: ""
                    if (start.isEmpty() || end.isEmpty()) return Result.failure(IllegalArgumentException("Range endpoints required"))
                    if (start.length != end.length || start >= end) {
                        return Result.failure(IllegalArgumentException("Invalid range"))
                    }
                }
                BlacklistRuleEntity.TYPE_COUNTRY -> {
                    val iso = rule.countryIso?.trim()?.uppercase() ?: ""
                    if (iso.length != 2 || iso.any { !it.isLetter() }) {
                        return Result.failure(IllegalArgumentException("Invalid country ISO"))
                    }
                }
                else -> return Result.failure(IllegalArgumentException("Unsupported rule type"))
            }

            // Duplicate check: same type + same pattern/start-end/country
            val existing = ruleDao.getAll()
            val dup = existing.any { e ->
                e.ruleType == rule.ruleType && (
                    (e.pattern != null && e.pattern == rule.pattern) ||
                        (e.startNumber == rule.startNumber && e.endNumber == rule.endNumber && rule.startNumber != null) ||
                        (e.countryIso != null && e.countryIso.equals(rule.countryIso, ignoreCase = true))
                    )
            }
            if (dup) return Result.failure(IllegalStateException("Rule already exists"))

            // Normalize stored values
            val toInsert = when (rule.ruleType) {
                BlacklistRuleEntity.TYPE_RANGE -> rule.copy(startNumber = rule.startNumber?.filter { it.isDigit() }, endNumber = rule.endNumber?.filter { it.isDigit() })
                BlacklistRuleEntity.TYPE_COUNTRY -> rule.copy(countryIso = rule.countryIso?.uppercase())
                else -> rule.copy(pattern = rule.pattern?.trim())
            }
            Result.success(ruleDao.insert(toInsert))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteBlacklistRule(id: Long) = ruleDao.deleteById(id)
    override suspend fun setBlacklistRuleEnabled(id: Long, enabled: Boolean) = ruleDao.setEnabled(id, enabled)

    override suspend fun enableTemporaryBlockAll(durationMs: Long) {
        cancelTemporaryBlockAll()
        val expiry = System.currentTimeMillis() + durationMs.coerceAtLeast(60_000)
        ruleDao.insert(
            BlacklistRuleEntity(
                ruleType = BlacklistRuleEntity.TYPE_TEMP_BLOCK_ALL,
                pattern = expiry.toString(),
                priority = 10,
                displayName = "temporary_firewall"
            )
        )
    }

    override suspend fun cancelTemporaryBlockAll() {
        ruleDao.getAll().filter { it.ruleType == BlacklistRuleEntity.TYPE_TEMP_BLOCK_ALL }.forEach { ruleDao.deleteById(it.id) }
    }

    override suspend fun isTemporaryBlockAllActive(): Boolean {
        cleanupExpiredTemporaryRules()
        return ruleDao.getAll().any {
            it.ruleType == BlacklistRuleEntity.TYPE_TEMP_BLOCK_ALL &&
                com.blacklist.app.domain.engine.TemporaryFirewall.isActive(it)
        }
    }

    override suspend fun addTemporaryAllow(rawNumber: String, durationMs: Long): Result<Long> {
        return try {
            val digits = rawNumber.filter { it.isDigit() }
            if (digits.length < 3) return Result.failure(IllegalArgumentException("Invalid number"))
            // Replace any previous temp-allow for the same number
            ruleDao.getAll()
                .filter { it.ruleType == BlacklistRuleEntity.TYPE_TEMP_ALLOW && it.pattern?.filter { c -> c.isDigit() } == digits }
                .forEach { ruleDao.deleteById(it.id) }
            val expiry = System.currentTimeMillis() + durationMs.coerceAtLeast(60_000)
            Result.success(
                ruleDao.insert(
                    BlacklistRuleEntity(
                        ruleType = BlacklistRuleEntity.TYPE_TEMP_ALLOW,
                        pattern = digits,
                        startNumber = expiry.toString(),
                        priority = 15,
                        displayName = "temporary_allow"
                    )
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun recordOutboundCallbackGrace(rawNumber: String): Result<Long> {
        return try {
            val digits = rawNumber.filter(Char::isDigit)
            if (!com.blacklist.app.domain.engine.OutboundCallbackGrace.isValidDigits(digits)) {
                return Result.failure(IllegalArgumentException("Invalid number"))
            }
            val now = System.currentTimeMillis()
            val current = ruleDao.getAll()
            current.filter {
                it.ruleType == BlacklistRuleEntity.TYPE_TEMP_OUTBOUND_CALLBACK &&
                    (!com.blacklist.app.domain.engine.TemporaryFirewall.isActive(it, now) || it.pattern == digits)
            }.forEach { ruleDao.deleteById(it.id) }
            val active = current.filter {
                it.ruleType == BlacklistRuleEntity.TYPE_TEMP_OUTBOUND_CALLBACK &&
                    it.pattern != digits && com.blacklist.app.domain.engine.TemporaryFirewall.isActive(it, now)
            }
            active.sortedBy { it.createdAt }
                .take((active.size - com.blacklist.app.domain.engine.OutboundCallbackGrace.MAX_ENTRIES + 1).coerceAtLeast(0))
                .forEach { ruleDao.deleteById(it.id) }
            val expiry = now + com.blacklist.app.domain.engine.OutboundCallbackGrace.DURATION_MS
            Result.success(
                ruleDao.insert(
                    BlacklistRuleEntity(
                        ruleType = BlacklistRuleEntity.TYPE_TEMP_OUTBOUND_CALLBACK,
                        pattern = digits,
                        startNumber = expiry.toString(),
                        priority = 20,
                        displayName = "outbound_callback_grace"
                    )
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun cleanupExpiredTemporaryRules(): Int {
        val now = System.currentTimeMillis()
        val expired = ruleDao.getAll().filter {
            (it.ruleType == BlacklistRuleEntity.TYPE_TEMP_BLOCK_ALL ||
                it.ruleType == BlacklistRuleEntity.TYPE_TEMP_ALLOW ||
                it.ruleType == BlacklistRuleEntity.TYPE_TEMP_OUTBOUND_CALLBACK) &&
                !com.blacklist.app.domain.engine.TemporaryFirewall.isActive(it, now)
        }
        expired.forEach { ruleDao.deleteById(it.id) }
        return expired.size
    }

    override suspend fun exportEncryptedBackup(
        output: OutputStream,
        passphrase: CharArray
    ): Result<EncryptedBackupService.ExportResult> = backupService.exportTo(output, passphrase)

    override suspend fun restoreEncryptedBackup(
        input: InputStream,
        passphrase: CharArray
    ): Result<EncryptedBackupService.RestoreResult> = backupService.restoreFrom(input, passphrase)
}
