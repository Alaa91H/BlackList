package com.blacklist.app.domain.analytics

import com.blacklist.app.data.local.dao.BlockedCallLogDao
import com.blacklist.app.data.local.dao.CallerReputationDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class FirewallStatistics(
    val totalCalls: Int = 0,
    val blocked: Int = 0,
    val allowed: Int = 0,
    val silenced: Int = 0,
    val spam: Int = 0,
    val unknown: Int = 0,
    val hidden: Int = 0,
    val highRisk: Int = 0,
    val topBlockedPrefixes: List<Pair<String, Int>> = emptyList(),
    val topBlockedNumbers: List<Pair<String, Int>> = emptyList(),
    val blockRate: Float = 0f,
    val protectionScore: Int = 0
)

class StatisticsEngine(
    private val logDao: BlockedCallLogDao,
    private val repDao: CallerReputationDao
) {
    fun observe(): Flow<FirewallStatistics> {
        return combine(logDao.observeAll(), repDao.observeAll()) { logs, reps ->
            val blocked = logs.size
            val byReason = logs.groupBy { it.reason }
            FirewallStatistics(
                totalCalls = blocked + 100, // placeholder, real would be from CallLog
                blocked = blocked,
                unknown = byReason["UNKNOWN"]?.size ?: 0,
                hidden = byReason["PRIVATE"]?.size ?: 0,
                spam = byReason["BLACKLIST"]?.size ?: 0,
                highRisk = reps.count { it.riskScore >= 80 },
                topBlockedPrefixes = logs.mapNotNull { it.phoneNumber?.take(6) }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(5).map { it.key to it.value },
                topBlockedNumbers = logs.mapNotNull { it.phoneNumber }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(5).map { it.key to it.value },
                blockRate = if (blocked > 0) blocked.toFloat() / (blocked + 10) else 0f,
                protectionScore = calculateProtection(logs, reps)
            )
        }
    }

    private fun calculateProtection(logs: List<com.blacklist.app.data.local.entity.BlockedCallLogEntity>, reps: List<com.blacklist.app.data.local.entity.CallerReputationEntity>): Int {
        var score = 50
        if (logs.isNotEmpty()) score += 10
        if (reps.isNotEmpty()) score += 10
        // diagnostics would add more
        score += 20 // base for call screening available
        return score.coerceIn(0, 100)
    }
}
