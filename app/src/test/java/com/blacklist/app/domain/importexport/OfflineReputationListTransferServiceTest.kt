package com.blacklist.app.domain.importexport

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineReputationListTransferServiceTest {
    private val service = OfflineReputationListTransferService()

    @Test
    fun `canonical list preserves provenance and produces deterministic lowercase fingerprint`() {
        val csv = """
            # BlackList Offline Reputation List
            # source: Example Research Group
            # version: 2026.08
            # url: https://example.org/reputation.csv
            number,score,category
            +4930123456,90,telemarketing
            +14155552671,45,marketing
        """.trimIndent()

        val first = preview(csv)
        val second = preview(csv)

        assertEquals("Example Research Group", first.sourceName)
        assertEquals("2026.08", first.sourceVersion)
        assertEquals("https://example.org/reputation.csv", first.sourceUrl)
        assertEquals(2, first.rows.size)
        assertEquals(1, first.highRiskRows)
        assertEquals(first.fingerprintSha256, second.fingerprintSha256)
        assertTrue(first.fingerprintSha256.matches(Regex("^[a-f0-9]{64}$")))
        assertFalse(first.fingerprintSha256.any { it.isUpperCase() })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `missing declared source is rejected`() {
        preview("number,score,category\n+4930123456,90,telemarketing")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non https declared URL is rejected`() {
        preview("# source: Example\n# url: http://example.org/list.csv\nnumber,score,category\n+4930123456,90,telemarketing")
    }

    @Test
    fun `non e164 duplicate and malformed rows are accounted for without being accepted`() {
        val preview = preview(
            """
                # source: Example
                number,score,category
                +4930123456,90,telemarketing
                +4930123456,80,duplicate
                030123456,95,not-canonical
                +14155552671,not-a-score,invalid
                +447700900123,70,"unterminated
            """.trimIndent()
        )

        assertEquals(5, preview.sourceRows)
        assertEquals(1, preview.rows.size)
        assertEquals(1, preview.duplicateRows)
        assertEquals(3, preview.invalidRows)
        assertEquals("+4930123456", preview.rows.single().rawNumber)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `oversized list is rejected before parsing`() {
        service.preview(ByteArrayInputStream(ByteArray(1_048_577)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `header must declare the complete canonical schema`() {
        preview("# source: Example\nnumber,score\n+4930123456,90")
    }

    private fun preview(content: String): OfflineReputationImportPreview =
        service.preview(ByteArrayInputStream(content.toByteArray(Charsets.UTF_8)))
}
