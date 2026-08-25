package com.blacklist.app.domain.engine

import com.blacklist.app.data.local.entity.AppSettingsEntity
import com.blacklist.app.data.local.entity.BlacklistRuleEntity
import com.blacklist.app.domain.model.CallEvent
import com.blacklist.app.domain.model.Decision
import com.blacklist.app.domain.normalization.PhoneNumberNormalizer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CallFirewallEngineTest {
    private val normalizer = PhoneNumberNormalizer("DE")

    @Test
    fun `emergency number is allowed even when an exact blacklist rule exists`() = runTest {
        val number = normalizer.normalize("112")
        val snapshot = PolicySnapshotStore.Snapshot(
            rules = listOf(
                BlacklistRuleEntity(
                    id = 4,
                    ruleType = BlacklistRuleEntity.TYPE_EXACT,
                    pattern = number.normalized
                )
            ),
            settings = AppSettingsEntity(blockAllExceptWhitelist = true)
        )

        val decision = engine(snapshot).evaluate(event("emergency", number.raw))

        assertEquals(Decision.ALLOW, decision.decision)
        assertEquals("emergency", decision.explainable.backend)
        assertTrue(decision.reasons.single().contains("Emergency"))
    }

    @Test
    fun `temporary allow overrides temporary firewall and a blacklist rule`() = runTest {
        val number = normalizer.normalize("+49 151 23456789")
        val snapshot = PolicySnapshotStore.Snapshot(
            rules = listOf(
                BlacklistRuleEntity(
                    id = 1,
                    priority = 1,
                    ruleType = BlacklistRuleEntity.TYPE_EXACT,
                    pattern = number.normalized
                ),
                BlacklistRuleEntity(
                    id = 2,
                    priority = 0,
                    ruleType = BlacklistRuleEntity.TYPE_TEMP_BLOCK_ALL,
                    pattern = (System.currentTimeMillis() + 60_000).toString()
                ),
                BlacklistRuleEntity(
                    id = 3,
                    priority = 0,
                    ruleType = BlacklistRuleEntity.TYPE_TEMP_ALLOW,
                    pattern = number.digitsOnly,
                    startNumber = (System.currentTimeMillis() + 60_000).toString()
                )
            )
        )

        val decision = engine(snapshot).evaluate(event("temporary-allow", number.raw))

        assertEquals(Decision.ALLOW, decision.decision)
        assertEquals("temporary_allow", decision.explainable.backend)
    }

    @Test
    fun `whitelist overrides broad block all except whitelist setting`() = runTest {
        val number = normalizer.normalize("+49 151 23456789")
        val snapshot = PolicySnapshotStore.Snapshot(
            whitelist = listOf(number),
            settings = AppSettingsEntity(blockAllExceptWhitelist = true)
        )

        val decision = engine(snapshot).evaluate(event("whitelist", number.raw))

        assertEquals(Decision.ALLOW, decision.decision)
        assertEquals("whitelist", decision.explainable.backend)
    }

    @Test
    fun `unknown-contact policy fails open when contacts permission is unavailable`() = runTest {
        val snapshot = PolicySnapshotStore.Snapshot(
            canReadContacts = false,
            settings = AppSettingsEntity(blockUnknown = true)
        )

        val decision = engine(snapshot).evaluate(event("no-contact-permission", "+49 151 23456789"))

        assertEquals(Decision.ALLOW, decision.decision)
        assertEquals("default_allow", decision.explainable.backend)
    }

    @Test
    fun `unknown-contact policy blocks unlisted normal number only when contacts are available`() = runTest {
        val snapshot = PolicySnapshotStore.Snapshot(
            canReadContacts = true,
            settings = AppSettingsEntity(blockUnknown = true)
        )

        val decision = engine(snapshot).evaluate(event("contact-permission", "+49 151 23456789"))

        assertEquals(Decision.BLOCK, decision.decision)
        assertEquals("unknown", decision.explainable.backend)
        assertTrue(decision.reasons.single().contains("Not in contacts"))
    }

    private fun engine(snapshot: PolicySnapshotStore.Snapshot): CallFirewallEngine = CallFirewallEngine(
        policySnapshots = PolicySnapshotProvider { snapshot },
        normalizer = normalizer,
        blacklistEngine = BlacklistEngine(normalizer),
        riskEngine = RiskScoringEngine(),
        behaviorEngine = BehaviorEngine()
    )

    private fun event(id: String, number: String): CallEvent = CallEvent(
        callId = id,
        phoneNumber = normalizer.normalize(number)
    )
}
