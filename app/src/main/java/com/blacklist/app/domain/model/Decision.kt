package com.blacklist.app.domain.model

data class EnforcementDecision(
    val callEvent: CallEvent,
    val decision: Decision,
    val riskScore: Int, // 0-100
    val reputation: ReputationLevel,
    val reasons: List<String>,
    val matchedRules: List<CallRule>,
    val backend: EnforcementBackendType,
    val verification: VerificationStatus = VerificationStatus.UNKNOWN,
    val explainable: ExplainableDecision
)

enum class Decision { BLOCK, ALLOW, SILENCE }

enum class EnforcementBackendType { CALL_SCREENING, TELECOM, ROOT, SHIZUKU, NONE }

enum class VerificationStatus { SUCCESS, FAILED, UNKNOWN }

data class ExplainableDecision(
    val summary: String,
    val riskLevel: RiskLevel,
    val details: List<String>,
    val matchedRuleIds: List<Long>,
    val backend: String,
    val verification: String
)

enum class RiskLevel { SAFE, LOW, SUSPICIOUS, HIGH }

fun riskLevel(score: Int): RiskLevel = when (score) {
    in 0..29 -> RiskLevel.SAFE
    in 30..59 -> RiskLevel.LOW
    in 60..79 -> RiskLevel.SUSPICIOUS
    else -> RiskLevel.HIGH
}
