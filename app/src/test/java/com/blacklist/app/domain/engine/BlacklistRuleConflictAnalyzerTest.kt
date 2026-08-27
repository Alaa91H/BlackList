package com.blacklist.app.domain.engine

import com.blacklist.app.data.local.entity.BlacklistRuleEntity
import com.blacklist.app.domain.normalization.PhoneNumberNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlacklistRuleConflictAnalyzerTest {
    private val analyzer = BlacklistRuleConflictAnalyzer(PhoneNumberNormalizer("DE"))

    @Test
    fun `equivalent exact rules are duplicates after regional normalization`() {
        val existing = BlacklistRuleEntity(
            id = 7,
            ruleType = BlacklistRuleEntity.TYPE_EXACT,
            pattern = "+49 151 23456789",
            createdAt = 100
        )
        val draft = BlacklistRuleEntity(
            ruleType = BlacklistRuleEntity.TYPE_EXACT,
            pattern = "+4915123456789",
            createdAt = 200
        )

        val preview = analyzer.analyze(draft, listOf(existing))

        assertTrue(preview.hasDuplicate)
        assertEquals(1, preview.conflicts.size)
        assertEquals(RuleConflictKind.DUPLICATE, preview.conflicts.single().kind)
        assertEquals(RuleConflictWinner.DRAFT, preview.conflicts.single().winner)
    }

    @Test
    fun `higher priority existing prefix wins over overlapping exact draft`() {
        val existing = BlacklistRuleEntity(
            id = 8,
            priority = 20,
            ruleType = BlacklistRuleEntity.TYPE_PREFIX,
            pattern = "+49151",
            createdAt = 100
        )
        val draft = BlacklistRuleEntity(
            priority = 30,
            ruleType = BlacklistRuleEntity.TYPE_EXACT,
            enforcement = BlacklistRuleEntity.ENFORCEMENT_SILENCE,
            pattern = "+49 151 23456789",
            createdAt = 200
        )

        val preview = analyzer.analyze(draft, listOf(existing))

        assertFalse(preview.hasDuplicate)
        assertEquals(RuleConflictKind.OVERLAP, preview.conflicts.single().kind)
        assertEquals(RuleConflictWinner.EXISTING, preview.conflicts.single().winner)
    }

    @Test
    fun `matching order breaks an equal priority tie with the newer rule`() {
        val number = PhoneNumberNormalizer("DE").normalize("+49 151 23456789")
        val older = BlacklistRuleEntity(
            id = 11,
            priority = 30,
            ruleType = BlacklistRuleEntity.TYPE_PREFIX,
            pattern = "+49151",
            createdAt = 100
        )
        val newer = BlacklistRuleEntity(
            id = 12,
            priority = 30,
            ruleType = BlacklistRuleEntity.TYPE_EXACT,
            pattern = "+49 151 23456789",
            createdAt = 200
        )

        val matches = BlacklistEngine(PhoneNumberNormalizer("DE")).findMatching(number, listOf(older, newer))

        assertEquals(newer.id, matches.first().id)
    }

    @Test
    fun `disabled rules do not participate in the draft preview`() {
        val disabled = BlacklistRuleEntity(
            id = 9,
            isEnabled = false,
            ruleType = BlacklistRuleEntity.TYPE_PREFIX,
            pattern = "+49151"
        )
        val draft = BlacklistRuleEntity(
            ruleType = BlacklistRuleEntity.TYPE_EXACT,
            pattern = "+49 151 23456789"
        )

        assertTrue(analyzer.analyze(draft, listOf(disabled)).conflicts.isEmpty())
    }

    @Test
    fun `bounded preview reports when lower priority active rules were not inspected`() {
        val first = BlacklistRuleEntity(
            id = 13,
            priority = 10,
            ruleType = BlacklistRuleEntity.TYPE_PREFIX,
            pattern = "+49151"
        )
        val second = BlacklistRuleEntity(
            id = 14,
            priority = 20,
            ruleType = BlacklistRuleEntity.TYPE_PREFIX,
            pattern = "+49152"
        )
        val draft = BlacklistRuleEntity(
            ruleType = BlacklistRuleEntity.TYPE_EXACT,
            pattern = "+49 151 23456789"
        )

        val preview = analyzer.analyze(draft, listOf(first, second), maxExistingRules = 1)

        assertEquals(1, preview.inspectedRuleCount)
        assertEquals(2, preview.activeRuleCount)
        assertTrue(preview.isTruncated)
        assertEquals(1, preview.conflicts.size)
    }

    @Test
    fun `exact draft reports a provable overlap with a numeric range`() {
        val existing = BlacklistRuleEntity(
            id = 10,
            ruleType = BlacklistRuleEntity.TYPE_RANGE,
            startNumber = "4915123456000",
            endNumber = "4915123456999"
        )
        val draft = BlacklistRuleEntity(
            ruleType = BlacklistRuleEntity.TYPE_EXACT,
            pattern = "+49 151 23456789"
        )

        val preview = analyzer.analyze(draft, listOf(existing))

        assertFalse(preview.hasDuplicate)
        assertEquals(RuleConflictKind.OVERLAP, preview.conflicts.single().kind)
    }
}
