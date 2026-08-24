package com.blacklist.app.domain.model

data class FirewallProfile(
    val id: String, // normal, work, sleep, vacation, maximum, contacts_only, custom
    val name: String,
    val unknownPolicy: Decision = Decision.BLOCK,
    val hiddenPolicy: Decision = Decision.BLOCK,
    val spamThreshold: Int = 60,
    val internationalPolicy: Decision = Decision.ALLOW,
    val riskThreshold: Int = 80,
    val whitelistIds: Set<Long> = emptySet(),
    val isDefault: Boolean = false
) {
    companion object {
        val NORMAL = FirewallProfile("normal", "Normal", Decision.ALLOW, Decision.BLOCK, 60, Decision.ALLOW, 80, isDefault = true)
        val WORK = FirewallProfile("work", "Work", Decision.SILENCE, Decision.BLOCK, 60, Decision.ALLOW, 70)
        val SLEEP = FirewallProfile("sleep", "Sleep", Decision.BLOCK, Decision.BLOCK, 40, Decision.BLOCK, 60)
        val MAXIMUM = FirewallProfile("maximum", "Maximum Protection", Decision.BLOCK, Decision.BLOCK, 30, Decision.BLOCK, 50)
        val CONTACTS_ONLY = FirewallProfile("contacts_only", "Contacts Only", Decision.BLOCK, Decision.BLOCK, 30, Decision.BLOCK, 50)
        val ALL = listOf(NORMAL, WORK, SLEEP, MAXIMUM, CONTACTS_ONLY)
    }
}

data class TemporaryRule(
    val id: Long = 0,
    val isEnabled: Boolean = true,
    val action: Decision = Decision.BLOCK,
    val durationMinutes: Int, // 15,30,60,120,1440
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + durationMinutes * 60 * 1000L,
    val reason: String? = null
)

enum class VipLevel { EMERGENCY, VIP, FAVORITE, CONTACT, UNKNOWN }
enum class ContactPolicy { ALWAYS_ALLOW, VIP, ALLOW_WORK, ALLOW_DAYTIME, SILENCE, BLOCK, CUSTOM }
