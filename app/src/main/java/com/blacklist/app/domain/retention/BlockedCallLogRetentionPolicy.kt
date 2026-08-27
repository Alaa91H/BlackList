package com.blacklist.app.domain.retention

/**
 * Bounded local retention policy for BlackList's own blocked-call history.
 *
 * A value of [NEVER] retains history indefinitely. Every other supported value
 * represents whole calendar-length 24-hour periods. This policy deliberately
 * has no influence on call-screening decisions or Android's shared call log.
 */
object BlockedCallLogRetentionPolicy {
    const val NEVER = 0L
    const val DAYS_7 = 7L
    const val DAYS_30 = 30L
    const val DAYS_90 = 90L
    const val DAYS_365 = 365L

    const val MILLIS_PER_DAY = 24L * 60L * 60L * 1_000L

    val supportedDays: List<Long> = listOf(NEVER, DAYS_7, DAYS_30, DAYS_90, DAYS_365)

    fun isSupported(days: Long): Boolean = days in supportedDays

    /**
     * Returns the exclusive deletion cutoff for a supported policy, or null
     * when history must be kept indefinitely. Entries exactly on the cutoff
     * remain retained.
     */
    fun deletionCutoffMillis(days: Long, nowMillis: Long): Long? {
        require(isSupported(days)) { "Unsupported blocked-call history retention." }
        if (days == NEVER) return null
        return (nowMillis - days * MILLIS_PER_DAY).coerceAtLeast(0L)
    }
}
