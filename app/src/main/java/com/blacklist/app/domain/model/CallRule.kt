package com.blacklist.app.domain.model

/**
 * Central rule definition. Supports exact/prefix/range/country/hidden/unknown plus SIM/time/profile/behavior.
 */
data class CallRule(
    val id: Long = 0,
    val isEnabled: Boolean = true,
    val priority: Int = 100, // lower = higher priority (0 = emergency)
    val action: RuleAction,
    val conditions: List<RuleCondition>,
    val description: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long? = null // for temporary rules
)

enum class RuleAction { BLOCK, ALLOW, SILENCE }

sealed class RuleCondition {
    data class Exact(val normalizedNumber: String) : RuleCondition()
    data class Prefix(val prefix: String) : RuleCondition() // e.g. +4930
    data class Range(val start: String, val end: String) : RuleCondition() // inclusive digits
    data class Country(val iso: String) : RuleCondition() // DE, US
    data object Hidden : RuleCondition()
    data object Unknown : RuleCondition()
    data class IsInContacts(val value: Boolean) : RuleCondition()
    data class SimSlot(val slot: Int) : RuleCondition() // 0,1 or -1 for any
    data class TimeWindow(val startMinutes: Int, val endMinutes: Int, val daysOfWeek: Int) : RuleCondition()
    data class Profile(val profileId: String) : RuleCondition()
    data class Frequency(val callsInMinutes: Int, val windowMinutes: Int) : RuleCondition() // burst
    data class RiskThreshold(val minScore: Int) : RuleCondition()
    data class Reputation(val level: ReputationLevel) : RuleCondition()
}

enum class ReputationLevel { TRUSTED, NEUTRAL, SUSPICIOUS, MALICIOUS, BLOCKED }

enum class RulePriority(val value: Int) {
    EMERGENCY(0),
    VIP_OVERRIDE(10),
    EXPLICIT_ALLOW(20),
    EXPLICIT_BLOCK(30),
    SECURITY(40),
    BEHAVIOR(50),
    SPAM(60),
    PROFILE(70),
    DEFAULT(100)
}
