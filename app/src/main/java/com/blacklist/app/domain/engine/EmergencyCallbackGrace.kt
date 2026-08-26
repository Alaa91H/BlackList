package com.blacklist.app.domain.engine

/**
 * Local-only safety window for callbacks that commonly follow an emergency
 * call. The expiry is persisted in the policy snapshot so the screening hot
 * path performs only a time comparison and never reads storage.
 *
 * Explicit user blacklist rules and the whitelist remain more specific than
 * this broad safeguard. The window is deliberately short and has no network,
 * location, contact, or call-log dependency.
 */
object EmergencyCallbackGrace {
    const val DURATION_MS: Long = 15 * 60 * 1_000L

    @Volatile
    private var inMemoryExpiry: Long = 0L

    fun activate(now: Long = System.currentTimeMillis()): Long = expiryFrom(now).also { expiry ->
        inMemoryExpiry = maxOf(inMemoryExpiry, expiry)
    }

    fun expiryFrom(now: Long = System.currentTimeMillis()): Long = now + DURATION_MS

    fun isActive(persistedExpiry: Long, now: Long = System.currentTimeMillis()): Boolean =
        maxOf(persistedExpiry, inMemoryExpiry) > now
}
