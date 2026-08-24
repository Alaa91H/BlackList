package com.blacklist.app.domain.engine

import com.blacklist.app.domain.model.*

/**
 * Configurable risk scoring 0-100.
 * Factors are additive, trusted contacts heavily negative.
 */
class RiskScoringEngine(
    var config: RiskConfig = RiskConfig()
) {
    data class RiskConfig(
        val unknownCaller: Int = 10,
        val hiddenCaller: Int = 30,
        val repeatedCalls: Int = 20,
        val callBurst: Int = 30,
        val suspiciousPrefix: Int = 40,
        val failedVerification: Int = 25,
        val knownSpamReputation: Int = 40,
        val userBlockedBefore: Int = 50,
        val trustedContact: Int = -80,
        val whitelist: Int = -100
    )

    fun score(
        event: CallEvent,
        reputation: CallerReputation?,
        signals: BehaviorSignals = BehaviorSignals(),
        isSuspiciousPrefix: Boolean = false,
        isWhitelisted: Boolean = false
    ): Int {
        var s = 0
        val reasons = mutableListOf<String>()
        if (event.phoneNumber.presentation == Presentation.UNKNOWN) { s += config.unknownCaller; reasons.add("unknown") }
        if (event.phoneNumber.presentation == Presentation.RESTRICTED) { s += config.hiddenCaller; reasons.add("hidden") }
        if (signals.repeatedCount >= 2) { s += config.repeatedCalls; reasons.add("repeated") }
        if (signals.isBurst) { s += config.callBurst; reasons.add("burst") }
        if (isSuspiciousPrefix) { s += config.suspiciousPrefix; reasons.add("suspicious_prefix") }
        if (signals.verificationFailed) { s += config.failedVerification; reasons.add("verification_failed") }
        if (reputation != null) {
            when (reputation.level) {
                ReputationLevel.MALICIOUS -> { s += config.knownSpamReputation; reasons.add("spam_reputation") }
                ReputationLevel.SUSPICIOUS -> { s += 20; reasons.add("suspicious_reputation") }
                else -> {}
            }
            if (reputation.blockedCalls > 0) { s += config.userBlockedBefore; reasons.add("blocked_before") }
        }
        if (event.contact?.isInContacts == true) {
            if (event.contact.isStarred || event.contact.isVip) { s += config.trustedContact; reasons.add("trusted_starred") }
            else { s += -30; reasons.add("in_contacts") }
        }
        if (isWhitelisted) { s += config.whitelist; reasons.add("whitelist") }
        return s.coerceIn(0, 100)
    }
}

data class BehaviorSignals(
    val repeatedCount: Int = 0,
    val isBurst: Boolean = false,
    val verificationFailed: Boolean = false,
    val callsLast10Minutes: Int = 0
)
