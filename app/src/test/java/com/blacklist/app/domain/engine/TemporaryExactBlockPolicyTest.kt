package com.blacklist.app.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemporaryExactBlockPolicyTest {

    @Test
    fun `only the documented temporary block durations are accepted`() {
        assertEquals(
            listOf(
                TemporaryExactBlockPolicy.HOUR_1,
                TemporaryExactBlockPolicy.DAY_1,
                TemporaryExactBlockPolicy.DAYS_7,
                TemporaryExactBlockPolicy.DAYS_30
            ),
            TemporaryExactBlockPolicy.supportedDurationsMs
        )
        assertFalse(TemporaryExactBlockPolicy.isSupportedDuration(0))
        assertFalse(TemporaryExactBlockPolicy.isSupportedDuration(TemporaryExactBlockPolicy.HOUR_1 * 2))
    }

    @Test
    fun `expiry is calculated from a supported duration without clock underflow`() {
        val now = 1_000_000L
        assertEquals(now + TemporaryExactBlockPolicy.DAY_1, TemporaryExactBlockPolicy.expiryAt(TemporaryExactBlockPolicy.DAY_1, now))
    }

    @Test
    fun `only canonical E164 digit lengths are accepted`() {
        assertTrue(TemporaryExactBlockPolicy.isValidE164Digits("4915123456789"))
        assertTrue(TemporaryExactBlockPolicy.isValidE164Digits("1234567"))
        assertTrue(TemporaryExactBlockPolicy.isValidE164Digits("123456789012345"))
        assertFalse(TemporaryExactBlockPolicy.isValidE164Digits("123456"))
        assertFalse(TemporaryExactBlockPolicy.isValidE164Digits("1234567890123456"))
        assertFalse(TemporaryExactBlockPolicy.isValidE164Digits("4915abc6789"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unsupported duration cannot produce an expiry`() {
        TemporaryExactBlockPolicy.expiryAt(42L, nowMillis = 1_000L)
    }
}
