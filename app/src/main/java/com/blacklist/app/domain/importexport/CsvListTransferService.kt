package com.blacklist.app.domain.importexport

import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

enum class CsvListTarget { BLACKLIST, WHITELIST }

data class CsvListRow(val number: String, val displayName: String?)

data class CsvImportPreview(
    val rows: List<CsvListRow>,
    val duplicateRows: Int,
    val invalidRows: Int,
    val sourceRows: Int
)

/**
 * Small, local-only CSV interchange for numbered lists. It never includes call
 * history, settings, identifiers, notification preferences, or encrypted backup data.
 */
class CsvListTransferService {
    fun preview(input: InputStream): CsvImportPreview {
        val text = readLimitedText(input)
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return CsvImportPreview(emptyList(), 0, 0, 0)

        val first = parseLine(lines.first())
        val hasHeader = first.firstOrNull()?.trim()?.equals("number", ignoreCase = true) == true
        val dataLines = if (hasHeader) lines.drop(1) else lines
        require(dataLines.size <= MAX_ROWS) { "CSV contains too many rows." }

        val rows = mutableListOf<CsvListRow>()
        val seen = mutableSetOf<String>()
        var invalid = 0
        var duplicates = 0
        dataLines.forEach { line ->
            val fields = runCatching { parseLine(line) }.getOrNull()
            if (fields == null || fields.isEmpty()) {
                invalid++
                return@forEach
            }
            val rawNumber = unescapeSpreadsheetPrefix(fields[0]).trim()
            val digits = rawNumber.filter(Char::isDigit)
            if (digits.length !in MIN_DIGITS..MAX_DIGITS || rawNumber.length > MAX_CELL_LENGTH) {
                invalid++
                return@forEach
            }
            if (!seen.add(digits)) {
                duplicates++
                return@forEach
            }
            val name = fields.getOrNull(1)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.takeIf { it.length <= MAX_NAME_LENGTH }
            if (fields.getOrNull(1)?.trim()?.length ?: 0 > MAX_NAME_LENGTH) {
                invalid++
                return@forEach
            }
            rows += CsvListRow(rawNumber, name)
        }
        return CsvImportPreview(rows, duplicates, invalid, dataLines.size)
    }

    fun export(rows: List<CsvListRow>, output: OutputStream) {
        require(rows.size <= MAX_ROWS) { "Too many rows to export." }
        val csv = buildString {
            append("number,name\n")
            rows.forEach { row ->
                append(escapeCsv(spreadsheetSafe(row.number)))
                append(',')
                append(escapeCsv(row.displayName.orEmpty()))
                append('\n')
            }
        }
        output.write(csv.toByteArray(StandardCharsets.UTF_8))
    }

    private fun readLimitedText(input: InputStream): String {
        val output = StringBuilder()
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            total += read
            require(total <= MAX_FILE_BYTES) { "CSV file is too large." }
            output.append(String(buffer, 0, read, StandardCharsets.UTF_8))
        }
        return output.toString().removePrefix("\uFEFF")
    }

    private fun parseLine(line: String): List<String> {
        require(line.length <= MAX_LINE_LENGTH) { "CSV row is too long." }
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index++
                }
                char == '"' -> quoted = !quoted
                char == ',' && !quoted -> {
                    fields += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
            index++
        }
        require(!quoted) { "CSV has an unterminated quoted field." }
        fields += current.toString()
        return fields
    }

    private fun escapeCsv(value: String): String = "\"${value.replace("\"", "\"\"")}\""

    private fun spreadsheetSafe(value: String): String =
        if (value.firstOrNull() in setOf('=', '+', '-', '@')) "'$value" else value

    private fun unescapeSpreadsheetPrefix(value: String): String =
        if (value.length > 1 && value[0] == '\'' && value[1] in setOf('=', '+', '-', '@')) value.drop(1) else value

    private companion object {
        const val MAX_FILE_BYTES = 1 * 1024 * 1024
        const val MAX_ROWS = 10_000
        const val MAX_LINE_LENGTH = 1_024
        const val MAX_CELL_LENGTH = 64
        const val MAX_NAME_LENGTH = 100
        const val MIN_DIGITS = 3
        const val MAX_DIGITS = 20
        const val BUFFER_SIZE = 8 * 1024
    }
}
