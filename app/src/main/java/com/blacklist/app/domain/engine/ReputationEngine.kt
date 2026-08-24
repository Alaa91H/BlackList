package com.blacklist.app.domain.engine

import com.blacklist.app.data.local.dao.CallerReputationDao
import com.blacklist.app.data.local.entity.CallerReputationEntity
import com.blacklist.app.domain.model.CallerReputation
import com.blacklist.app.domain.model.ReputationLevel
import com.blacklist.app.domain.model.UserVerdict

class ReputationEngine(
    private val dao: CallerReputationDao
) {
    suspend fun getOrCreate(normalized: String): CallerReputationEntity {
        return dao.find(normalized) ?: CallerReputationEntity(normalizedNumber = normalized).also { dao.upsert(it) }
    }

    suspend fun recordBlocked(normalized: String) {
        val existing = dao.find(normalized)
        if (existing == null) {
            dao.upsert(CallerReputationEntity(normalizedNumber = normalized, totalCalls = 1, blockedCalls = 1, spamScore = 30, riskScore = 30, level = "SUSPICIOUS"))
        } else {
            dao.incrementBlocked(normalized)
            // update scores
            val updated = dao.find(normalized) ?: return
            val newSpam = (updated.spamScore + 15).coerceAtMost(100)
            val newRisk = (updated.riskScore + 15).coerceAtMost(100)
            dao.upsert(updated.copy(spamScore = newSpam, riskScore = newRisk, level = levelFor(updated, newSpam, newRisk).name))
        }
    }

    suspend fun recordAllowed(normalized: String) {
        val existing = dao.find(normalized)
        if (existing == null) {
            dao.upsert(CallerReputationEntity(normalizedNumber = normalized, totalCalls = 1, allowedCalls = 1, level = "NEUTRAL"))
        } else {
            dao.incrementAllowed(normalized)
            // decay spam if user allows
            val updated = dao.find(normalized) ?: return
            val newSpam = (updated.spamScore - 10).coerceAtLeast(0)
            dao.upsert(updated.copy(spamScore = newSpam, level = levelFor(updated, newSpam, updated.riskScore).name))
        }
    }

    suspend fun setUserVerdict(normalized: String, verdict: UserVerdict) {
        val e = getOrCreate(normalized)
        val level = when (verdict) {
            UserVerdict.TRUSTED -> "TRUSTED"
            UserVerdict.SPAM -> "MALICIOUS"
            UserVerdict.NOT_SPAM -> "NEUTRAL"
        }
        val spam = when (verdict) {
            UserVerdict.SPAM -> 90
            UserVerdict.NOT_SPAM -> 10
            UserVerdict.TRUSTED -> 0
        }
        dao.upsert(e.copy(userVerdict = verdict.name, level = level, spamScore = spam))
    }

    private fun levelFor(e: CallerReputationEntity, spam: Int, risk: Int): ReputationLevel {
        return when {
            e.userVerdict == "TRUSTED" -> ReputationLevel.TRUSTED
            e.userVerdict == "SPAM" -> ReputationLevel.MALICIOUS
            spam >= 80 || risk >= 80 -> ReputationLevel.MALICIOUS
            spam >= 50 || risk >= 60 -> ReputationLevel.SUSPICIOUS
            e.blockedCalls > 3 && e.blockedCalls > e.allowedCalls -> ReputationLevel.SUSPICIOUS
            e.allowedCalls > 5 && e.blockedCalls == 0 -> ReputationLevel.TRUSTED
            else -> ReputationLevel.NEUTRAL
        }
    }
}
