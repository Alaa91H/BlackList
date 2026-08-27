package com.blacklist.app.domain.importexport

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.Locale

/**
 * Parses a user-selected, offline reputation list. This format is deliberately
 * small and explicit: metadata makes provenance reviewable and no URL is ever
 * fetched by the app.
 *
 * Format:
 * # BlackList Offline Reputation List
 * # source: Example Research Group
 * # version: 2026.08 (optional)
 * # url: https://example.invalid/list (optional; display-only)
 * number,score,category
 * +4930123456,90,telemarketing
 */
class OfflineReputationListTransferService {
    fun preview(input: InputStream): OfflineReputationImportPreview {
        val bytes = readBounded(input)
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { byte -> String.format(Locale.ROOT, "%02x", byte.toInt() and 0xff) }
        val lines = decodeUtf8(bytes).removePrefix("\uFEFF").lineSequence().toList()
        require(lines.size <= MAX_LINES) { "Reputation list contains too many lines." }

        val metadata = linkedMapOf<String, String>()
        var headerIndex = -1
        lines.forEachIndexed { index, rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEachIndexed
            if (line.startsWith("#")) {
                val separator = line.indexOf(':')
                if (separator > 1) {
                    val key = line.substring(1, separator).trim().lowercase()
                    val value = line.substring(separator + 1).trim()
                    if (key in ALLOWED_METADATA && value.isNotEmpty()) {
                        require(key !in metadata) { "Reputation list repeats '$key' metadata." }
                        metadata[key] = value
                    }
                }
            } else if (headerIndex == -1) {
                headerIndex = index
            }
        }

        val sourceName = metadata["source"]?.takeIf(::isSafeSourceText)
            ?: throw IllegalArgumentException("Reputation list must declare a valid source.")
        val sourceVersion = metadata["version"]?.takeIf(::isSafeVersionText)
        require(metadata["version"] == null || sourceVersion != null) { "Reputation list has invalid version metadata." }
        val sourceUrl = metadata["url"]
        require(sourceUrl == null || isSafeDisplayUrl(sourceUrl)) { "Reputation list has an invalid declared URL." }
        require(headerIndex >= 0) { "Reputation list has no CSV header." }

        val header = parseCsvLine(lines[headerIndex]).map { it.trim().lowercase(Locale.ROOT) }
        require(header == listOf("number", "score", "category")) {
            "Reputation list header must be number,score,category."
        }
        val categoryIndex = header.indexOf("category")
        val rows = mutableListOf<OfflineReputationImportRow>()
        val seen = mutableSetOf<String>()
        var invalid = 0
        var duplicates = 0
        var sourceRows = 0

        lines.drop(headerIndex + 1).forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            sourceRows++
            val fields = runCatching { parseCsvLine(line) }.getOrNull()
            val rawNumber = fields?.getOrNull(0)?.trim().orEmpty()
            val score = fields?.getOrNull(1)?.trim()?.toIntOrNull()
            val category = categoryIndex.takeIf { it >= 0 }?.let { fields?.getOrNull(it)?.trim() }
                ?.takeIf { !it.isNullOrEmpty() }
            if (fields == null || fields.size != REQUIRED_COLUMN_COUNT || fields.any { it.length > MAX_CELL_LENGTH } ||
                !E164_NUMBER.matches(rawNumber) || score == null || score !in MIN_SCORE..MAX_SCORE ||
                (category?.let { !isSafeCategory(it) } ?: false)
            ) {
                invalid++
                return@forEach
            }
            if (!seen.add(rawNumber)) {
                duplicates++
                return@forEach
            }
            rows += OfflineReputationImportRow(rawNumber, score, category)
        }
        require(sourceRows <= MAX_ROWS) { "Reputation list contains too many rows." }
        return OfflineReputationImportPreview(
            sourceName = sourceName,
            sourceVersion = sourceVersion,
            sourceUrl = sourceUrl,
            fingerprintSha256 = fingerprint,
            rows = rows,
            sourceRows = sourceRows,
            invalidRows = invalid,
            duplicateRows = duplicates,
            highRiskRows = rows.count { it.riskScore >= HIGH_RISK_SCORE }
        )
    }

    private fun readBounded(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            total += read
            require(total <= MAX_FILE_BYTES) { "Reputation list is too large." }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun parseCsvLine(line: String): List<String> {
        require(line.length <= MAX_LINE_LENGTH) { "Reputation list row is too long." }
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            when (val character = line[index]) {
                '"' -> if (quoted && index + 1 < line.length && line[index + 1] == '"') {
                    current.append('"')
                    index++
                } else quoted = !quoted
                ',' -> if (quoted) current.append(character) else {
                    fields += current.toString()
                    current.clear()
                }
                else -> current.append(character)
            }
            index++
        }
        require(!quoted) { "Reputation list has an unterminated quoted field." }
        fields += current.toString()
        return fields
    }

    private fun decodeUtf8(bytes: ByteArray): String = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()

    private fun isSafeSourceText(value: String): Boolean =
        value.length in 1..MAX_SOURCE_LENGTH && value.all(::isSafeTextCharacter)

    private fun isSafeVersionText(value: String): Boolean =
        value.length in 1..MAX_VERSION_LENGTH && value.all(::isSafeTextCharacter)

    private fun isSafeCategory(value: String): Boolean =
        value.length <= MAX_CATEGORY_LENGTH && value.all(::isSafeTextCharacter)

    private fun isSafeTextCharacter(character: Char): Boolean =
        !character.isISOControl() && character != '\u007f'

    private fun isSafeDisplayUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        value.length <= MAX_URL_LENGTH && uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null
    }.getOrDefault(false)

    private companion object {
        val ALLOWED_METADATA = setOf("source", "version", "url")
        const val MAX_FILE_BYTES = 1 * 1024 * 1024
        const val MAX_LINES = 10_050
        const val MAX_ROWS = 10_000
        const val MAX_LINE_LENGTH = 1_024
        const val MAX_CELL_LENGTH = 512
        const val MAX_SOURCE_LENGTH = 100
        const val MAX_VERSION_LENGTH = 64
        const val MAX_URL_LENGTH = 512
        const val MAX_CATEGORY_LENGTH = 80
        const val REQUIRED_COLUMN_COUNT = 3
        const val MIN_SCORE = 0
        const val MAX_SCORE = 100
        const val HIGH_RISK_SCORE = 80
        const val BUFFER_SIZE = 8 * 1024
        val E164_NUMBER = Regex("^\\+[1-9][0-9]{6,14}$")
    }
}

data class OfflineReputationImportRow(
    val rawNumber: String,
    val riskScore: Int,
    val category: String?
)

data class OfflineReputationImportPreview(
    val sourceName: String,
    val sourceVersion: String?,
    val sourceUrl: String?,
    val fingerprintSha256: String,
    val rows: List<OfflineReputationImportRow>,
    val sourceRows: Int,
    val invalidRows: Int,
    val duplicateRows: Int,
    val highRiskRows: Int
)
