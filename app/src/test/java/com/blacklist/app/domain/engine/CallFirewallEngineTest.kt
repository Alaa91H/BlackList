package com.blacklist.app.domain.engine

import com.blacklist.app.data.local.entity.AppSettingsEntity
import com.blacklist.app.data.local.entity.BlacklistRuleEntity
import com.blacklist.app.domain.model.CallEvent
import com.blacklist.app.domain.model.Decision
import com.blacklist.app.domain.model.VerificationStatus
import com.blacklist.app.domain.normalization.PhoneNumberNormalizer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `silent exact blacklist rule mutes the ringtone and remains explainable`() = runTest {
        val number = normalizer.normalize("+49 151 23456789")
        val snapshot = PolicySnapshotStore.Snapshot(
            rules = listOf(
                BlacklistRuleEntity(
                    id = 6,
                    ruleType = BlacklistRuleEntity.TYPE_EXACT,
                    enforcement = BlacklistRuleEntity.ENFORCEMENT_SILENCE,
                    pattern = number.normalized
                )
            )
        )

        val decision = engine(snapshot).evaluate(event("silent-exact", number.raw))

        assertEquals(Decision.SILENCE, decision.decision)
        assertEquals("blacklist_silence", decision.explainable.backend)
        assertEquals(com.blacklist.app.domain.model.RuleAction.SILENCE, decision.matchedRules.single().action)
        assertTrue(decision.reasons.single().contains("silent blacklist rule"))
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
    fun `recent outgoing callback grace bypasses broad block policy for that exact number`() = runTest {
        val number = normalizer.normalize("+49 151 23456789")
        val snapshot = PolicySnapshotStore.Snapshot(
            rules = listOf(
                BlacklistRuleEntity(
                    ruleType = BlacklistRuleEntity.TYPE_TEMP_OUTBOUND_CALLBACK,
                    pattern = number.digitsOnly,
                    startNumber = (System.currentTimeMillis() + OutboundCallbackGrace.DURATION_MS).toString()
                )
            ),
            settings = AppSettingsEntity(
                blockAllExceptWhitelist = true,
                allowOutboundCallbackGrace = true
            )
        )

        val decision = engine(snapshot).evaluate(event("outbound-callback", number.raw))

        assertEquals(Decision.ALLOW, decision.decision)
        assertEquals("outbound_callback_grace", decision.explainable.backend)
    }

    @Test
    fun `recent outgoing callback grace never overrides explicit blacklist or another number`() = runTest {
        val number = normalizer.normalize("+49 151 23456789")
        val otherNumber = normalizer.normalize("+49 151 11111111")
        val grace = BlacklistRuleEntity(
            ruleType = BlacklistRuleEntity.TYPE_TEMP_OUTBOUND_CALLBACK,
            pattern = number.digitsOnly,
            startNumber = (System.currentTimeMillis() + OutboundCallbackGrace.DURATION_MS).toString()
        )
        val explicitBlock = BlacklistRuleEntity(
            ruleType = BlacklistRuleEntity.TYPE_EXACT,
            pattern = number.normalized
        )
        val snapshot = PolicySnapshotStore.Snapshot(
            rules = listOf(grace, explicitBlock),
            settings = AppSettingsEntity(
                blockAllExceptWhitelist = true,
                allowOutboundCallbackGrace = true
            )
        )

        val explicitlyBlocked = engine(snapshot).evaluate(event("outbound-callback-explicit", number.raw))
        val unmatchedNumber = engine(snapshot).evaluate(event("outbound-callback-other", otherNumber.raw))

        assertEquals(Decision.BLOCK, explicitlyBlocked.decision)
        assertEquals("blacklist", explicitlyBlocked.explainable.backend)
        assertEquals(Decision.BLOCK, unmatchedNumber.decision)
        assertEquals("policy", unmatchedNumber.explainable.backend)
    }

    @Test
    fun `disabled outgoing callback grace does not weaken broad blocking`() = runTest {
        val number = normalizer.normalize("+49 151 23456789")
        val snapshot = PolicySnapshotStore.Snapshot(
            rules = listOf(
                BlacklistRuleEntity(
                    ruleType = BlacklistRuleEntity.TYPE_TEMP_OUTBOUND_CALLBACK,
                    pattern = number.digitsOnly,
                    startNumber = (System.currentTimeMillis() + OutboundCallbackGrace.DURATION_MS).toString()
                )
            ),
            settings = AppSettingsEntity(
                blockAllExceptWhitelist = true,
                allowOutboundCallbackGrace = false
            )
        )

        val decision = engine(snapshot).evaluate(event("disabled-outbound-callback", number.raw))

        assertEquals(Decision.BLOCK, decision.decision)
        assertEquals("policy", decision.explainable.backend)
    }

    @Test
    fun `outgoing callback grace is disabled by default`() {
        assertFalse(AppSettingsEntity().allowOutboundCallbackGrace)
    }

    @Test
    fun `legacy global schedules are ignored after per-rule scheduling migration`() = runTest {
        val number = normalizer.normalize("+49 151 23456789")
        val snapshot = PolicySnapshotStore.Snapshot(
            rules = emptyList(),
            schedules = emptyList(),
            scheduleExceptions = emptyList()
        )

        val decision = engine(snapshot).evaluate(event("global-schedule-removed", number.raw))

        assertEquals(Decision.ALLOW, decision.decision)
        assertEquals("default_allow", decision.explainable.backend)
    }

    @Test
    fun `per-rule schedule still blocks only while the rule is active`() = runTest {
        val number = normalizer.normalize("+49 151 23456789")
        val rule = BlacklistRuleEntity(
            ruleType = BlacklistRuleEntity.TYPE_EXACT,
            pattern = number.normalized,
            scheduleEnabled = true,
            scheduleStartMinutes = 0,
            scheduleEndMinutes = 1439,
            scheduleDaysOfWeek = 127
        )
        val snapshot = PolicySnapshotStore.Snapshot(rules = listOf(rule))

        val decision = engine(snapshot).evaluate(event("per-rule-schedule", number.raw))

        assertEquals(Decision.BLOCK, decision.decision)
        assertEquals("blacklist", decision.explainable.backend)
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

    @Test
    fun `international policy blocks foreign caller but allows home region`() = runTest {
        val snapshot = PolicySnapshotStore.Snapshot(
            settings = AppSettingsEntity(blockInternational = true)
        )
        val foreign = engine(snapshot).evaluate(event("international-foreign", "+1 202 555 0100"))
        val local = engine(snapshot).evaluate(event("international-local", "+49 151 23456789"))

        assertEquals(Decision.BLOCK, foreign.decision)
        assertEquals("international", foreign.explainable.backend)
        assertEquals(Decision.ALLOW, local.decision)
    }

    @Test
    fun `international policy can silence foreign caller`() = runTest {
        val snapshot = PolicySnapshotStore.Snapshot(
            settings = AppSettingsEntity(blockInternational = true, silenceInternational = true)
        )

        val decision = engine(snapshot).evaluate(event("international-silence", "+1 202 555 0100"))

        assertEquals(Decision.SILENCE, decision.decision)
        assertEquals("international_silence", decision.explainable.backend)
    }

    @Test
    fun `first-time caller policy blocks an unsaved caller with no local history`() = runTest {
        val number = normalizer.normalize("+49 151 23456789")
        val snapshot = PolicySnapshotStore.Snapshot(
            canReadContacts = true,
            settings = AppSettingsEntity(firstTimeCallerPolicy = AppSettingsEntity.FIRST_TIME_BLOCK)
        )

        val decision = engine(snapshot).evaluate(event("first-time", number.raw))

        assertEquals(Decision.BLOCK, decision.decision)
        assertEquals("first_time", decision.explainable.backend)
    }

    @Test
    fun `first-time caller policy never overrides whitelist`() = runTest {
        val number = normalizer.normalize("+49 151 23456789")
        val snapshot = PolicySnapshotStore.Snapshot(
            canReadContacts = true,
            whitelist = listOf(number),
            settings = AppSettingsEntity(firstTimeCallerPolicy = AppSettingsEntity.FIRST_TIME_BLOCK)
        )

        val decision = engine(snapshot).evaluate(event("first-time-whitelist", number.raw))

        assertEquals(Decision.ALLOW, decision.decision)
        assertEquals("whitelist", decision.explainable.backend)
    }

    @Test
    fun `repeated caller policy applies on configured attempt threshold`() = runTest {
        val snapshot = PolicySnapshotStore.Snapshot(
            settings = AppSettingsEntity(
                repeatedCallerPolicy = AppSettingsEntity.REPEATED_SILENCE,
                repeatedCallerThreshold = 2,
                repeatedCallerWindowMinutes = 15
            )
        )
        val firewall = engine(snapshot)
        val first = firewall.evaluate(event("repeat-1", "+49 151 23456789"))
        val second = firewall.evaluate(event("repeat-2", "+49 151 23456789"))

        assertEquals(Decision.ALLOW, first.decision)
        assertEquals(Decision.SILENCE, second.decision)
        assertEquals("repeated_silence", second.explainable.backend)
    }

    @Test
    fun `repeated caller policy fails open when disabled`() = runTest {
        val snapshot = PolicySnapshotStore.Snapshot(
            settings = AppSettingsEntity(repeatedCallerThreshold = 2)
        )

        val firewall = engine(snapshot)
        val first = firewall.evaluate(event("repeat-off-1", "+49 151 23456789"))
        val second = firewall.evaluate(event("repeat-off-2", "+49 151 23456789"))

        assertEquals(Decision.ALLOW, first.decision)
        assertEquals(Decision.ALLOW, second.decision)
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
