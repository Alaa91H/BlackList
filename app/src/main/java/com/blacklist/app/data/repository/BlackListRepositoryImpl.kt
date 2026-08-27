package com.blacklist.app.data.repository

import androidx.room.withTransaction
import com.blacklist.app.data.local.BlackListDatabase
import com.blacklist.app.data.local.entity.*
import com.blacklist.app.domain.backup.EncryptedBackupService
import com.blacklist.app.domain.importexport.OfflineReputationImportPreview
import com.blacklist.app.domain.repository.BlackListRepository
import com.blacklist.app.util.PhoneNumberUtils
import kotlinx.coroutines.flow.Flow
import java.io.InputStream
import java.io.OutputStream
import java.net.URI

class BlackListRepositoryImpl(
    private val db: BlackListDatabase
) : BlackListRepository {

    private val blockedDao get() = db.blockedNumberDao()
    private val whiteDao get() = db.whitelistedNumberDao()
    private val logDao get() = db.blockedCallLogDao()
    private val settingsDao get() = db.appSettingsDao()
    private val scheduleDao get() = db.scheduleRuleDao()
    private val scheduleExceptionDao get() = db.scheduleExceptionDao()
    private val ruleDao get() = db.blacklistRuleDao()
    private val offlineReputationDao get() = db.offlineReputationDao()
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
    override fun observeScheduleExceptions(scheduleRuleId: Long): Flow<List<ScheduleExceptionEntity>> =
        scheduleExceptionDao.observeForSchedule(scheduleRuleId)

    override suspend fun addScheduleException(scheduleRuleId: Long, rawNumber: String): Result<Long> {
        return try {
            if (scheduleRuleId <= 0) return Result.failure(IllegalArgumentException("Invalid schedule rule"))
            val normalized = PhoneNumberUtils.normalize(rawNumber) ?: return Result.failure(IllegalArgumentException("Invalid number"))
            val digits = normalized.filter(Char::isDigit)
            if (digits.length !in 3..32) return Result.failure(IllegalArgumentException("Invalid number"))
            if (scheduleExceptionDao.countForSchedule(scheduleRuleId) >= MAX_SCHEDULE_EXCEPTIONS_PER_RULE) {
                return Result.failure(IllegalStateException("Schedule exception limit reached"))
            }
            val id = scheduleExceptionDao.insert(
                ScheduleExceptionEntity(scheduleRuleId = scheduleRuleId, normalizedNumber = normalized)
            )
            if (id == -1L) Result.failure(IllegalStateException("Schedule exception already exists")) else Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteScheduleException(id: Long) = scheduleExceptionDao.deleteById(id)

    override fun observeOfflineReputationSources(): Flow<List<OfflineReputationSourceEntity>> =
        offlineReputationDao.observeSources()

    override suspend fun importOfflineReputationList(preview: OfflineReputationImportPreview): Result<Long> = runCatching {
        validateOfflineReputationPreview(preview)
        db.withTransaction {
            require(offlineReputationDao.countSources() < MAX_REPUTATION_SOURCES) {
                "Offline reputation source limit reached. Remove a source before importing another list."
            }
            require(!offlineReputationDao.hasFingerprint(preview.fingerprintSha256)) {
                "This reputation list has already been imported."
            }
            val normalizedRows = preview.rows.groupBy { it.rawNumber }.map { (number, rows) ->
                val highest = rows.maxBy { it.riskScore }
                OfflineReputationEntryEntity(
                    sourceId = 0,
                    normalizedNumber = number,
                    riskScore = highest.riskScore,
                    category = highest.category?.trim()?.takeIf { it.isNotEmpty() }
                )
            }
            require(normalizedRows.isNotEmpty()) { "Reputation list contains no importable entries." }
            require(normalizedRows.size <= MAX_REPUTATION_ENTRIES_PER_SOURCE) { "Reputation list exceeds the per-source limit." }
            require(offlineReputationDao.countEntries() + normalizedRows.size <= MAX_REPUTATION_ENTRIES_TOTAL) {
                "Offline reputation entry limit reached. Remove a source before importing another list."
            }
            val sourceId = offlineReputationDao.insertSource(
                OfflineReputationSourceEntity(
                    sourceName = preview.sourceName.trim(),
                    sourceVersion = preview.sourceVersion?.trim(),
                    sourceUrl = preview.sourceUrl?.trim(),
                    fingerprintSha256 = preview.fingerprintSha256,
                    entryCount = normalizedRows.size
                )
            )
            offlineReputationDao.insertEntries(normalizedRows.map { it.copy(sourceId = sourceId) })
            sourceId
        }
    }

    private fun validateOfflineReputationPreview(preview: OfflineReputationImportPreview) {
        require(preview.rows.isNotEmpty() && preview.rows.size <= MAX_REPUTATION_ENTRIES_PER_SOURCE) {
            "Reputation list has an invalid number of valid entries."
        }
        require(preview.sourceRows in 1..MAX_REPUTATION_SOURCE_ROWS && preview.invalidRows >= 0 && preview.duplicateRows >= 0) {
            "Reputation list has invalid row statistics."
        }
        require(preview.rows.size + preview.invalidRows + preview.duplicateRows == preview.sourceRows) {
            "Reputation list row statistics do not match its contents."
        }
        require(preview.highRiskRows == preview.rows.count { it.riskScore >= HIGH_RISK_SCORE }) {
            "Reputation list high-risk statistic does not match its contents."
        }
        require(isSafeText(preview.sourceName, MAX_REPUTATION_SOURCE_LENGTH)) { "Invalid reputation source name." }
        require(preview.sourceVersion == null || isSafeText(preview.sourceVersion, MAX_REPUTATION_VERSION_LENGTH)) {
            "Invalid reputation source version."
        }
        require(preview.sourceUrl == null || isSafeDisplayUrl(preview.sourceUrl)) { "Invalid reputation source URL." }
        require(preview.fingerprintSha256.matches(REPUTATION_FINGERPRINT)) { "Invalid reputation list fingerprint." }
        preview.rows.forEach { row ->
            require(REPUTATION_E164.matches(row.rawNumber)) { "Offline reputation numbers must be E.164 values." }
            require(row.riskScore in 0..100) { "Invalid offline reputation score." }
            require(row.category == null || isSafeText(row.category, MAX_REPUTATION_CATEGORY_LENGTH, allowEmpty = true)) {
                "Invalid offline reputation category."
            }
        }
    }

    private fun isSafeText(value: String, maxLength: Int, allowEmpty: Boolean = false): Boolean =
        value.length.let { if (allowEmpty) it <= maxLength else it in 1..maxLength } &&
            value.all { !it.isISOControl() && it != '\u007f' }

    private fun isSafeDisplayUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        value.length <= MAX_REPUTATION_URL_LENGTH && uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null
    }.getOrDefault(false)

    override suspend fun deleteOfflineReputationSource(id: Long) {
        if (id > 0) offlineReputationDao.deleteSource(id)
    }

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

    private companion object {
        const val MAX_SCHEDULE_EXCEPTIONS_PER_RULE = 50
        const val MAX_REPUTATION_SOURCES = 10
        const val MAX_REPUTATION_SOURCE_ROWS = 10_000
        const val MAX_REPUTATION_ENTRIES_PER_SOURCE = 5_000
        const val MAX_REPUTATION_ENTRIES_TOTAL = 10_000
        const val HIGH_RISK_SCORE = 80
        val REPUTATION_E164 = Regex("^\\+[1-9][0-9]{6,14}$")
        val REPUTATION_FINGERPRINT = Regex("^[a-f0-9]{64}$")
        const val MAX_REPUTATION_SOURCE_LENGTH = 100
        const val MAX_REPUTATION_VERSION_LENGTH = 64
        const val MAX_REPUTATION_URL_LENGTH = 512
        const val MAX_REPUTATION_CATEGORY_LENGTH = 80
    }
}
