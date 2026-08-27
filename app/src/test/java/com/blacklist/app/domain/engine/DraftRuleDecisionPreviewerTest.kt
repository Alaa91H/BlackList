package com.blacklist.app.domain.engine

import com.blacklist.app.data.local.entity.BlacklistRuleEntity
import com.blacklist.app.domain.model.Decision
import com.blacklist.app.domain.normalization.PhoneNumberNormalizer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftRuleDecisionPreviewerTest {
    private val normalizer = PhoneNumberNormalizer("DE")

    @Test
    fun `unsaved silent draft is evaluated without changing the source snapshot`() = runTest {
        val snapshot = PolicySnapshotStore.Snapshot()
        val draft = BlacklistRuleEntity(
            ruleType = BlacklistRuleEntity.TYPE_EXACT,
            enforcement = BlacklistRuleEntity.ENFORCEMENT_SILENCE,
            pattern = "+49 151 23456789"
        )

        val decision = previewer(snapshot).preview("+49 151 23456789", draft)

        assertEquals(Decision.SILENCE, decision.decision)
        assertEquals("blacklist_silence", decision.explainable.backend)
        assertTrue(snapshot.rules.isEmpty())
    }

    @Test
    fun `whitelist remains higher priority than an unsaved blocking draft`() = runTest {
        val number = normalizer.normalize("+49 151 23456789")
        val snapshot = PolicySnapshotStore.Snapshot(whitelist = listOf(number))
        val draft = BlacklistRuleEntity(
            ruleType = BlacklistRuleEntity.TYPE_EXACT,
            pattern = "+49 151 23456789"
        )

        val decision = previewer(snapshot).preview("+49 151 23456789", draft)

        assertEquals(Decision.ALLOW, decision.decision)
        assertEquals("whitelist", decision.explainable.backend)
    }

    @Test
    fun `emergency safeguard remains higher priority than an unsaved blocking draft`() = runTest {
        val snapshot = PolicySnapshotStore.Snapshot()
        val draft = BlacklistRuleEntity(
            ruleType = BlacklistRuleEntity.TYPE_PREFIX,
            pattern = "1"
        )

        val decision = previewer(snapshot).preview("112", draft)

        assertEquals(Decision.ALLOW, decision.decision)
        assertEquals("emergency", decision.explainable.backend)
    }

    private fun previewer(snapshot: PolicySnapshotStore.Snapshot): DraftRuleDecisionPreviewer =
        DraftRuleDecisionPreviewer(
            policySnapshots = PolicySnapshotProvider { snapshot },
            normalizer = normalizer,
            blacklistEngine = BlacklistEngine(normalizer),
            riskEngine = RiskScoringEngine()
        )
}
