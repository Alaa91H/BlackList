package com.blacklist.app.domain.engine

import com.blacklist.app.data.local.BlackListDatabase
import com.blacklist.app.data.local.entity.AppSettingsEntity
import com.blacklist.app.data.local.entity.BlacklistRuleEntity
import com.blacklist.app.data.local.entity.BlockedNumberEntity
import com.blacklist.app.data.local.entity.CallerReputationEntity
import com.blacklist.app.data.local.entity.OfflineReputationEntryEntity
import com.blacklist.app.data.local.entity.OfflineReputationSourceEntity
import com.blacklist.app.data.local.entity.ScheduleExceptionEntity
import com.blacklist.app.data.local.entity.ScheduleRuleEntity
import com.blacklist.app.data.local.entity.WhitelistedNumberEntity
import com.blacklist.app.domain.model.PhoneNumber
import com.blacklist.app.domain.normalization.PhoneNumberNormalizer
import com.blacklist.app.util.ContactUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

fun interface PolicySnapshotProvider {
    fun snapshot(): PolicySnapshotStore.Snapshot
}

/**
 * Keeps all policy inputs needed by the call-screening hot path in memory.
 *
 * Room, ContactsProvider, and other slow sources are read only on this store's
 * background dispatcher. [snapshot] is immutable, therefore the screening
 * service can read one coherent policy without waiting for I/O or locks.
 */
class PolicySnapshotStore(
    private val database: BlackListDatabase,
    private val normalizer: PhoneNumberNormalizer,
    private val contactUtils: ContactUtils
) : PolicySnapshotProvider {
    data class Snapshot(
        val version: Long = 0,
        val refreshedAt: Long = 0,
        val rules: List<BlacklistRuleEntity> = emptyList(),
        val schedules: List<ScheduleRuleEntity> = emptyList(),
        val scheduleExceptions: List<ScheduleExceptionEntity> = emptyList(),
        val whitelist: List<PhoneNumber> = emptyList(),
        val legacyBlocked: List<PhoneNumber> = emptyList(),
        val knownContactNumbers: List<PhoneNumber> = emptyList(),
        val canReadContacts: Boolean = false,
        val settings: AppSettingsEntity? = null,
        val reputations: Map<String, CallerReputationEntity> = emptyMap(),
        val offlineReputations: Map<String, OfflineReputationSignal> = emptyMap()
    ) {
        fun isKnownContact(number: PhoneNumber, matcher: PhoneNumberNormalizer): Boolean =
            knownContactNumbers.any { matcher.matches(number, it) }

        fun isWhitelisted(number: PhoneNumber, matcher: PhoneNumberNormalizer): Boolean =
            whitelist.any { matcher.matches(number, it) }

        fun isLegacyBlocked(number: PhoneNumber, matcher: PhoneNumberNormalizer): Boolean =
            legacyBlocked.any { matcher.matches(number, it) }

        fun reputationFor(number: PhoneNumber): CallerReputationEntity? =
            reputations[number.normalized] ?: reputations[number.e164]

        fun offlineReputationFor(number: PhoneNumber): OfflineReputationSignal? =
            offlineReputations[number.normalized] ?: offlineReputations[number.e164]
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)
    private val refreshMutex = Mutex()
    private val state = AtomicReference(Snapshot())

    /** A lock-free immutable view used by CallScreeningService. */
    override fun snapshot(): Snapshot = state.get()

    /**
     * Starts background hydration and watches all persisted policy sources.
     * Calling this method repeatedly is safe.
     */
    fun start() {
        if (!started.compareAndSet(false, true)) return

        scope.launch { refresh() }
        scope.launch { database.blacklistRuleDao().observeAll().collect { refresh() } }
        scope.launch { database.scheduleRuleDao().observeAll().collect { refresh() } }
        scope.launch { database.scheduleExceptionDao().observeAll().collect { refresh() } }
        scope.launch { database.whitelistedNumberDao().observeAll().collect { refresh() } }
        scope.launch { database.blockedNumberDao().observeAll().collect { refresh() } }
        scope.launch { database.appSettingsDao().observe().collect { refresh() } }
        scope.launch { database.callerReputationDao().observeAll().collect { refresh() } }
        scope.launch { database.offlineReputationDao().observeEntries().collect { refresh() } }
        scope.launch { database.offlineReputationDao().observeSources().collect { refresh() } }
    }

    /** Forces a background reload; intended for app start and tests. */
    suspend fun refresh() = refreshMutex.withLock {
        val rules = safe { database.blacklistRuleDao().getAll() }.orEmpty()
        val schedules = safe { database.scheduleRuleDao().getAll() }.orEmpty()
        val scheduleExceptions = safe { database.scheduleExceptionDao().getAll() }.orEmpty()
        val whitelist = safe { database.whitelistedNumberDao().getAll() }.orEmpty()
        val legacyBlocked = safe { database.blockedNumberDao().getAll() }.orEmpty()
        val settings = safe { database.appSettingsDao().get() }
        val reputations = safe { database.callerReputationDao().getAll() }.orEmpty()
        val offlineSources = safe { database.offlineReputationDao().getSources() }.orEmpty()
        val offlineEntries = safe { database.offlineReputationDao().getEntries() }.orEmpty()
        val offlineReputations = aggregateOfflineReputations(offlineEntries, offlineSources)
        // Contacts remain strictly optional. A provider failure or a revoked
        // permission must not prevent refreshed local rules from being applied.
        val canReadContacts = contactUtils.canReadContacts()
        val contacts = if (canReadContacts) safe { contactUtils.getAllPhoneNumbers() }.orEmpty() else emptyList()

        val previous = state.get()
        state.set(
            Snapshot(
                version = previous.version + 1,
                refreshedAt = System.currentTimeMillis(),
                rules = rules.filter { it.isEnabled },
                schedules = schedules.filter { it.isEnabled },
                scheduleExceptions = scheduleExceptions,
                whitelist = whitelist.map(WhitelistedNumberEntity::normalizedNumber).map(normalizer::normalize),
                legacyBlocked = legacyBlocked.map(BlockedNumberEntity::normalizedNumber).map(normalizer::normalize),
                knownContactNumbers = contacts.map(normalizer::normalize),
                canReadContacts = canReadContacts,
                settings = settings,
                reputations = reputations.associateBy { it.normalizedNumber },
                offlineReputations = offlineReputations
            )
        )
    }

    private suspend fun <T> safe(block: suspend () -> T): T? =
        try {
            block()
        } catch (_: Exception) {
            null
        }
}


internal fun aggregateOfflineReputations(
    entries: List<OfflineReputationEntryEntity>,
    sources: List<OfflineReputationSourceEntity>
): Map<String, OfflineReputationSignal> {
    val sourceNames = sources.associate { it.id to it.sourceName }
    return entries.groupBy { it.normalizedNumber }.mapValues { (_, matches) ->
        val highest = matches.maxBy { it.riskScore }
        OfflineReputationSignal(
            riskScore = highest.riskScore,
            sources = matches.mapNotNull { sourceNames[it.sourceId] }.distinct().sorted(),
            categories = matches.mapNotNull { it.category }.distinct().sorted()
        )
    }
}

data class OfflineReputationSignal(
    val riskScore: Int,
    val sources: List<String>,
    val categories: List<String>
)
