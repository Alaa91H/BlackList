package com.blacklist.app.domain.engine

import com.blacklist.app.data.local.entity.BlacklistRuleEntity

/**
 * Temporary firewall semantics stored in blacklist_rules (no schema change):
 *  - TEMP_BLOCK_ALL: pattern = expiry epoch millis (blocks everything except whitelist while active)
 *  - TEMP_ALLOW:     pattern = normalized digits, startNumber = expiry epoch millis
 * Expired rules are ignored at evaluation time and purged opportunistically by the UI layer.
 */
object TemporaryFirewall {

    const val MIN_15 = 15L * 60 * 1000
    const val MIN_30 = 30L * 60 * 1000
    val HOUR_1 = 60L * 60 * 1000
    val HOUR_2 = 2L * 60 * 60 * 1000

    fun isTempType(ruleType: String): Boolean =
        ruleType == BlacklistRuleEntity.TYPE_TEMP_BLOCK_ALL || ruleType == BlacklistRuleEntity.TYPE_TEMP_ALLOW

    fun expiryOf(rule: BlacklistRuleEntity): Long? {
        val raw = if (rule.ruleType == BlacklistRuleEntity.TYPE_TEMP_ALLOW) rule.startNumber else rule.pattern
        return raw?.trim()?.toLongOrNull()
    }

    fun isActive(rule: BlacklistRuleEntity, now: Long = System.currentTimeMillis()): Boolean {
        if (!rule.isEnabled || !isTempType(rule.ruleType)) return false
        val expiry = expiryOf(rule) ?: return false
        return expiry > now
    }

    fun remainingMs(rule: BlacklistRuleEntity, now: Long = System.currentTimeMillis()): Long {
        val expiry = expiryOf(rule) ?: return 0
        return (expiry - now).coerceAtLeast(0)
    }

    fun blockAllActive(rules: List<BlacklistRuleEntity>, now: Long = System.currentTimeMillis()): BlacklistRuleEntity? {
        return rules.firstOrNull { it.ruleType == BlacklistRuleEntity.TYPE_TEMP_BLOCK_ALL && isActive(it, now) }
    }

    fun allowMatches(rules: List<BlacklistRuleEntity>, digitsOnly: String, now: Long = System.currentTimeMillis()): Boolean {
        if (digitsOnly.isBlank()) return false
        return rules.any { rule ->
            rule.ruleType == BlacklistRuleEntity.TYPE_TEMP_ALLOW && isActive(rule, now) &&
                com.blacklist.app.util.PhoneNumberUtils.matches(rule.pattern, digitsOnly)
        }
    }
}
