package com.blacklist.app.domain.model

data class CallerReputation(
    val normalizedNumber: String,
    val firstSeen: Long = System.currentTimeMillis(),
    var lastSeen: Long = System.currentTimeMillis(),
    var totalCalls: Int = 0,
    var blockedCalls: Int = 0,
    var allowedCalls: Int = 0,
    var spamScore: Int = 0, // 0-100
    var riskScore: Int = 0, // 0-100
    var level: ReputationLevel = ReputationLevel.NEUTRAL,
    var userVerdict: UserVerdict? = null, // explicit user override
    var behaviorFlags: Set<String> = emptySet(),
    var prefixReputation: Int? = null // for campaign
)

enum class UserVerdict { TRUSTED, SPAM, NOT_SPAM }

fun CallerReputation.updateLevel() {
    level = when {
        userVerdict == UserVerdict.TRUSTED -> ReputationLevel.TRUSTED
        userVerdict == UserVerdict.SPAM -> ReputationLevel.MALICIOUS
        riskScore >= 80 || spamScore >= 80 -> ReputationLevel.MALICIOUS
        riskScore >= 60 || spamScore >= 60 -> ReputationLevel.SUSPICIOUS
        blockedCalls > 3 && blockedCalls > allowedCalls -> ReputationLevel.SUSPICIOUS
        allowedCalls > 5 && blockedCalls == 0 -> ReputationLevel.TRUSTED
        else -> ReputationLevel.NEUTRAL
    }
}
