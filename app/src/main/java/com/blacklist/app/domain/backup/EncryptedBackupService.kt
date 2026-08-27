package com.blacklist.app.domain.backup

import androidx.room.withTransaction
import com.blacklist.app.data.local.BlackListDatabase
import com.blacklist.app.data.local.entity.AppSettingsEntity
import com.blacklist.app.data.local.entity.BlacklistRuleEntity
import com.blacklist.app.data.local.entity.BlockedNumberEntity
import com.blacklist.app.data.local.entity.OfflineReputationEntryEntity
import com.blacklist.app.data.local.entity.OfflineReputationSourceEntity
import com.blacklist.app.data.local.entity.ScheduleExceptionEntity
import com.blacklist.app.data.local.entity.ScheduleRuleEntity
import com.blacklist.app.util.PhoneNumberUtils
import com.blacklist.app.data.local.entity.WhitelistedNumberEntity
import com.blacklist.app.domain.engine.EmergencyCallbackGrace
import com.blacklist.app.domain.model.ProtectionProfiles
import com.blacklist.app.domain.retention.BlockedCallLogRetentionPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

/**
 * User-controlled portable backup for policy data only.
 *
 * Blocked-call logs, reputation history and device diagnostics are deliberately
 * excluded because they can reveal sensitive call activity. The envelope uses
 * PBKDF2-HMAC-SHA256 plus AES-256-GCM; no passphrase is retained by the app.
 */
