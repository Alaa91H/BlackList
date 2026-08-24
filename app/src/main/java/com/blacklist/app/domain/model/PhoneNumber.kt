package com.blacklist.app.domain.model

/**
 * Unified phone number representation - single source for all matching.
 * E.164 is canonical if libphonenumber succeeds, otherwise digits fallback.
 */
data class PhoneNumber(
    val raw: String,
    val normalized: String, // +digits or digits
    val e164: String?, // e.g. +49123456789 if parsed
    val countryIso: String?, // e.g. DE, US
    val nationalNumber: String?, // digits without country
    val presentation: Presentation
) {
    val digitsOnly: String get() = normalized.filter { it.isDigit() }
    val isPossible: Boolean get() = digitsOnly.length in 7..15
}

enum class Presentation {
    ALLOWED, // normal number
    RESTRICTED, // hidden/private
    UNKNOWN, // null/empty
    PAYPHONE // rarely used
}
