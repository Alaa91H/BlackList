package com.blacklist.app.domain.engine

import com.blacklist.app.data.local.entity.BlacklistRuleEntity
import com.blacklist.app.domain.model.CallEvent
import com.blacklist.app.domain.model.CallSource
import com.blacklist.app.domain.model.EnforcementDecision
import com.blacklist.app.domain.model.Presentation
import com.blacklist.app.domain.normalization.PhoneNumberNormalizer

/**
 * Evaluates a candidate persistent rule against a temporary in-memory policy.
 *
 * This is deliberately separate from [CallScreeningService]: it does not call
 * Telecom, write a database row, emit a log, or retain the supplied number.
 * A fresh [BehaviorEngine] ensures an editor test cannot influence live
 * behavioural signals used by future incoming calls.
 */
class DraftRuleDecisionPreviewer(
    private val policySnapshots: PolicySnapshotProvider,
    private val normalizer: PhoneNumberNormalizer,
    private val blacklistEngine: BlacklistEngine,
    private val riskEngine: RiskScoringEngine
) {
    suspend fun preview(rawNumber: String, draft: BlacklistRuleEntity): EnforcementDecision {
        val input = rawNumber.trim()
        require(input.isNotEmpty() && input.length <= MAX_TEST_INPUT_LENGTH) { "Enter a valid test number" }

        val number = normalizer.normalize(input)
        require(number.presentation == Presentation.ALLOWED && number.digitsOnly.length >= MIN_TEST_DIGITS) {
            "Enter a valid test number"
        }

        val current = policySnapshots.snapshot()
        val temporarySnapshot = current.copy(rules = current.rules + draft)
        val previewEngine = CallFirewallEngine(
            policySnapshots = PolicySnapshotProvider { temporarySnapshot },
            normalizer = normalizer,
            blacklistEngine = blacklistEngine,
            riskEngine = RiskScoringEngine(riskEngine.config),
            behaviorEngine = BehaviorEngine()
        )
        return previewEngine.evaluate(
            CallEvent(
                callId = DRAFT_PREVIEW_CALL_ID,
                phoneNumber = number,
                source = CallSource.TEST
            )
        )
    }

    private companion object {
        const val MAX_TEST_INPUT_LENGTH = 64
        const val MIN_TEST_DIGITS = 3
        const val DRAFT_PREVIEW_CALL_ID = "draft-rule-preview"
    }
}
