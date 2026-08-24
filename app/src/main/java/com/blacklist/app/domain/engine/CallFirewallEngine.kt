package com.blacklist.app.domain.engine

import com.blacklist.app.data.local.BlackListDatabase
import com.blacklist.app.domain.model.*
import com.blacklist.app.domain.normalization.PhoneNumberNormalizer
import com.blacklist.app.util.ScheduleEvaluator

/**
 * Central orchestrator: Rule -> Reputation -> Behavior -> Risk -> Policy -> Decision
 * All logic lives here, not in Service/Activity.
 */
class CallFirewallEngine(
    private val db: BlackListDatabase,
    private val normalizer: PhoneNumberNormalizer,
    private val blacklistEngine: BlacklistEngine,
    private val riskEngine: RiskScoringEngine,
    private val reputationEngine: ReputationEngine,
    private val behaviorEngine: BehaviorEngine
) {
    suspend fun evaluate(event: CallEvent): EnforcementDecision {
        val start = System.currentTimeMillis()
        // 1. Caller context already in event

        // 2. Check whitelist (explicit allow priority 20)
        val isWhitelisted = isWhitelisted(event.phoneNumber)
        if (isWhitelisted) {
            return decision(event, Decision.ALLOW, 0, ReputationLevel.TRUSTED, listOf("Whitelisted"), emptyList(), "whitelist bypass")
        }

        // 3. Schedule profile rules (priority 70)
        val scheduleRule = activeScheduleRule()
        if (scheduleRule != null) {
            // Let schedule decide; we treat it as profile rule
            val scheduleDecision = evaluateSchedule(event, scheduleRule)
            if (scheduleDecision != null) return scheduleDecision
        }

        // 4. Load blacklist rules (priority 30) + hidden/unknown (priority 40)
        val rules = try { db.blacklistRuleDao().getEnabled() } catch (_: Exception) { emptyList() }
        val matchedBlacklist = blacklistEngine.findMatching(event.phoneNumber, rules)
        if (matchedBlacklist.isNotEmpty()) {
            // Check if any matched is EXPLICIT_BLOCK vs COUNTRY etc.
            val top = matchedBlacklist.first()
            val risk = riskEngine.score(event, null, BehaviorSignals(), isSuspiciousPrefix = isSuspiciousPrefix(event.phoneNumber), isWhitelisted = false)
            return decision(event, Decision.BLOCK, risk, ReputationLevel.SUSPICIOUS, listOf("Matched blacklist rule: ${top.ruleType} ${top.pattern ?: top.countryIso}"), matchedBlacklist.map { toCallRule(it) }, "blacklist")
        }

        // 5. Legacy exact blocked_numbers fallback (existing feature preserved)
        if (isLegacyBlocked(event.phoneNumber)) {
            return decision(event, Decision.BLOCK, 60, ReputationLevel.SUSPICIOUS, listOf("Legacy blacklist exact match"), emptyList(), "legacy_blacklist")
        }

        // 6. AppSettings fallback (blockUnknown/blockPrivate/blockAllExcept)
        val settings = try { db.appSettingsDao().get() } catch (_: Exception) { null }
        if (settings != null) {
            if (settings.blockAllExceptWhitelist) {
                // already checked whitelist, so block
                return decision(event, Decision.BLOCK, 85, ReputationLevel.SUSPICIOUS, listOf("Block all except whitelist"), emptyList(), "policy")
            }
            if (settings.blockPrivate && event.phoneNumber.presentation == Presentation.RESTRICTED) {
                return decision(event, Decision.BLOCK, 70, ReputationLevel.SUSPICIOUS, listOf("Private/hidden policy"), emptyList(), "private")
            }
            if (settings.blockUnknown && event.phoneNumber.presentation == Presentation.UNKNOWN) {
                return decision(event, Decision.BLOCK, 50, ReputationLevel.NEUTRAL, listOf("Unknown caller policy"), emptyList(), "unknown")
            }
            if (settings.blockUnknown && event.contact?.isInContacts == false && event.phoneNumber.presentation == Presentation.ALLOWED) {
                return decision(event, Decision.BLOCK, 55, ReputationLevel.NEUTRAL, listOf("Not in contacts"), emptyList(), "unknown")
            }
        }

        // 7. Behavior / Reputation / Risk (if no hard rule)
        val normalized = event.phoneNumber.normalized
        val repEntity = try { db.callerReputationDao().find(normalized) } catch (_: Exception) { null }
        val repLevel = try { ReputationLevel.valueOf(repEntity?.level ?: "NEUTRAL") } catch (_: Exception) { ReputationLevel.NEUTRAL }
        val signals = try { behaviorEngine.signalsFor(event.phoneNumber) } catch (_: Exception) { BehaviorSignals() }
        val risk = riskEngine.score(event, repEntity?.let { CallerReputation(it.normalizedNumber, totalCalls = it.totalCalls, blockedCalls = it.blockedCalls, level = repLevel) }, signals, isSuspiciousPrefix(event.phoneNumber), isWhitelisted)
        // Example threshold: only block if policy says so. Default: HIGH_RISK (>=80) blocks via security rule
        if (risk >= 80) {
            return decision(event, Decision.BLOCK, risk, repLevel, listOf("High risk score $risk", "Signals: burst=${signals.isBurst} repeated=${signals.repeatedCount}"), emptyList(), "risk")
        }
        if (signals.isBurst && risk >= 60) {
            return decision(event, Decision.BLOCK, risk, repLevel, listOf("Suspicious burst ${signals.callsLast10Minutes}/10m"), emptyList(), "behavior")
        }

        // Default allow
        return decision(event, Decision.ALLOW, risk, repLevel, listOf("No matching block rule"), emptyList(), "default_allow")
    }

    private fun decision(event: CallEvent, dec: Decision, risk: Int, rep: ReputationLevel, reasons: List<String>, rules: List<CallRule>, backendHint: String): EnforcementDecision {
        val level = riskLevel(risk)
        return EnforcementDecision(
            callEvent = event,
            decision = dec,
            riskScore = risk,
            reputation = rep,
            reasons = reasons,
            matchedRules = rules,
            backend = EnforcementBackendType.CALL_SCREENING,
            verification = VerificationStatus.UNKNOWN,
            explainable = ExplainableDecision(
                summary = "${dec.name} - $backendHint - Risk ${risk}/100 (${level.name})",
                riskLevel = level,
                details = reasons,
                matchedRuleIds = rules.map { it.id },
                backend = backendHint,
                verification = "UNKNOWN"
            )
        )
    }

    private fun toCallRule(e: com.blacklist.app.data.local.entity.BlacklistRuleEntity): CallRule {
        return CallRule(id = e.id, priority = e.priority, action = RuleAction.BLOCK, conditions = emptyList(), description = "${e.ruleType}:${e.pattern}")
    }

    private suspend fun isWhitelisted(num: PhoneNumber): Boolean {
        val all = try { db.whitelistedNumberDao().getAll() } catch (_: Exception) { emptyList() }
        return all.any { w ->
            val wNum = normalizer.normalize(w.normalizedNumber)
            normalizer.matches(num, wNum)
        }
    }

    private suspend fun isLegacyBlocked(num: PhoneNumber): Boolean {
        val all = try { db.blockedNumberDao().getAll() } catch (_: Exception) { emptyList() }
        return all.any { b ->
            val bNum = normalizer.normalize(b.normalizedNumber)
            normalizer.matches(num, bNum)
        }
    }

    private suspend fun activeScheduleRule(): com.blacklist.app.data.local.entity.ScheduleRuleEntity? {
        val rules = try { db.scheduleRuleDao().getEnabled() } catch (_: Exception) { emptyList() }
        return ScheduleEvaluator.matchingRule(rules)
    }

    private suspend fun evaluateSchedule(event: CallEvent, rule: com.blacklist.app.data.local.entity.ScheduleRuleEntity): EnforcementDecision? {
        return when (rule.mode) {
            com.blacklist.app.data.local.entity.ScheduleRuleEntity.MODE_ALL -> decision(event, Decision.BLOCK, 90, ReputationLevel.NEUTRAL, listOf("Schedule: Block All"), emptyList(), "schedule")
            com.blacklist.app.data.local.entity.ScheduleRuleEntity.MODE_ALL_EXCEPT_WHITELIST -> {
                if (isWhitelisted(event.phoneNumber)) null else decision(event, Decision.BLOCK, 85, ReputationLevel.NEUTRAL, listOf("Schedule: All except whitelist"), emptyList(), "schedule")
            }
            com.blacklist.app.data.local.entity.ScheduleRuleEntity.MODE_UNKNOWN_PRIVATE -> {
                val isUnknown = event.phoneNumber.presentation != Presentation.ALLOWED || event.contact?.isInContacts == false
                if (isUnknown || event.phoneNumber.presentation == Presentation.RESTRICTED) decision(event, Decision.BLOCK, 60, ReputationLevel.NEUTRAL, listOf("Schedule: Unknown/Private"), emptyList(), "schedule") else null
            }
            com.blacklist.app.data.local.entity.ScheduleRuleEntity.MODE_BLACKLIST -> {
                val matched = blacklistEngine.findMatching(event.phoneNumber, try { db.blacklistRuleDao().getEnabled() } catch (_: Exception) { emptyList() })
                if (matched.isNotEmpty()) decision(event, Decision.BLOCK, 70, ReputationLevel.SUSPICIOUS, listOf("Schedule: Blacklist rule"), matched.map { toCallRule(it) }, "schedule") else null
            }
            else -> null
        }
    }

    private fun isSuspiciousPrefix(num: PhoneNumber): Boolean {
        // Example: suspicious prefixes list (could be configurable, for now static)
        val suspicious = listOf("+216", "+212", "+234", "+92") // example
        return suspicious.any { num.normalized.startsWith(it) }
    }
}
