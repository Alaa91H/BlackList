package com.blacklist.app.domain.engine

import com.blacklist.app.domain.model.PhoneNumber
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Local behavioral detection: bursts, repeated calls, sequential numbers, campaign hints.
 * All state is in memory, with no database or network access on the screening path.
 */
class BehaviorEngine {
    private val recentCalls = ArrayDeque<Pair<String, Long>>() // (digits, timestamp)
    private val mutex = Mutex()

    suspend fun recordAttempt(digits: String) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            recentCalls.addLast(digits to now)
            // keep 15 min window
            while (recentCalls.isNotEmpty() && now - recentCalls.first().second > 15 * 60 * 1000) {
                recentCalls.removeFirst()
            }
        }
    }

    suspend fun signalsFor(number: PhoneNumber, windowMinutes: Int = 10): BehaviorSignals {
        val digits = number.digitsOnly
        mutex.withLock {
            val now = System.currentTimeMillis()
            // clean again
            while (recentCalls.isNotEmpty() && now - recentCalls.first().second > 15 * 60 * 1000) {
                recentCalls.removeFirst()
            }
            val windowMillis = windowMinutes.coerceIn(1, 60) * 60 * 1000L
            val last10 = recentCalls.count { now - it.second <= windowMillis }
            val sameNumber = recentCalls.count { it.first == digits && now - it.second <= windowMillis }
            val burst = last10 >= 5
            // sequential numbers: check if last 5 share prefix 6 digits
            val isSequential = if (recentCalls.size >= 4) {
                val last = recentCalls.takeLast(5).map { it.first }
                val prefix = last.first().take(6)
                last.all { it.startsWith(prefix) } && last.toSet().size >= 4
            } else false
            return BehaviorSignals(
                repeatedCount = sameNumber,
                isBurst = burst || isSequential,
                verificationFailed = false,
                callsLast10Minutes = last10
            )
        }
    }

    suspend fun detectCampaign(): String? {
        mutex.withLock {
            if (recentCalls.size < 5) return null
            val last = recentCalls.takeLast(8).map { it.first }
            val prefix6 = last.groupBy { it.take(6) }.maxByOrNull { it.value.size } ?: return null
            if (prefix6.value.size >= 5) return prefix6.key // campaign prefix
            return null
        }
    }
}
