package com.blacklist.app.domain.importexport

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvListTransferServiceTest {
    private val service = CsvListTransferService()

    @Test
    fun `preview accepts valid rows and excludes duplicate and invalid values`() {
        val input = """
            number,name
            +49 151 1234567,Alice
            +49 151 1234567,Duplicate Alice
            abc,Bad row
            112,Emergency
        """.trimIndent()

        val preview = service.preview(ByteArrayInputStream(input.toByteArray()))

        assertEquals(2, preview.rows.size)
        assertEquals(1, preview.duplicateRows)
        assertEquals(1, preview.invalidRows)
        assertEquals("Alice", preview.rows.first().displayName)
        assertTrue(preview.rows.any { it.number == "112" })
    }

    @Test
    fun `preview reads a spreadsheet-safe exported e164 number`() {
        val output = ByteArrayOutputStream()
        service.export(listOf(CsvListRow("+491511234567", "Alice")), output)

        val preview = service.preview(ByteArrayInputStream(output.toByteArray()))

        assertEquals(1, preview.rows.size)
        assertEquals("+491511234567", preview.rows.single().number)
        assertFalse(output.toString().contains(",+491511234567,"))
    }

    @Test
    fun `preview rejects unterminated quoted input`() {
        val input = "number,name\n+491511234567,\"Alice"

        val preview = service.preview(ByteArrayInputStream(input.toByteArray()))

        assertEquals(0, preview.rows.size)
        assertEquals(1, preview.invalidRows)
    }
}
