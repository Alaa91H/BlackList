package com.blacklist.app.domain.engine

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Rate limiter: unknown 3 calls / 10 minutes -> temporary firewall.
 * Local, no battery drain (in-memory + DB prune).
 */
class CallFloodProtector(
    private val unknownThreshold: Int = 3,
    private val unknownWindowMinutes: Int = 10,
    private val privateThreshold: Int = 3
) {
    private val history = ArrayDeque<Pair<String, Long>>() // key = "unknown" or "private" or digits
    private val mutex = Mutex()

    suspend fun shouldTriggerFlood(eventDigits: String?, isUnknown: Boolean, isPrivate: Boolean): Boolean {
        val key = when {
            isPrivate -> "private"
            isUnknown -> "unknown"
            eventDigits != null -> eventDigits
            else -> return false
        }
        mutex.withLock {
            val now = System.currentTimeMillis()
            val window = unknownWindowMinutes * 60 * 1000L
            history.addLast(key to now)
            // prune
            while (history.isNotEmpty() && now - history.first().second > window) history.removeFirst()
            val count = history.count { it.first == key }
            val threshold = if (isPrivate) privateThreshold else unknownThreshold
            return count >= threshold
        }
    }

    suspend fun reset() { mutex.withLock { history.clear() } }
}
