package com.blacklist.app.domain.engine

/**
 * Bounded durations and identity rules for user-requested temporary exact blocks.
 * This policy is deliberately independent from Room and call-screening I/O.
 */
object TemporaryExactBlockPolicy {
    val HOUR_1 = 60L * 60 * 1000
    val DAY_1 = 24L * HOUR_1
    val DAYS_7 = 7L * DAY_1
    val DAYS_30 = 30L * DAY_1

    val supportedDurationsMs = listOf(HOUR_1, DAY_1, DAYS_7, DAYS_30)
    const val MIN_MANUAL_DURATION_MS = 60L * 1000
    val MAX_MANUAL_DURATION_MS = DAYS_30

    const val MAX_ACTIVE_RULES = 100
    const val MIN_E164_DIGITS = 7
    const val MAX_E164_DIGITS = 15

    fun isSupportedDuration(durationMs: Long): Boolean =
        durationMs in MIN_MANUAL_DURATION_MS..MAX_MANUAL_DURATION_MS

    fun manualDurationMs(minutes: Long): Long? =
        (minutes * 60_000L).takeIf { isSupportedDuration(it) }

    fun expiryAt(durationMs: Long, nowMillis: Long): Long {
        require(isSupportedDuration(durationMs)) { "Unsupported temporary block duration." }
        return (nowMillis + durationMs).coerceAtLeast(nowMillis)
    }

    fun isValidE164Digits(value: String): Boolean =
        value.length in MIN_E164_DIGITS..MAX_E164_DIGITS && value.all(Char::isDigit)
}
