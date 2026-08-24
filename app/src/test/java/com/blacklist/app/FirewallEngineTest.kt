package com.blacklist.app

import com.blacklist.app.data.local.entity.BlacklistRuleEntity
import com.blacklist.app.domain.engine.BehaviorEngine
import com.blacklist.app.domain.engine.BlacklistEngine
import com.blacklist.app.domain.engine.RiskScoringEngine
import com.blacklist.app.domain.model.*
import com.blacklist.app.domain.normalization.PhoneNumberNormalizer
import com.blacklist.app.util.PhoneNumberUtils
import org.junit.Assert.*
import org.junit.Test

class FirewallEngineTest {

    private val normalizer = PhoneNumberNormalizer("DE")
    private val blacklistEngine = BlacklistEngine(normalizer)
    private val riskEngine = RiskScoringEngine()

    @Test
    fun testNormalizationE164() {
        val a = normalizer.normalize("+49123456789")
        assertEquals("+49123456789", a.e164)
        assertEquals("DE", a.countryIso)
        val b = normalizer.normalize("0049123456789")
        assertEquals(a.e164, b.e164)
        assertTrue(normalizer.isSameNumber("+49123456789", "0049123456789"))
    }

    @Test
    fun testNormalizationLocalFallback() {
        val a = normalizer.normalize("(030) 1234-5678")
        assertNotNull(a.normalized)
        assertTrue(a.digitsOnly.length >= 7)
    }

    @Test
    fun testPrefixMatching() {
        val event = normalizer.normalize("+49301234567")
        val rule = BlacklistRuleEntity(ruleType = BlacklistRuleEntity.TYPE_PREFIX, pattern = "+4930", priority = 30)
        assertTrue(blacklistEngine.matches(event, rule))
        val noMatch = BlacklistRuleEntity(ruleType = BlacklistRuleEntity.TYPE_PREFIX, pattern = "+4910", priority = 30)
        assertFalse(blacklistEngine.matches(event, noMatch))
    }

    @Test
    fun testSuffixMatching() {
        val event = normalizer.normalize("+49301234567")
        val rule = BlacklistRuleEntity(ruleType = BlacklistRuleEntity.TYPE_SUFFIX, pattern = "4567", priority = 30)
        assertTrue(blacklistEngine.matches(event, rule))
        val noMatch = BlacklistRuleEntity(ruleType = BlacklistRuleEntity.TYPE_SUFFIX, pattern = "9999", priority = 30)
        assertFalse(blacklistEngine.matches(event, noMatch))
    }

    @Test
    fun testContainsMatching() {
        val event = normalizer.normalize("+49301234567")
        val rule = BlacklistRuleEntity(ruleType = BlacklistRuleEntity.TYPE_CONTAINS, pattern = "0123", priority = 30)
        assertTrue(blacklistEngine.matches(event, rule))
        val midRule = BlacklistRuleEntity(ruleType = BlacklistRuleEntity.TYPE_CONTAINS, pattern = "2345", priority = 30)
        assertTrue(blacklistEngine.matches(event, midRule))
        val noMatch = BlacklistRuleEntity(ruleType = BlacklistRuleEntity.TYPE_CONTAINS, pattern = "7777", priority = 30)
        assertFalse(blacklistEngine.matches(event, noMatch))
    }

    @Test
    fun testSuffixContainsIgnoreFormatting() {
        // Formatting characters in the rule pattern must be ignored
        val event = normalizer.normalize("+4930 1234-567")
        val suffixRule = BlacklistRuleEntity(ruleType = BlacklistRuleEntity.TYPE_SUFFIX, pattern = "+1234-567 ", priority = 30)
        assertTrue(blacklistEngine.matches(event, suffixRule))
        val containsRule = BlacklistRuleEntity(ruleType = BlacklistRuleEntity.TYPE_CONTAINS, pattern = "30-12 34", priority = 30)
        assertTrue(blacklistEngine.matches(event, containsRule))
    }

