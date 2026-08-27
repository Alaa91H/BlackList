package com.blacklist.app.domain.engine

import com.blacklist.app.data.local.entity.AppSettingsEntity
import com.blacklist.app.data.local.entity.BlacklistRuleEntity
import com.blacklist.app.data.local.entity.CallerReputationEntity
import com.blacklist.app.domain.model.CallEvent
import com.blacklist.app.domain.model.Decision
import com.blacklist.app.domain.model.Presentation
import com.blacklist.app.domain.normalization.PhoneNumberNormalizer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineReputationCallFirewallEngineTest {
    private val normalizer = PhoneNumberNormalizer(defaultRegion = "DE")

    @Test
    fun `high offline score blocks exact matching number`() = runBlocking {
        val decision = evaluate(snapshotWithSignal("+4930123456", 90))

        assertEquals(Decision.BLOCK, decision.decision)
        assertEquals(90, decision.riskScore)
        assertEquals("offline_reputation", decision.explainable.backend)
        assertTrue(decision.reasons.any { it.startsWith("Offline reputation: Example Research Group") })
    }

    @Test
    fun `lower offline score remains informational and does not block directly`() = runBlocking {
        val decision = evaluate(snapshotWithSignal("+4930123456", 79))

        assertEquals(Decision.ALLOW, decision.decision)
        assertEquals(79, decision.riskScore)
    }

    @Test
    fun `offline entry does not match a different exact number`() = runBlocking {
        val decision = evaluate(snapshotWithSignal("+4930123456", 100), rawNumber = "+4930123457")

        assertEquals(Decision.ALLOW, decision.decision)
        assertEquals(0, decision.riskScore)
    }

    @Test
    fun `temporary exact block blocks its canonical number before permanent and risk paths`() = runBlocking {
        val snapshot = PolicySnapshotStore.Snapshot(
            rules = listOf(
                BlacklistRuleEntity(
                    ruleType = BlacklistRuleEntity.TYPE_TEMP_BLOCK_EXACT,
                    pattern = "4930123456",
                    startNumber = (System.currentTimeMillis() + 60_000).toString()
                )
            )
        )

        val decision = evaluate(snapshot)

        assertEquals(Decision.BLOCK, decision.decision)
        assertEquals("temporary_block_exact", decision.explainable.backend)
    }

    @Test
    fun `temporary allow remains ahead of temporary exact block`() = runBlocking {
        val expiry = (System.currentTimeMillis() + 60_000).toString()
        val snapshot = PolicySnapshotStore.Snapshot(
            rules = listOf(
                BlacklistRuleEntity(
                    ruleType = BlacklistRuleEntity.TYPE_TEMP_ALLOW,
                    pattern = "4930123456",
                    startNumber = expiry
                ),
                BlacklistRuleEntity(
                    ruleType = BlacklistRuleEntity.TYPE_TEMP_BLOCK_EXACT,
                    pattern = "4930123456",
                    startNumber = expiry
                )
            )
        )

        val decision = evaluate(snapshot)

        assertEquals(Decision.ALLOW, decision.decision)
        assertEquals("temporary_allow", decision.explainable.backend)
    }

    @Test
    fun `whitelist remains ahead of temporary exact block`() = runBlocking {
        val phone = normalizer.normalize("+4930123456")
        val snapshot = PolicySnapshotStore.Snapshot(
            rules = listOf(
                BlacklistRuleEntity(
                    ruleType = BlacklistRuleEntity.TYPE_TEMP_BLOCK_EXACT,
                    pattern = "4930123456",
                    startNumber = (System.currentTimeMillis() + 60_000).toString()
                )
            ),
            whitelist = listOf(phone)
        )

        val decision = evaluate(snapshot)

        assertEquals(Decision.ALLOW, decision.decision)
        assertEquals("whitelist", decision.explainable.backend)
    }

    @Test
    fun `explicit blacklist remains authoritative over offline reputation`() = runBlocking {
        val snapshot = snapshotWithSignal("+4930123456", 10).copy(
            rules = listOf(BlacklistRuleEntity(ruleType = BlacklistRuleEntity.TYPE_EXACT, pattern = "+4930123456"))
        )

        val decision = evaluate(snapshot)

        assertEquals(Decision.BLOCK, decision.decision)
        assertEquals("blacklist", decision.explainable.backend)
    }

    @Test
    fun `whitelist remains authoritative over high offline reputation`() = runBlocking {
        val phone = normalizer.normalize("+4930123456")
        val snapshot = snapshotWithSignal("+4930123456", 100).copy(whitelist = listOf(phone))

        val decision = evaluate(snapshot)

        assertEquals(Decision.ALLOW, decision.decision)
        assertEquals("whitelist", decision.explainable.backend)
    }

    @Test
    fun `emergency protection remains authoritative`() = runBlocking {
        val snapshot = snapshotWithSignal("+49112", 100)

        val decision = evaluate(snapshot, rawNumber = "112")

        assertEquals(Decision.ALLOW, decision.decision)
        assertEquals("emergency", decision.explainable.backend)
    }

    @Test
    fun `trusted and not spam local verdicts override imported score`() = runBlocking {
        listOf("TRUSTED", "NOT_SPAM").forEach { verdict ->
            val snapshot = snapshotWithSignal("+4930123456", 100).copy(
                reputations = mapOf(
                    "+4930123456" to CallerReputationEntity(
                        normalizedNumber = "+4930123456",
                        userVerdict = verdict,
                        level = "TRUSTED"
                    )
                )
            )

            val decision = evaluate(snapshot)

            assertEquals(Decision.ALLOW, decision.decision)
            assertEquals(0, decision.riskScore)
        }
    }

    @Test
    fun `unknown callers can be silenced only after explicit opt in`() = runBlocking {
        val decision = evaluate(
            PolicySnapshotStore.Snapshot(
                settings = AppSettingsEntity(blockUnknown = true, silenceUnknown = true)
            ),
            presentation = Presentation.UNKNOWN
        )

        assertEquals(Decision.SILENCE, decision.decision)
        assertEquals("unknown_silence", decision.explainable.backend)
    }

    @Test
    fun `unknown callers remain rejected by default`() = runBlocking {
        val decision = evaluate(
            PolicySnapshotStore.Snapshot(settings = AppSettingsEntity(blockUnknown = true)),
            presentation = Presentation.UNKNOWN
        )

        assertEquals(Decision.BLOCK, decision.decision)
        assertEquals("unknown", decision.explainable.backend)
    }

    @Test
    fun `unknown silence never activates without its parent policy`() = runBlocking {
        val decision = evaluate(
            PolicySnapshotStore.Snapshot(
                settings = AppSettingsEntity(blockUnknown = false, silenceUnknown = true)
            ),
            presentation = Presentation.UNKNOWN
        )

        assertEquals(Decision.ALLOW, decision.decision)
    }

    @Test
    fun `private callers can be silenced only after explicit opt in`() = runBlocking {
        val decision = evaluate(
            PolicySnapshotStore.Snapshot(
                settings = AppSettingsEntity(blockPrivate = true, silencePrivate = true)
            ),
            presentation = Presentation.RESTRICTED
        )

        assertEquals(Decision.SILENCE, decision.decision)
        assertEquals("private_silence", decision.explainable.backend)
    }

    @Test
    fun `explicit blacklist remains ahead of silent unknown policy`() = runBlocking {
        val blacklistDecision = evaluate(
            PolicySnapshotStore.Snapshot(
                settings = AppSettingsEntity(blockUnknown = true, silenceUnknown = true),
                rules = listOf(BlacklistRuleEntity(ruleType = BlacklistRuleEntity.TYPE_EXACT, pattern = "+4930123456"))
            )
        )

        assertEquals(Decision.BLOCK, blacklistDecision.decision)
        assertEquals("blacklist", blacklistDecision.explainable.backend)
    }

    private suspend fun evaluate(
        snapshot: PolicySnapshotStore.Snapshot,
        rawNumber: String = "+4930123456",
        presentation: Presentation = Presentation.ALLOWED
    ) = CallFirewallEngine(
        policySnapshots = PolicySnapshotProvider { snapshot },
        normalizer = normalizer,
        blacklistEngine = BlacklistEngine(normalizer),
        riskEngine = RiskScoringEngine(),
        behaviorEngine = BehaviorEngine()
    ).evaluate(
        CallEvent(
            callId = "test",
            phoneNumber = normalizer.normalize(rawNumber).copy(presentation = presentation)
        )
    )

    private fun snapshotWithSignal(number: String, score: Int) = PolicySnapshotStore.Snapshot(
        settings = AppSettingsEntity(),
        offlineReputations = mapOf(
            number to OfflineReputationSignal(
                riskScore = score,
                sources = listOf("Example Research Group"),
                categories = listOf("telemarketing")
            )
        )
    )
}
