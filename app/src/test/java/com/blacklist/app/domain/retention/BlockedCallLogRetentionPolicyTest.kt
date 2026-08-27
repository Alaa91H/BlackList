package com.blacklist.app.domain.retention

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockedCallLogRetentionPolicyTest {

    @Test
    fun `default retention keeps local history indefinitely`() {
        assertEquals(BlockedCallLogRetentionPolicy.NEVER, 0L)
        assertTrue(BlockedCallLogRetentionPolicy.isSupported(BlockedCallLogRetentionPolicy.NEVER))
        assertNull(
            BlockedCallLogRetentionPolicy.deletionCutoffMillis(
                BlockedCallLogRetentionPolicy.NEVER,
                nowMillis = 1_000_000L
            )
        )
    }

    @Test
    fun `only bounded documented retention periods are supported`() {
        assertEquals(
            listOf(0L, 7L, 30L, 90L, 365L),
            BlockedCallLogRetentionPolicy.supportedDays
        )
        assertFalse(BlockedCallLogRetentionPolicy.isSupported(1L))
        assertFalse(BlockedCallLogRetentionPolicy.isSupported(-7L))
        assertFalse(BlockedCallLogRetentionPolicy.isSupported(366L))
    }

    @Test
    fun `supported period computes exclusive cleanup cutoff`() {
        val now = 4_000_000_000L

        assertEquals(
            now - BlockedCallLogRetentionPolicy.DAYS_30 * BlockedCallLogRetentionPolicy.MILLIS_PER_DAY,
            BlockedCallLogRetentionPolicy.deletionCutoffMillis(BlockedCallLogRetentionPolicy.DAYS_30, now)
        )
    }

    @Test
    fun `early clock values never underflow the cleanup cutoff`() {
        assertEquals(
            0L,
            BlockedCallLogRetentionPolicy.deletionCutoffMillis(
                BlockedCallLogRetentionPolicy.DAYS_365,
                nowMillis = 1L
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unsupported retention cannot produce a cleanup cutoff`() {
        BlockedCallLogRetentionPolicy.deletionCutoffMillis(14L, nowMillis = 1_000_000L)
    }
}
