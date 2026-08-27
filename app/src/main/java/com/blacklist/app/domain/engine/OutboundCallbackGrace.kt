package com.blacklist.app.domain.engine

import java.util.concurrent.ConcurrentHashMap

/**
 * Bounded, process-local bridge for the callback allowance recorded after a
 * definite outgoing call. Room remains the durable source of truth; this map
 * only prevents a snapshot-refresh race from turning an immediate callback
 * into a false positive. It contains digits and UTC expiries only.
 */
object OutboundCallbackGrace {
    const val DURATION_MS = 15L * 60 * 1000
    const val MAX_ENTRIES = 32

    private val expiriesByDigits = ConcurrentHashMap<String, Long>()

    fun activate(digitsOnly: String, now: Long = System.currentTimeMillis()): Long? {
        if (!isValidDigits(digitsOnly)) return null
        prune(now)
        if (!expiriesByDigits.containsKey(digitsOnly) && expiriesByDigits.size >= MAX_ENTRIES) {
            expiriesByDigits.entries.minByOrNull { it.value }?.key?.let(expiriesByDigits::remove)
        }
        return (now + DURATION_MS).also { expiriesByDigits[digitsOnly] = it }
    }

    fun isActive(digitsOnly: String, now: Long = System.currentTimeMillis()): Boolean {
        val expiry = expiriesByDigits[digitsOnly] ?: return false
        return if (expiry > now) true else {
            expiriesByDigits.remove(digitsOnly, expiry)
            false
        }
    }

    fun clear(digitsOnly: String) {
        expiriesByDigits.remove(digitsOnly)
    }

    fun isValidDigits(digitsOnly: String): Boolean =
        digitsOnly.length in 3..32 && digitsOnly.all(Char::isDigit)

    private fun prune(now: Long) {
        expiriesByDigits.entries.removeIf { (_, expiry) -> expiry <= now }
    }
}
