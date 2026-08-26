package com.blacklist.app.domain.engine

import com.blacklist.app.data.local.entity.AppSettingsEntity
import com.blacklist.app.data.local.entity.BlacklistRuleEntity
import com.blacklist.app.domain.model.CallEvent
import com.blacklist.app.domain.model.Decision
import com.blacklist.app.domain.model.VerificationStatus
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
    fun `regional emergency number is allowed even when a broad policy is enabled`() = runTest {
        val number = normalizer.normalize("110")
        val snapshot = PolicySnapshotStore.Snapshot(
            rules = listOf(
                BlacklistRuleEntity(
                    id = 5,
                    ruleType = BlacklistRuleEntity.TYPE_EXACT,
                    pattern = number.normalized
                )
            ),
            settings = AppSettingsEntity(blockAllExceptWhitelist = true)
        )

        val decision = engine(snapshot).evaluate(event("regional-emergency", number.raw))

        assertEquals(Decision.ALLOW, decision.decision)
        assertEquals("emergency", decision.explainable.backend)
    }

    @Test
    fun `failed caller verification contributes risk and remains explainable`() = runTest {
        val decision = engine(PolicySnapshotStore.Snapshot()).evaluate(
            event("failed-verification", "+49 151 23456789", VerificationStatus.FAILED)
        )

        assertEquals(25, decision.riskScore)
        assertEquals(VerificationStatus.FAILED, decision.verification)
        assertEquals("FAILED", decision.explainable.verification)
        assertTrue(decision.reasons.contains("No matching block rule"))
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
    fun `emergency callback grace allows a caller through broad blocking policies`() = runTest {
        val snapshot = PolicySnapshotStore.Snapshot(
            settings = AppSettingsEntity(
                blockAllExceptWhitelist = true,
                emergencyCallbackGraceUntil = System.currentTimeMillis() + EmergencyCallbackGrace.DURATION_MS
            )
        )

        val decision = engine(snapshot).evaluate(event("emergency-callback", "+49 151 23456789"))

        assertEquals(Decision.ALLOW, decision.decision)
        assertEquals("emergency_callback_grace", decision.explainable.backend)
    }

    @Test
    fun `emergency callback grace never overrides an explicit blacklist rule`() = runTest {
        val number = normalizer.normalize("+49 151 23456789")
        val snapshot = PolicySnapshotStore.Snapshot(
            rules = listOf(
                BlacklistRuleEntity(
                    id = 12,
                    ruleType = BlacklistRuleEntity.TYPE_EXACT,
                    pattern = number.normalized
                )
            ),
            settings = AppSettingsEntity(
                blockAllExceptWhitelist = true,
                emergencyCallbackGraceUntil = System.currentTimeMillis() + EmergencyCallbackGrace.DURATION_MS
            )
        )

        val decision = engine(snapshot).evaluate(event("explicit-blacklist", number.raw))

        assertEquals(Decision.BLOCK, decision.decision)
        assertEquals("blacklist", decision.explainable.backend)
    }

    @Test
    fun `expired emergency callback grace does not weaken blocking`() = runTest {
        val snapshot = PolicySnapshotStore.Snapshot(
            settings = AppSettingsEntity(
                blockAllExceptWhitelist = true,
                emergencyCallbackGraceUntil = System.currentTimeMillis() - 1
            )
        )

        val decision = engine(snapshot).evaluate(event("expired-emergency-callback", "+49 151 23456789"))

        assertEquals(Decision.BLOCK, decision.decision)
        assertEquals("policy", decision.explainable.backend)
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

    private fun event(
        id: String,
        number: String,
        verificationStatus: VerificationStatus = VerificationStatus.UNKNOWN
    ): CallEvent = CallEvent(
        callId = id,
        phoneNumber = normalizer.normalize(number),
        verificationStatus = verificationStatus
    )
}
