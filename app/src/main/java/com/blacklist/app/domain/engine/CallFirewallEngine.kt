package com.blacklist.app.domain.engine

import com.blacklist.app.data.local.entity.BlacklistRuleEntity
import com.blacklist.app.data.local.entity.ScheduleRuleEntity
import com.blacklist.app.domain.model.*
import com.blacklist.app.domain.normalization.PhoneNumberNormalizer
import com.blacklist.app.util.ScheduleEvaluator

/**
 * Deterministic local decision engine for the call-screening hot path.
 *
 * Policy data is supplied by [PolicySnapshotStore], so evaluating an incoming
 * call never queries Room or ContactsProvider. Any durable logging, contact
 * name enrichment, notification, and reputation write must happen after
 * Telecom receives the response.
 */
class CallFirewallEngine(
    private val policySnapshots: PolicySnapshotProvider,
    private val normalizer: PhoneNumberNormalizer,
    private val blacklistEngine: BlacklistEngine,
    private val riskEngine: RiskScoringEngine,
    private val behaviorEngine: BehaviorEngine
) {
    suspend fun evaluate(event: CallEvent): EnforcementDecision {
        val snapshot = policySnapshots.snapshot()
        val enrichedEvent = enrichContactFromSnapshot(event, snapshot)

        // Emergency routing must always win over user-defined broad rules.
        if (normalizer.isEmergencyNumber(enrichedEvent.phoneNumber)) {
            return decision(enrichedEvent, Decision.ALLOW, 0, ReputationLevel.TRUSTED, listOf("Emergency safeguard"), emptyList(), "emergency")
        }

        // Keep behavioral signals entirely in memory. This is intentionally
        // cheap and independent from the persistent blocked-call log.
        behaviorEngine.recordAttempt(enrichedEvent.phoneNumber.digitsOnly)
        val signals = behaviorEngine.signalsFor(enrichedEvent.phoneNumber)

        // The precedence order is deliberately explicit and stable.
        if (TemporaryFirewall.allowMatches(snapshot.rules, enrichedEvent.phoneNumber.digitsOnly)) {
            return decision(enrichedEvent, Decision.ALLOW, 0, ReputationLevel.NEUTRAL, listOf("Temporary allow active"), emptyList(), "temporary_allow")
        }
        if (snapshot.isWhitelisted(enrichedEvent.phoneNumber, normalizer)) {
            return decision(enrichedEvent, Decision.ALLOW, 0, ReputationLevel.TRUSTED, listOf("Whitelisted"), emptyList(), "whitelist")
        }

        TemporaryFirewall.blockExactMatches(snapshot.rules, enrichedEvent.phoneNumber.digitsOnly)?.let { rule ->
            return decision(
                enrichedEvent,
                Decision.BLOCK,
                75,
                ReputationLevel.SUSPICIOUS,
                listOf("Temporary exact block active"),
                listOf(toCallRule(rule)),
                "temporary_block_exact"
            )
        }

        val matchedBlacklist = blacklistEngine.findMatching(enrichedEvent.phoneNumber, snapshot.rules)
        if (matchedBlacklist.isNotEmpty()) {
            val top = matchedBlacklist.first()
            val action = if (top.enforcement == BlacklistRuleEntity.ENFORCEMENT_SILENCE) {
                Decision.SILENCE
            } else {
                Decision.BLOCK
            }
            val risk = riskEngine.score(
                enrichedEvent,
                null,
                signals,
                isSuspiciousPrefix(enrichedEvent.phoneNumber),
                isWhitelisted = false
            )
            return decision(
                enrichedEvent,
                action,
                risk,
                ReputationLevel.SUSPICIOUS,
                listOf("Matched ${if (action == Decision.SILENCE) "silent" else "blocking"} blacklist rule: ${top.ruleType} ${top.pattern ?: top.countryIso}"),
                matchedBlacklist.map(::toCallRule),
                if (action == Decision.SILENCE) "blacklist_silence" else "blacklist"
            )
        }

        if (snapshot.isLegacyBlocked(enrichedEvent.phoneNumber, normalizer)) {
            return decision(enrichedEvent, Decision.BLOCK, 60, ReputationLevel.SUSPICIOUS, listOf("Legacy blacklist exact match"), emptyList(), "legacy_blacklist")
        }

        val settings = snapshot.settings
        if ((settings?.allowOutboundCallbackGrace ?: false) &&
            (OutboundCallbackGrace.isActive(enrichedEvent.phoneNumber.digitsOnly) ||
                TemporaryFirewall.outboundCallbackMatches(snapshot.rules, enrichedEvent.phoneNumber.digitsOnly))
        ) {
            return decision(
                enrichedEvent,
                Decision.ALLOW,
                0,
                ReputationLevel.TRUSTED,
                listOf("Recent outgoing-call callback grace active"),
                emptyList(),
                "outbound_callback_grace"
            )
        }

        if (EmergencyCallbackGrace.isActive(settings?.emergencyCallbackGraceUntil ?: 0L)) {
            return decision(
                enrichedEvent,
                Decision.ALLOW,
                0,
                ReputationLevel.TRUSTED,
                listOf("Emergency callback grace active"),
                emptyList(),
                "emergency_callback_grace"
            )
        }

        evaluateSchedule(enrichedEvent, snapshot)?.let { return it }

        TemporaryFirewall.blockAllActive(snapshot.rules)?.let {
            return decision(enrichedEvent, Decision.BLOCK, 90, ReputationLevel.SUSPICIOUS, listOf("Temporary firewall active"), emptyList(), "temporary_block_all")
        }

        if (settings != null) {
            if (settings.blockAllExceptWhitelist) {
                return decision(enrichedEvent, Decision.BLOCK, 85, ReputationLevel.SUSPICIOUS, listOf("Block all except whitelist"), emptyList(), "policy")
            }
            if (settings.blockPrivate && enrichedEvent.phoneNumber.presentation == Presentation.RESTRICTED) {
                return broadPolicyDecision(
                    event = enrichedEvent,
                    silence = settings.silencePrivate,
                    risk = 70,
                    reputation = ReputationLevel.SUSPICIOUS,
                    reason = "Private or hidden policy",
                    backend = "private"
                )
            }
            if (settings.blockUnknown && enrichedEvent.phoneNumber.presentation == Presentation.UNKNOWN) {
                return broadPolicyDecision(
                    event = enrichedEvent,
                    silence = settings.silenceUnknown,
                    risk = 50,
                    reputation = ReputationLevel.NEUTRAL,
                    reason = "Unknown caller policy",
                    backend = "unknown"
                )
            }
            // Contact access is optional. A revoked or unavailable permission
            // never turns all known callers into accidental blocks.
            if (settings.blockUnknown && snapshot.canReadContacts && enrichedEvent.phoneNumber.presentation == Presentation.ALLOWED && enrichedEvent.contact?.isInContacts != true) {
                return broadPolicyDecision(
                    event = enrichedEvent,
                    silence = settings.silenceUnknown,
                    risk = 55,
                    reputation = ReputationLevel.NEUTRAL,
                    reason = "Not in contacts",
                    backend = "unknown"
                )
            }
        }

        val storedReputation = snapshot.reputationFor(enrichedEvent.phoneNumber)
        val offlineReputation = snapshot.offlineReputationFor(enrichedEvent.phoneNumber)
        val reputation = effectiveReputation(storedReputation, offlineReputation)
        val calculatedRisk = riskEngine.score(
            enrichedEvent,
            reputation,
            signals,
            isSuspiciousPrefix(enrichedEvent.phoneNumber),
            isWhitelisted = false
        )
        val importedRisk = offlineReputation
            ?.takeIf { storedReputation?.userVerdict.isNullOrBlank() }
            ?.riskScore
            ?: 0
        val risk = maxOf(calculatedRisk, importedRisk)
        if (risk >= 80) {
            val reasons = buildList {
                add("High risk score $risk")
                offlineReputation?.takeIf { storedReputation?.userVerdict.isNullOrBlank() }?.let {
                    val sources = it.sources.joinToString().take(MAX_SOURCE_REASON_LENGTH)
                    val categories = it.categories.joinToString().take(MAX_CATEGORY_REASON_LENGTH)
                    add("Offline reputation: $sources; score ${it.riskScore}" +
                        categories.takeIf(String::isNotBlank)?.let { category -> "; category $category" }.orEmpty())
                }
                add("Signals: burst=${signals.isBurst} repeated=${signals.repeatedCount}")
            }
            return decision(
                enrichedEvent,
                Decision.BLOCK,
                risk,
                reputation?.level ?: ReputationLevel.NEUTRAL,
                reasons,
                emptyList(),
                if (offlineReputation != null && storedReputation?.userVerdict.isNullOrBlank()) "offline_reputation" else "risk"
            )
        }
        if (signals.isBurst && risk >= 60) {
            return decision(enrichedEvent, Decision.BLOCK, risk, reputation?.level ?: ReputationLevel.NEUTRAL, listOf("Suspicious burst ${signals.callsLast10Minutes}/10m"), emptyList(), "behavior")
        }

        return decision(enrichedEvent, Decision.ALLOW, risk, reputation?.level ?: ReputationLevel.NEUTRAL, listOf("No matching block rule"), emptyList(), "default_allow")
    }

    private fun enrichContactFromSnapshot(event: CallEvent, snapshot: PolicySnapshotStore.Snapshot): CallEvent {
        if (event.phoneNumber.presentation != Presentation.ALLOWED || !snapshot.canReadContacts) return event
        val known = snapshot.isKnownContact(event.phoneNumber, normalizer)
        return event.copy(contact = CallerContact(displayName = null, isInContacts = known))
    }

    private fun evaluateSchedule(event: CallEvent, snapshot: PolicySnapshotStore.Snapshot): EnforcementDecision? {
        val rule = ScheduleEvaluator.matchingRule(snapshot.schedules) ?: return null
        val isScheduleException = snapshot.scheduleExceptions.any { exception ->
            exception.scheduleRuleId == rule.id &&
                normalizer.matches(event.phoneNumber, normalizer.normalize(exception.normalizedNumber))
        }
        if (isScheduleException) {
            return decision(
                event,
                Decision.ALLOW,
                0,
                ReputationLevel.TRUSTED,
                listOf("Schedule exception active"),
                emptyList(),
                "schedule_exception"
            )
        }
        return when (rule.mode) {
            ScheduleRuleEntity.MODE_ALL -> decision(event, Decision.BLOCK, 90, ReputationLevel.NEUTRAL, listOf("Schedule: block all"), emptyList(), "schedule")
            ScheduleRuleEntity.MODE_ALL_EXCEPT_WHITELIST -> decision(event, Decision.BLOCK, 85, ReputationLevel.NEUTRAL, listOf("Schedule: all except whitelist"), emptyList(), "schedule")
            ScheduleRuleEntity.MODE_UNKNOWN_PRIVATE -> {
                val unknown = event.phoneNumber.presentation != Presentation.ALLOWED || event.contact?.isInContacts == false
                if (unknown) decision(event, Decision.BLOCK, 60, ReputationLevel.NEUTRAL, listOf("Schedule: unknown or private"), emptyList(), "schedule") else null
            }
            ScheduleRuleEntity.MODE_BLACKLIST -> {
                val matched = blacklistEngine.findMatching(event.phoneNumber, snapshot.rules)
                if (matched.isNotEmpty()) decision(event, Decision.BLOCK, 70, ReputationLevel.SUSPICIOUS, listOf("Schedule: blacklist rule"), matched.map(::toCallRule), "schedule") else null
            }
            else -> null
        }
    }

    private fun isSuspiciousPrefix(number: PhoneNumber): Boolean {
        val suspicious = listOf("+216", "+212", "+234", "+92")
        return suspicious.any { number.normalized.startsWith(it) }
    }

    private fun broadPolicyDecision(
        event: CallEvent,
        silence: Boolean,
        risk: Int,
        reputation: ReputationLevel,
        reason: String,
        backend: String
    ): EnforcementDecision = decision(
        event = event,
        decision = if (silence) Decision.SILENCE else Decision.BLOCK,
        risk = risk,
        reputation = reputation,
        reasons = listOf(if (silence) "$reason; silenced locally" else reason),
        rules = emptyList(),
        backendHint = if (silence) "${backend}_silence" else backend
    )

    private fun decision(
        event: CallEvent,
        decision: Decision,
        risk: Int,
        reputation: ReputationLevel,
        reasons: List<String>,
        rules: List<CallRule>,
        backendHint: String
    ): EnforcementDecision = EnforcementDecision(
        callEvent = event,
        decision = decision,
        riskScore = risk,
        reputation = reputation,
        reasons = reasons,
        matchedRules = rules,
        backend = EnforcementBackendType.CALL_SCREENING,
        verification = event.verificationStatus,
        explainable = ExplainableDecision(
            summary = "${decision.name} - $backendHint - Risk ${risk}/100 (${riskLevel(risk).name})",
            riskLevel = riskLevel(risk),
            details = reasons,
            matchedRuleIds = rules.map { it.id },
            backend = backendHint,
            verification = event.verificationStatus.name
        )
    )

    private fun toCallRule(entity: BlacklistRuleEntity): CallRule =
        CallRule(
            id = entity.id,
            priority = entity.priority,
            action = if (entity.enforcement == BlacklistRuleEntity.ENFORCEMENT_SILENCE) {
                RuleAction.SILENCE
            } else {
                RuleAction.BLOCK
            },
            conditions = emptyList(),
            description = "${entity.ruleType}:${entity.pattern}"
        )

    private fun effectiveReputation(
        stored: com.blacklist.app.data.local.entity.CallerReputationEntity?,
        offline: OfflineReputationSignal?
    ): CallerReputation? {
        val local = stored?.toDomain()
        // A user verdict is a direct local instruction and must never be diluted
        // by imported data, including a list the user previously selected.
        if (offline == null || !stored?.userVerdict.isNullOrBlank()) return local
        val base = local ?: CallerReputation(normalizedNumber = "offline")
        val risk = maxOf(base.riskScore, offline.riskScore)
        val level = when {
            base.level == ReputationLevel.MALICIOUS || risk >= 80 -> ReputationLevel.MALICIOUS
            base.level == ReputationLevel.SUSPICIOUS || risk >= 60 -> ReputationLevel.SUSPICIOUS
            else -> base.level
        }
        return base.copy(riskScore = risk, level = level)
    }

    private fun com.blacklist.app.data.local.entity.CallerReputationEntity.toDomain(): CallerReputation {
        val reputationLevel = runCatching { ReputationLevel.valueOf(this.level) }
            .getOrDefault(ReputationLevel.NEUTRAL)
        return CallerReputation(
            normalizedNumber = normalizedNumber,
            totalCalls = totalCalls,
            blockedCalls = blockedCalls,
            allowedCalls = allowedCalls,
            spamScore = spamScore,
            riskScore = riskScore,
            level = reputationLevel,
            userVerdict = userVerdict?.let { runCatching { UserVerdict.valueOf(it) }.getOrNull() },
            behaviorFlags = behaviorFlags.split(',').filter { it.isNotBlank() }.toSet()
        )
    }

    private companion object {
        const val MAX_SOURCE_REASON_LENGTH = 120
        const val MAX_CATEGORY_REASON_LENGTH = 80
    }
}
