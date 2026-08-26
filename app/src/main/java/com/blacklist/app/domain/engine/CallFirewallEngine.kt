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

        val matchedBlacklist = blacklistEngine.findMatching(enrichedEvent.phoneNumber, snapshot.rules)
        if (matchedBlacklist.isNotEmpty()) {
            val top = matchedBlacklist.first()
            val risk = riskEngine.score(
                enrichedEvent,
                null,
                signals,
                isSuspiciousPrefix(enrichedEvent.phoneNumber),
                isWhitelisted = false
            )
            return decision(
                enrichedEvent,
                Decision.BLOCK,
                risk,
                ReputationLevel.SUSPICIOUS,
                listOf("Matched blacklist rule: ${top.ruleType} ${top.pattern ?: top.countryIso}"),
                matchedBlacklist.map(::toCallRule),
                "blacklist"
            )
        }

        if (snapshot.isLegacyBlocked(enrichedEvent.phoneNumber, normalizer)) {
            return decision(enrichedEvent, Decision.BLOCK, 60, ReputationLevel.SUSPICIOUS, listOf("Legacy blacklist exact match"), emptyList(), "legacy_blacklist")
        }

        val settings = snapshot.settings
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
                return decision(enrichedEvent, Decision.BLOCK, 70, ReputationLevel.SUSPICIOUS, listOf("Private or hidden policy"), emptyList(), "private")
            }
            if (settings.blockUnknown && enrichedEvent.phoneNumber.presentation == Presentation.UNKNOWN) {
                return decision(enrichedEvent, Decision.BLOCK, 50, ReputationLevel.NEUTRAL, listOf("Unknown caller policy"), emptyList(), "unknown")
            }
            // Contact access is optional. A revoked or unavailable permission
            // never turns all known callers into accidental blocks.
            if (settings.blockUnknown && snapshot.canReadContacts && enrichedEvent.phoneNumber.presentation == Presentation.ALLOWED && enrichedEvent.contact?.isInContacts != true) {
                return decision(enrichedEvent, Decision.BLOCK, 55, ReputationLevel.NEUTRAL, listOf("Not in contacts"), emptyList(), "unknown")
            }
        }

        val reputation = snapshot.reputationFor(enrichedEvent.phoneNumber)?.toDomain()
        val risk = riskEngine.score(
            enrichedEvent,
            reputation,
            signals,
            isSuspiciousPrefix(enrichedEvent.phoneNumber),
            isWhitelisted = false
        )
        if (risk >= 80) {
            return decision(enrichedEvent, Decision.BLOCK, risk, reputation?.level ?: ReputationLevel.NEUTRAL, listOf("High risk score $risk", "Signals: burst=${signals.isBurst} repeated=${signals.repeatedCount}"), emptyList(), "risk")
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
            action = RuleAction.BLOCK,
            conditions = emptyList(),
            description = "${entity.ruleType}:${entity.pattern}"
        )

    private fun com.blacklist.app.data.local.entity.CallerReputationEntity.toDomain(): CallerReputation {
        val reputationLevel = runCatching { ReputationLevel.valueOf(this.level) }
            .getOrDefault(ReputationLevel.NEUTRAL)
        return CallerReputation(
            normalizedNumber = normalizedNumber,
            totalCalls = totalCalls,
            blockedCalls = blockedCalls,
            level = reputationLevel
        )
    }

}
