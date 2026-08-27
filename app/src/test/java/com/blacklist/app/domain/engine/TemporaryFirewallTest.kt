package com.blacklist.app.domain.engine

import com.blacklist.app.data.local.entity.BlacklistRuleEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemporaryFirewallTest {
    @Test
    fun `temporary block applies only before its expiry`() {
        val now = 1_000_000L
        val active = BlacklistRuleEntity(
            ruleType = BlacklistRuleEntity.TYPE_TEMP_BLOCK_ALL,
            pattern = (now + 1).toString()
        )
        val expired = active.copy(pattern = now.toString())

        assertTrue(TemporaryFirewall.blockAllActive(listOf(active), now) != null)
        assertTrue(TemporaryFirewall.blockAllActive(listOf(expired), now) == null)
    }

    @Test
    fun `temporary allow matches only an active matching number`() {
        val now = 1_000_000L
        val allow = BlacklistRuleEntity(
            ruleType = BlacklistRuleEntity.TYPE_TEMP_ALLOW,
            pattern = "4915123456789",
            startNumber = (now + 1).toString()
        )

        assertTrue(TemporaryFirewall.allowMatches(listOf(allow), "4915123456789", now))
        assertFalse(TemporaryFirewall.allowMatches(listOf(allow), "4915111111111", now))
        assertFalse(TemporaryFirewall.allowMatches(listOf(allow.copy(startNumber = now.toString())), "4915123456789", now))
    }

    @Test
    fun `temporary exact block matches only its active canonical number`() {
        val now = 1_000_000L
        val block = BlacklistRuleEntity(
            ruleType = BlacklistRuleEntity.TYPE_TEMP_BLOCK_EXACT,
            pattern = "4915123456789",
            startNumber = (now + 1).toString()
        )

        assertTrue(TemporaryFirewall.blockExactMatches(listOf(block), "4915123456789", now) == block)
        assertTrue(TemporaryFirewall.blockExactMatches(listOf(block), "491512345678", now) == null)
        assertTrue(TemporaryFirewall.blockExactMatches(listOf(block.copy(startNumber = now.toString())), "4915123456789", now) == null)
    }

    @Test
    fun `outbound callback grace matches only its active exact number`() {
        val now = 1_000_000L
        val grace = BlacklistRuleEntity(
            ruleType = BlacklistRuleEntity.TYPE_TEMP_OUTBOUND_CALLBACK,
            pattern = "4915123456789",
            startNumber = (now + 1).toString()
        )

        assertTrue(TemporaryFirewall.outboundCallbackMatches(listOf(grace), "4915123456789", now))
        assertFalse(TemporaryFirewall.outboundCallbackMatches(listOf(grace), "4915111111111", now))
        assertFalse(TemporaryFirewall.outboundCallbackMatches(listOf(grace.copy(startNumber = now.toString())), "4915123456789", now))
    }
}