class EncryptedBackupService(
    private val database: BlackListDatabase
) {
    data class ExportResult(
        val createdAt: Long,
        val rules: Int,
        val blockedNumbers: Int,
        val whitelistedNumbers: Int,
        val schedules: Int
    )

    data class RestoreResult(
        val rules: Int,
        val blockedNumbers: Int,
        val whitelistedNumbers: Int,
        val schedules: Int
    )

    suspend fun exportTo(output: OutputStream, passphrase: CharArray): Result<ExportResult> = runCatching {
        require(passphrase.size >= MIN_PASSPHRASE_LENGTH) { "Backup passphrase must contain at least $MIN_PASSPHRASE_LENGTH characters." }
        withContext(Dispatchers.IO) {
            val rules = database.blacklistRuleDao().getAll()
            val blocked = database.blockedNumberDao().getAll()
            val whitelisted = database.whitelistedNumberDao().getAll()
            val schedules = database.scheduleRuleDao().getAll()
            val scheduleExceptions = database.scheduleExceptionDao().getAll()
            val offlineReputationSources = database.offlineReputationDao().getSources()
            val offlineReputationEntries = database.offlineReputationDao().getEntries()
            val settings = database.appSettingsDao().get() ?: AppSettingsEntity()
            val createdAt = System.currentTimeMillis()
            val payload = JSONObject()
                .put("payloadVersion", PAYLOAD_VERSION)
                .put("createdAt", createdAt)
                .put("settings", settings.toJson())
                .put("rules", JSONArray().also { array -> rules.forEach { array.put(it.toJson()) } })
                .put("blockedNumbers", JSONArray().also { array -> blocked.forEach { array.put(it.toJson()) } })
                .put("whitelistedNumbers", JSONArray().also { array -> whitelisted.forEach { array.put(it.toJson()) } })
                .put("schedules", JSONArray().also { array -> schedules.forEach { array.put(it.toJson()) } })
                .put("scheduleExceptions", JSONArray().also { array -> scheduleExceptions.forEach { array.put(it.toJson()) } })
                .put("offlineReputationSources", JSONArray().also { array -> offlineReputationSources.forEach { array.put(it.toJson()) } })
                .put("offlineReputationEntries", JSONArray().also { array -> offlineReputationEntries.forEach { array.put(it.toJson()) } })

            val salt = randomBytes(SALT_BYTES)
            val iv = randomBytes(GCM_IV_BYTES)
            val ciphertext = encrypt(payload.toString().toByteArray(Charsets.UTF_8), passphrase, salt, iv)
            val envelope = JSONObject()
                .put("format", FORMAT)
                .put("version", ENVELOPE_VERSION)
                .put("kdf", KDF)
                .put("iterations", KDF_ITERATIONS)
                .put("cipher", CIPHER)
                .put("salt", Base64.getEncoder().encodeToString(salt))
                .put("iv", Base64.getEncoder().encodeToString(iv))
                .put("ciphertext", Base64.getEncoder().encodeToString(ciphertext))

            output.write(envelope.toString().toByteArray(Charsets.UTF_8))
            output.flush()
            ExportResult(createdAt, rules.size, blocked.size, whitelisted.size, schedules.size)
        }
    }

    suspend fun restoreFrom(input: InputStream, passphrase: CharArray): Result<RestoreResult> = runCatching {
        require(passphrase.size >= MIN_PASSPHRASE_LENGTH) { "Backup passphrase must contain at least $MIN_PASSPHRASE_LENGTH characters." }
        withContext(Dispatchers.IO) {
            val envelope = JSONObject(readBounded(input).toString(Charsets.UTF_8))
            require(envelope.optString("format") == FORMAT) { "Unsupported backup format." }
            require(envelope.optInt("version") == ENVELOPE_VERSION) { "Unsupported backup envelope version." }
            require(envelope.optString("kdf") == KDF && envelope.optString("cipher") == CIPHER) { "Unsupported backup cryptography." }
            require(envelope.optInt("iterations") in MIN_KDF_ITERATIONS..MAX_KDF_ITERATIONS) { "Invalid KDF iteration count." }

            val salt = decode(envelope.getString("salt"), SALT_BYTES, SALT_BYTES)
            val iv = decode(envelope.getString("iv"), GCM_IV_BYTES, GCM_IV_BYTES)
            val ciphertext = decode(envelope.getString("ciphertext"), MIN_CIPHERTEXT_BYTES, MAX_BACKUP_BYTES)
            val plaintext = decrypt(ciphertext, passphrase, salt, iv, envelope.getInt("iterations"))
            val payload = parsePayload(JSONObject(plaintext.toString(Charsets.UTF_8)))

            // Parsing and all limits complete before touching persistent data.
            // withTransaction guarantees that a failed restore preserves the old state.
            database.withTransaction {
                database.blacklistRuleDao().clearAll()
                database.blockedNumberDao().clearAll()
                database.whitelistedNumberDao().clearAll()
                database.scheduleRuleDao().clearAll()
                database.offlineReputationDao().clearSources()

                payload.rules.forEach { database.blacklistRuleDao().insert(it.copy(id = 0)) }
                payload.blockedNumbers.forEach { database.blockedNumberDao().insert(it.copy(id = 0)) }
                payload.whitelistedNumbers.forEach { database.whitelistedNumberDao().insert(it.copy(id = 0)) }
                val restoredScheduleIds = mutableMapOf<Long, Long>()
                payload.schedules.forEach { schedule ->
                    val restoredId = database.scheduleRuleDao().insert(schedule.copy(id = 0))
                    restoredScheduleIds[schedule.id] = restoredId
                }
                payload.scheduleExceptions.forEach { exception ->
                    val restoredScheduleId = restoredScheduleIds[exception.scheduleRuleId]
                        ?: throw IllegalArgumentException("Schedule exception references a missing schedule.")
                    database.scheduleExceptionDao().insert(
                        exception.copy(id = 0, scheduleRuleId = restoredScheduleId)
                    )
                }
                val restoredReputationSourceIds = mutableMapOf<Long, Long>()
                payload.offlineReputationSources.forEach { source ->
                    restoredReputationSourceIds[source.id] = database.offlineReputationDao().insertSource(source.copy(id = 0))
                }
                if (payload.offlineReputationEntries.isNotEmpty()) {
                    database.offlineReputationDao().insertEntries(payload.offlineReputationEntries.map { entry ->
                        val restoredSourceId = restoredReputationSourceIds[entry.sourceId]
                            ?: throw IllegalArgumentException("Offline reputation entry references a missing source.")
                        entry.copy(id = 0, sourceId = restoredSourceId)
                    })
                }
                database.appSettingsDao().upsert(payload.settings.copy(id = 1, updatedAt = System.currentTimeMillis()))
            }

            RestoreResult(payload.rules.size, payload.blockedNumbers.size, payload.whitelistedNumbers.size, payload.schedules.size)
        }
    }

    private data class Payload(
        val settings: AppSettingsEntity,
        val rules: List<BlacklistRuleEntity>,
        val blockedNumbers: List<BlockedNumberEntity>,
        val whitelistedNumbers: List<WhitelistedNumberEntity>,
        val schedules: List<ScheduleRuleEntity>,
        val scheduleExceptions: List<ScheduleExceptionEntity>,
        val offlineReputationSources: List<OfflineReputationSourceEntity>,
        val offlineReputationEntries: List<OfflineReputationEntryEntity>
    )

    private fun parsePayload(json: JSONObject): Payload {
        require(json.optInt("payloadVersion") == PAYLOAD_VERSION) { "Unsupported backup payload version." }
        val rules = json.requiredArray("rules", MAX_RULES).map(::ruleFromJson)
        val blocked = json.requiredArray("blockedNumbers", MAX_NUMBERS).map(::blockedFromJson)
        val whitelisted = json.requiredArray("whitelistedNumbers", MAX_NUMBERS).map(::whitelistedFromJson)
        val schedules = json.requiredArray("schedules", MAX_SCHEDULES).map(::scheduleFromJson)
        val scheduleExceptions = json.optionalArray("scheduleExceptions", MAX_SCHEDULE_EXCEPTIONS).map(::scheduleExceptionFromJson)
        require(scheduleExceptions.all { exception -> schedules.any { it.id == exception.scheduleRuleId } }) {
            "Schedule exception references a missing schedule."
        }
        val offlineReputationSources = json.optionalArray("offlineReputationSources", MAX_REPUTATION_SOURCES)
            .map(::offlineReputationSourceFromJson)
        val offlineReputationEntries = json.optionalArray("offlineReputationEntries", MAX_REPUTATION_ENTRIES_TOTAL)
            .map(::offlineReputationEntryFromJson)
        validateOfflineReputationPayload(offlineReputationSources, offlineReputationEntries)
        return Payload(
            settings = settingsFromJson(json.getJSONObject("settings")),
            rules = rules,
            blockedNumbers = blocked,
            whitelistedNumbers = whitelisted,
            schedules = schedules,
            scheduleExceptions = scheduleExceptions,
            offlineReputationSources = offlineReputationSources,
            offlineReputationEntries = offlineReputationEntries
        )
    }

    private fun JSONObject.optionalArray(name: String, cap: Int): List<JSONObject> =
        if (!has(name) || isNull(name)) emptyList() else requiredArray(name, cap)

    private fun JSONObject.requiredArray(name: String, cap: Int): List<JSONObject> {
        val array = getJSONArray(name)
        require(array.length() <= cap) { "$name exceeds the restore limit." }
        return List(array.length()) { index -> array.getJSONObject(index) }
    }

    private fun AppSettingsEntity.toJson(): JSONObject = JSONObject()
        .put("blockUnknown", blockUnknown)
        .put("silenceUnknown", silenceUnknown)
        .put("blockPrivate", blockPrivate)
        .put("silencePrivate", silencePrivate)
        .put("blockAllExceptWhitelist", blockAllExceptWhitelist)
        .put("showBlockedNotification", showBlockedNotification)
        .put("hideBlockedCallsFromSystemLog", hideBlockedCallsFromSystemLog)
        .put("blockedLogRetentionDays", blockedLogRetentionDays)
        .put("allowOutboundCallbackGrace", allowOutboundCallbackGrace)
        .put("activeProfileId", activeProfileId)
        .put("themeMode", themeMode)
        .put("emergencyCallbackGraceUntil", emergencyCallbackGraceUntil)

    private fun BlacklistRuleEntity.toJson(): JSONObject = JSONObject()
        .put("enabled", isEnabled)
        .put("priority", priority)
        .put("type", ruleType)
        .put("pattern", pattern)
        .put("start", startNumber)
        .put("end", endNumber)
        .put("country", countryIso)
        .put("name", displayName)
        .put("notify", showNotification)
        .put("createdAt", createdAt)

    private fun BlockedNumberEntity.toJson(): JSONObject = JSONObject()
        .put("raw", rawNumber)
        .put("normalized", normalizedNumber)
        .put("name", displayName)
        .put("notify", showNotification)
        .put("createdAt", createdAt)

    private fun WhitelistedNumberEntity.toJson(): JSONObject = JSONObject()
        .put("raw", rawNumber)
        .put("normalized", normalizedNumber)
        .put("name", displayName)
        .put("createdAt", createdAt)

    private fun ScheduleRuleEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("enabled", isEnabled)
        .put("startMinutes", startMinutes)
        .put("endMinutes", endMinutes)
        .put("days", daysOfWeek)
        .put("mode", mode)
        .put("createdAt", createdAt)

    private fun ScheduleExceptionEntity.toJson(): JSONObject = JSONObject()
        .put("scheduleRuleId", scheduleRuleId)
        .put("number", normalizedNumber)
        .put("createdAt", createdAt)

    private fun OfflineReputationSourceEntity.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", sourceName)
        .put("version", sourceVersion)
        .put("url", sourceUrl)
        .put("fingerprint", fingerprintSha256)
        .put("entryCount", entryCount)
        .put("importedAt", importedAt)

    private fun OfflineReputationEntryEntity.toJson(): JSONObject = JSONObject()
        .put("sourceId", sourceId)
        .put("number", normalizedNumber)
        .put("score", riskScore)
        .put("category", category)

    private fun settingsFromJson(json: JSONObject): AppSettingsEntity {
        val themeMode = json.optString("themeMode", "SYSTEM")
        require(themeMode in ALLOWED_THEME_MODES) { "Unsupported theme mode in backup." }
        val activeProfileId = json.optString("activeProfileId", ProtectionProfiles.CUSTOM)
        require(activeProfileId in ALLOWED_PROFILE_IDS) { "Unsupported protection profile in backup." }
        val graceUntil = json.optLong("emergencyCallbackGraceUntil", 0L)
        require(graceUntil == 0L || graceUntil in 1..System.currentTimeMillis() + EmergencyCallbackGrace.DURATION_MS) {
            "Invalid emergency callback grace in backup."
        }
        val blockedLogRetentionDays = json.optLong("blockedLogRetentionDays", BlockedCallLogRetentionPolicy.NEVER)
        require(BlockedCallLogRetentionPolicy.isSupported(blockedLogRetentionDays)) {
            "Invalid blocked-call history retention in backup."
        }
        return AppSettingsEntity(
            blockUnknown = json.optBoolean("blockUnknown", false),
            silenceUnknown = json.optBoolean("silenceUnknown", false),
            blockPrivate = json.optBoolean("blockPrivate", true),
            silencePrivate = json.optBoolean("silencePrivate", false),
            blockAllExceptWhitelist = json.optBoolean("blockAllExceptWhitelist", false),
            showBlockedNotification = json.optBoolean("showBlockedNotification", true),
            hideBlockedCallsFromSystemLog = json.optBoolean("hideBlockedCallsFromSystemLog", false),
            blockedLogRetentionDays = blockedLogRetentionDays,
            allowOutboundCallbackGrace = json.optBoolean("allowOutboundCallbackGrace", false),
            activeProfileId = activeProfileId,
            themeMode = themeMode,
            emergencyCallbackGraceUntil = graceUntil,
        )
    }

    private fun ruleFromJson(json: JSONObject): BlacklistRuleEntity {
        val type = json.requiredText("type", 32)
        require(type in ALLOWED_RULE_TYPES) { "Unsupported rule type in backup." }
        val priority = json.optInt("priority", 30).also { require(it in 0..1000) { "Invalid rule priority." } }
        val pattern = json.optionalText("pattern", MAX_PATTERN_LENGTH)
        val startNumber = json.optionalText("start", MAX_PATTERN_LENGTH)
        val endNumber = json.optionalText("end", MAX_PATTERN_LENGTH)
        when (type) {
            BlacklistRuleEntity.TYPE_TEMP_BLOCK_ALL -> require(isSaneTemporaryExpiry(pattern)) {
                "Invalid temporary firewall expiry in backup."
            }
            BlacklistRuleEntity.TYPE_TEMP_ALLOW -> {
                require(!pattern.isNullOrBlank()) { "Invalid temporary allow number in backup." }
                require(isSaneTemporaryExpiry(startNumber)) { "Invalid temporary allow expiry in backup." }
            }
            BlacklistRuleEntity.TYPE_TEMP_OUTBOUND_CALLBACK -> {
                require(com.blacklist.app.domain.engine.OutboundCallbackGrace.isValidDigits(pattern.orEmpty())) {
                    "Invalid outgoing callback number in backup."
                }
                require(isSaneTemporaryExpiry(startNumber)) { "Invalid outgoing callback expiry in backup." }
            }
            BlacklistRuleEntity.TYPE_TEMP_BLOCK_EXACT -> {
                require(com.blacklist.app.domain.engine.TemporaryExactBlockPolicy.isValidE164Digits(pattern.orEmpty())) {
                    "Invalid temporary exact block number in backup."
                }
                require(isSaneTemporaryExpiry(startNumber)) { "Invalid temporary exact block expiry in backup." }
            }
        }
        return BlacklistRuleEntity(
            isEnabled = json.optBoolean("enabled", true),
            priority = priority,
            ruleType = type,
            pattern = pattern,
            startNumber = startNumber,
            endNumber = endNumber,
            countryIso = json.optionalText("country", 2)?.uppercase(),
            displayName = json.optionalText("name", MAX_TEXT_LENGTH),
            showNotification = json.optBoolean("notify", true),
            createdAt = json.optLong("createdAt", System.currentTimeMillis())
        )
    }

    private fun blockedFromJson(json: JSONObject): BlockedNumberEntity = BlockedNumberEntity(
        rawNumber = json.requiredText("raw", MAX_NUMBER_LENGTH),
        normalizedNumber = json.requiredText("normalized", MAX_NUMBER_LENGTH),
        displayName = json.optionalText("name", MAX_TEXT_LENGTH),
        showNotification = json.optBoolean("notify", true),
        createdAt = json.optLong("createdAt", System.currentTimeMillis())
    )

    private fun whitelistedFromJson(json: JSONObject): WhitelistedNumberEntity = WhitelistedNumberEntity(
        rawNumber = json.requiredText("raw", MAX_NUMBER_LENGTH),
        normalizedNumber = json.requiredText("normalized", MAX_NUMBER_LENGTH),
        displayName = json.optionalText("name", MAX_TEXT_LENGTH),
        createdAt = json.optLong("createdAt", System.currentTimeMillis())
    )

    private fun scheduleFromJson(json: JSONObject): ScheduleRuleEntity {
        val start = json.getInt("startMinutes")
        val end = json.getInt("endMinutes")
        val days = json.getInt("days")
        require(start in 0..1439 && end in 0..1439 && days in 1..127) { "Invalid schedule in backup." }
        val mode = json.requiredText("mode", 32)
        require(mode in ALLOWED_SCHEDULE_MODES) { "Unsupported schedule mode in backup." }
        return ScheduleRuleEntity(
            id = json.optLong("id", 0L).also { require(it >= 0) { "Invalid schedule id in backup." } },
            isEnabled = json.optBoolean("enabled", true),
            startMinutes = start,
            endMinutes = end,
            daysOfWeek = days,
            mode = mode,
            createdAt = json.optLong("createdAt", System.currentTimeMillis())
        )
    }

    private fun offlineReputationSourceFromJson(json: JSONObject): OfflineReputationSourceEntity {
        val id = json.optLong("id", -1L)
        val name = json.requiredText("name", MAX_REPUTATION_SOURCE_NAME_LENGTH)
        val version = json.optionalText("version", MAX_REPUTATION_VERSION_LENGTH)
        val url = json.optionalText("url", MAX_REPUTATION_URL_LENGTH)
        val fingerprint = json.requiredText("fingerprint", REPUTATION_FINGERPRINT_LENGTH)
        val entryCount = json.getInt("entryCount")
        val importedAt = json.optLong("importedAt", 0L)
        require(id > 0 && isSafeReputationText(name, MAX_REPUTATION_SOURCE_NAME_LENGTH)) { "Invalid offline reputation source." }
        require(version == null || isSafeReputationText(version, MAX_REPUTATION_VERSION_LENGTH)) { "Invalid offline reputation source version." }
        require(url == null || isSafeReputationUrl(url)) { "Invalid offline reputation source URL." }
        require(fingerprint.matches(REPUTATION_FINGERPRINT)) { "Invalid offline reputation fingerprint." }
        require(entryCount in 1..MAX_REPUTATION_ENTRIES_PER_SOURCE && importedAt > 0) { "Invalid offline reputation source metadata." }
        return OfflineReputationSourceEntity(id, name, version, url, fingerprint, entryCount, importedAt)
    }

    private fun offlineReputationEntryFromJson(json: JSONObject): OfflineReputationEntryEntity {
        val sourceId = json.optLong("sourceId", -1L)
        val number = json.requiredText("number", MAX_NUMBER_LENGTH)
        val score = json.getInt("score")
        val category = json.optionalText("category", MAX_REPUTATION_CATEGORY_LENGTH)
        require(sourceId > 0 && REPUTATION_E164.matches(number)) { "Invalid offline reputation entry number." }
        require(score in 0..100) { "Invalid offline reputation entry score." }
        require(category == null || isSafeReputationText(category, MAX_REPUTATION_CATEGORY_LENGTH, allowEmpty = true)) {
            "Invalid offline reputation entry category."
        }
        return OfflineReputationEntryEntity(sourceId = sourceId, normalizedNumber = number, riskScore = score, category = category)
    }

    private fun validateOfflineReputationPayload(
        sources: List<OfflineReputationSourceEntity>,
        entries: List<OfflineReputationEntryEntity>
    ) {
        require(sources.size <= MAX_REPUTATION_SOURCES && entries.size <= MAX_REPUTATION_ENTRIES_TOTAL) {
            "Offline reputation data exceeds the restore limit."
        }
        require(sources.map { it.id }.distinct().size == sources.size) { "Offline reputation source ids must be unique." }
        require(sources.map { it.fingerprintSha256 }.distinct().size == sources.size) { "Offline reputation source fingerprints must be unique." }
        val sourceIds = sources.map { it.id }.toSet()
        require(entries.all { it.sourceId in sourceIds }) { "Offline reputation entry references a missing source." }
        require(entries.groupBy { it.sourceId }.all { it.value.size <= MAX_REPUTATION_ENTRIES_PER_SOURCE }) {
            "Offline reputation source exceeds the entry limit."
        }
        require(entries.groupBy { it.sourceId }.all { (_, sourceEntries) ->
            sourceEntries.map { it.normalizedNumber }.distinct().size == sourceEntries.size
        }) { "Offline reputation entries must be unique within each source." }
        require(entries.groupBy { it.sourceId }.all { (sourceId, sourceEntries) ->
            sources.first { it.id == sourceId }.entryCount == sourceEntries.size
        }) { "Offline reputation source entry counts do not match their entries." }
        require(sources.all { source -> entries.any { it.sourceId == source.id } }) {
            "Offline reputation sources must contain at least one entry."
        }
    }

    private fun isSafeReputationText(value: String, maxLength: Int, allowEmpty: Boolean = false): Boolean =
        value.length.let { if (allowEmpty) it <= maxLength else it in 1..maxLength } &&
            value.all { !it.isISOControl() && it != '\u007f' }

    private fun isSafeReputationUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        value.length <= MAX_REPUTATION_URL_LENGTH && uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null
    }.getOrDefault(false)

    private fun scheduleExceptionFromJson(json: JSONObject): ScheduleExceptionEntity {
        val scheduleRuleId = json.optLong("scheduleRuleId", -1L)
        require(scheduleRuleId > 0) { "Invalid schedule exception parent in backup." }
        val number = json.requiredText("number", MAX_NUMBER_LENGTH)
        val canonical = PhoneNumberUtils.normalize(number)
        require(canonical == number && number.filter(Char::isDigit).length in 3..32) {
            "Invalid schedule exception number in backup."
        }
        return ScheduleExceptionEntity(
            scheduleRuleId = scheduleRuleId,
            normalizedNumber = number,
            createdAt = json.optLong("createdAt", System.currentTimeMillis())
        )
    }

    private fun isSaneTemporaryExpiry(value: String?): Boolean =
        value?.toLongOrNull()?.let { it > 0 && it <= System.currentTimeMillis() + MAX_TEMPORARY_EXPIRY_FUTURE_MS } == true

    private fun JSONObject.requiredText(name: String, maxLength: Int): String =
        getString(name).also { require(it.isNotBlank() && it.length <= maxLength) { "Invalid $name in backup." } }

    private fun JSONObject.optionalText(name: String, maxLength: Int): String? =
        if (isNull(name)) null else getString(name).takeIf { it.length <= maxLength }
            ?: throw IllegalArgumentException("Invalid $name in backup.")

    private fun encrypt(plaintext: ByteArray, passphrase: CharArray, salt: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt, KDF_ITERATIONS), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(plaintext)
    }

    private fun decrypt(ciphertext: ByteArray, passphrase: CharArray, salt: ByteArray, iv: ByteArray, iterations: Int): ByteArray {
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt, iterations), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): javax.crypto.SecretKey {
        val spec = PBEKeySpec(passphrase, salt, iterations, AES_KEY_BITS)
        return try {
            val bytes = SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded
            javax.crypto.spec.SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun readBounded(input: InputStream): ByteArray {
        val result = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= MAX_BACKUP_BYTES) { "Backup file is too large." }
            result.write(buffer, 0, read)
        }
        return result.toByteArray()
    }

    private fun decode(value: String, minLength: Int, maxLength: Int): ByteArray {
        val bytes = Base64.getDecoder().decode(value)
        require(bytes.size in minLength..maxLength) { "Invalid encrypted backup field." }
        return bytes
    }

    private fun randomBytes(size: Int): ByteArray = ByteArray(size).also(SecureRandom()::nextBytes)

    private companion object {
        const val FORMAT = "blacklist-local-backup"
        const val ENVELOPE_VERSION = 1
        const val PAYLOAD_VERSION = 1
        const val KDF = "PBKDF2WithHmacSHA256"
        const val CIPHER = "AES/GCM/NoPadding"
        const val KDF_ITERATIONS = 210_000
        const val MIN_KDF_ITERATIONS = 150_000
        const val MAX_KDF_ITERATIONS = 1_000_000
        const val AES_KEY_BITS = 256
        const val GCM_TAG_BITS = 128
        const val SALT_BYTES = 16
        const val GCM_IV_BYTES = 12
        const val MIN_CIPHERTEXT_BYTES = 16
        const val MAX_BACKUP_BYTES = 32 * 1024 * 1024
        const val MAX_RULES = 10_000
        const val MAX_NUMBERS = 50_000
        const val MAX_SCHEDULES = 1_000
        const val MAX_SCHEDULE_EXCEPTIONS = 10_000
        const val MIN_PASSPHRASE_LENGTH = 12
        const val MAX_NUMBER_LENGTH = 64
        const val MAX_PATTERN_LENGTH = 128
        const val MAX_TEXT_LENGTH = 200
        const val MAX_TEMPORARY_EXPIRY_FUTURE_MS = 7L * 24 * 60 * 60 * 1000
        const val MAX_REPUTATION_SOURCES = 10
        const val MAX_REPUTATION_ENTRIES_PER_SOURCE = 5_000
        const val MAX_REPUTATION_ENTRIES_TOTAL = 10_000
        const val MAX_REPUTATION_SOURCE_NAME_LENGTH = 100
        const val MAX_REPUTATION_VERSION_LENGTH = 64
        const val MAX_REPUTATION_URL_LENGTH = 512
        const val MAX_REPUTATION_CATEGORY_LENGTH = 80
        const val REPUTATION_FINGERPRINT_LENGTH = 64

        val REPUTATION_E164 = Regex("^\\+[1-9][0-9]{6,14}$")
        val REPUTATION_FINGERPRINT = Regex("^[a-f0-9]{64}$")

        val ALLOWED_RULE_TYPES = setOf(
            BlacklistRuleEntity.TYPE_EXACT,
            BlacklistRuleEntity.TYPE_PREFIX,
            BlacklistRuleEntity.TYPE_SUFFIX,
            BlacklistRuleEntity.TYPE_CONTAINS,
            BlacklistRuleEntity.TYPE_RANGE,
            BlacklistRuleEntity.TYPE_COUNTRY,
            BlacklistRuleEntity.TYPE_HIDDEN,
            BlacklistRuleEntity.TYPE_UNKNOWN,
            BlacklistRuleEntity.TYPE_TEMP_BLOCK_ALL,
            BlacklistRuleEntity.TYPE_TEMP_ALLOW,
            BlacklistRuleEntity.TYPE_TEMP_OUTBOUND_CALLBACK,
            BlacklistRuleEntity.TYPE_TEMP_BLOCK_EXACT
        )
        val ALLOWED_THEME_MODES = setOf("SYSTEM", "LIGHT", "DARK")
        val ALLOWED_PROFILE_IDS = setOf(
            ProtectionProfiles.CUSTOM,
            ProtectionProfiles.NORMAL,
            ProtectionProfiles.FOCUS,
            ProtectionProfiles.WHITELIST_ONLY
        )
        val ALLOWED_SCHEDULE_MODES = setOf(
            ScheduleRuleEntity.MODE_ALL,
            ScheduleRuleEntity.MODE_ALL_EXCEPT_WHITELIST,
            ScheduleRuleEntity.MODE_UNKNOWN_PRIVATE,
            ScheduleRuleEntity.MODE_BLACKLIST
        )
    }
}
