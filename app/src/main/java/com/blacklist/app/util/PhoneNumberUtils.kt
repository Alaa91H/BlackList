package com.blacklist.app.util

object PhoneNumberUtils {
    /**
     * Normalize to digits only with leading + preserved if present.
     * Used for consistent blacklist/whitelist matching.
     */
    fun normalize(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        val hasPlus = trimmed.startsWith("+")
        val digits = trimmed.filter { it.isDigit() }
        if (digits.isEmpty()) return null
        return if (hasPlus) "+$digits" else digits
    }

    /** For matching, compare normalized forms; also handles national format vs E164 */
    fun matches(a: String?, b: String?): Boolean {
        val na = normalize(a) ?: return false
        val nb = normalize(b) ?: return false
        if (na == nb) return true
        // Also match suffix (last 9-10 digits) for local formats
        val da = na.filter { it.isDigit() }
        val db = nb.filter { it.isDigit() }
        val minLen = minOf(da.length, db.length)
        if (minLen < 7) return false
        // Compare last 9 digits or full if shorter
        val compareLen = minOf(9, minLen)
        return da.takeLast(compareLen) == db.takeLast(compareLen)
    }

    fun isPrivateOrHidden(number: String?): Boolean {
        if (number.isNullOrBlank()) return true
        val lower = number.lowercase().trim()
        return lower in setOf("private", "unknown", "withheld", "restricted", "-1", "-2", "anonymous")
    }
}
