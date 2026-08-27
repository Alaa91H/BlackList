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
        val trace = DecisionTraceInterpreter.forDecision(decision)
        assertEquals(DecisionTraceInterpreter.Stage.PERSISTENT_BLACKLIST, trace.decisiveStage)
        assertEquals(
            DecisionTraceInterpreter.State.DECISIVE,
            trace.entries.single { it.stage == DecisionTraceInterpreter.Stage.PERSISTENT_BLACKLIST }.state
        )
        assertEquals(
            DecisionTraceInterpreter.State.NOT_REACHED,
            trace.entries.single { it.stage == DecisionTraceInterpreter.Stage.LEGACY_BLACKLIST }.state
        )
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
        val trace = DecisionTraceInterpreter.forDecision(decision)
        assertEquals(DecisionTraceInterpreter.Stage.WHITELIST, trace.decisiveStage)
        assertEquals(
            DecisionTraceInterpreter.State.PASSED,
            trace.entries.single { it.stage == DecisionTraceInterpreter.Stage.TEMPORARY_ALLOW }.state
        )
        assertEquals(
            DecisionTraceInterpreter.State.NOT_REACHED,
            trace.entries.single { it.stage == DecisionTraceInterpreter.Stage.PERSISTENT_BLACKLIST }.state
        )
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
        val trace = DecisionTraceInterpreter.forDecision(decision)
        assertEquals(DecisionTraceInterpreter.Stage.EMERGENCY, trace.decisiveStage)
        assertTrue(trace.entries.drop(1).all { it.state == DecisionTraceInterpreter.State.NOT_REACHED })
    }

    private fun previewer(snapshot: PolicySnapshotStore.Snapshot): DraftRuleDecisionPreviewer =
        DraftRuleDecisionPreviewer(
            policySnapshots = PolicySnapshotProvider { snapshot },
            normalizer = normalizer,
            blacklistEngine = BlacklistEngine(normalizer),
            riskEngine = RiskScoringEngine()
        )
}