    @Test
    fun testBlankPatternNeverMatches() {
        val event = normalizer.normalize("+49301234567")
        assertFalse(blacklistEngine.matches(event, BlacklistRuleEntity(ruleType = BlacklistRuleEntity.TYPE_SUFFIX, pattern = "", priority = 30)))
        assertFalse(blacklistEngine.matches(event, BlacklistRuleEntity(ruleType = BlacklistRuleEntity.TYPE_CONTAINS, pattern = null, priority = 30)))
        assertFalse(blacklistEngine.matches(event, BlacklistRuleEntity(ruleType = BlacklistRuleEntity.TYPE_PREFIX, pattern = "", priority = 30)))
    }

    @Test
    fun testRangeMatching() {
        val event = normalizer.normalize("+49301234567")
        val rule = BlacklistRuleEntity(ruleType = BlacklistRuleEntity.TYPE_RANGE, startNumber = "+49300000000", endNumber = "+49309999999", priority = 30)
        assertTrue(blacklistEngine.matches(event, rule))
        val out = BlacklistRuleEntity(ruleType = BlacklistRuleEntity.TYPE_RANGE, startNumber = "+49400000000", endNumber = "+49499999999", priority = 30)
        assertFalse(blacklistEngine.matches(event, out))
    }

    @Test
    fun testCountryMatching() {
        val event = normalizer.normalize("+49123456789")
        val rule = BlacklistRuleEntity(ruleType = BlacklistRuleEntity.TYPE_COUNTRY, countryIso = "DE", priority = 30)
        assertTrue(blacklistEngine.matches(event, rule))
        val usRule = BlacklistRuleEntity(ruleType = BlacklistRuleEntity.TYPE_COUNTRY, countryIso = "US", priority = 30)
        assertFalse(blacklistEngine.matches(event, usRule))
    }

    @Test
    fun testHiddenUnknown() {
        val hidden = normalizer.normalize("private")
        assertEquals(Presentation.RESTRICTED, hidden.presentation)
        val ruleHidden = BlacklistRuleEntity(ruleType = BlacklistRuleEntity.TYPE_HIDDEN, priority = 30)
        assertTrue(blacklistEngine.matches(hidden, ruleHidden))
        val unknown = normalizer.normalize(null)
        assertEquals(Presentation.UNKNOWN, unknown.presentation)
        val ruleUnknown = BlacklistRuleEntity(ruleType = BlacklistRuleEntity.TYPE_UNKNOWN, priority = 30)
        assertTrue(blacklistEngine.matches(unknown, ruleUnknown))
    }

    @Test
    fun testRiskScoring() {
        val event = CallEvent(
            callId = "1",
            phoneNumber = normalizer.normalize("+49301234567"),
            contact = CallerContact(null, false)
        )
        val scoreUnknown = riskEngine.score(event.copy(phoneNumber = normalizer.normalize("unknown")), null)
        assertTrue(scoreUnknown >= 10)
        val trustedEvent = event.copy(contact = CallerContact("John", true, isStarred = true))
        val scoreTrusted = riskEngine.score(trustedEvent, null)
        assertTrue(scoreTrusted < 30)
        val whitelistScore = riskEngine.score(event, null, isWhitelisted = true)
        assertEquals(0, whitelistScore)
    }

    @Test
    fun testReputationLevels() {
        val rep = com.blacklist.app.domain.model.CallerReputation("49123456789", blockedCalls = 5, allowedCalls = 0, riskScore = 85)
        rep.updateLevel()
        assertEquals(ReputationLevel.MALICIOUS, rep.level)
    }

    @Test
    fun testPhoneNumberUtilsMatches() {
        assertTrue(PhoneNumberUtils.matches("+49123456789", "0049123456789"))
        assertTrue(PhoneNumberUtils.matches("030 12345678", "+493012345678"))
        assertFalse(PhoneNumberUtils.matches("+49123456789", "+49876543210"))
    }

    @Test
    fun testRiskHighBlocks() {
        val event = CallEvent(callId = "x", phoneNumber = normalizer.normalize("+49301234567"), contact = CallerContact(null, false))
        val signals = com.blacklist.app.domain.engine.BehaviorSignals(isBurst = true, repeatedCount = 3)
        val score = riskEngine.score(event, CallerReputation("+49301234567", blockedCalls = 2, riskScore = 70, level = ReputationLevel.SUSPICIOUS), signals, isSuspiciousPrefix = true)
        assertTrue(score >= 80)
    }
}
