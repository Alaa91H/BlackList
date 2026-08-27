package com.blacklist.app.domain.sharing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedPhoneNumberExtractorTest {

    @Test
    fun `extracts phone-like fragments while retaining only a bounded unique set`() {
        val candidates = SharedPhoneNumberExtractor.extract(
            "Call +49 151 12345678 or 0049-30-123456. Duplicate +49 151 12345678"
        )

        assertEquals(listOf("+49 151 12345678", "0049-30-123456"), candidates)
    }

    @Test
    fun `rejects non phone text and blank payloads`() {
        assertTrue(SharedPhoneNumberExtractor.extract(null).isEmpty())
        assertTrue(SharedPhoneNumberExtractor.extract("only words and https://example.test").isEmpty())
        assertTrue(SharedPhoneNumberExtractor.extract("1234").isEmpty())
    }

    @Test
    fun `caps input and candidate count`() {
        val numbers = (1..20).joinToString("; ") { "+49 151 1234${it.toString().padStart(4, '0')}" }
        val candidates = SharedPhoneNumberExtractor.extract(numbers + " ".repeat(SharedPhoneNumberExtractor.MAX_INPUT_LENGTH + 100))

        assertEquals(SharedPhoneNumberExtractor.MAX_CANDIDATES, candidates.size)
    }

    @Test
    fun `does not accept a candidate embedded in an alphanumeric token`() {
        assertTrue(SharedPhoneNumberExtractor.extract("abc4915112345678def").isEmpty())
    }
}
