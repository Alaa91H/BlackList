package com.blacklist.app.domain.sharing

/**
 * Extracts a small set of phone-like text fragments from an explicit ACTION_SEND payload.
 * It never accesses the clipboard, a ContentResolver, contacts, call history, messages or storage.
 * Candidates are only suggestions; a caller must normalize and confirm one before it is persisted.
 */
object SharedPhoneNumberExtractor {
    const val MAX_INPUT_LENGTH = 2_048
    const val MAX_CANDIDATES = 8

    private val phoneLikeCandidate = Regex(
        pattern = "(?<![A-Za-z0-9])(?:\\+|00)?[0-9][0-9 .()\\-]{4,30}[0-9](?![A-Za-z0-9])"
    )

    fun extract(sharedText: CharSequence?): List<String> {
        val bounded = sharedText?.toString()?.take(MAX_INPUT_LENGTH).orEmpty()
        if (bounded.isBlank()) return emptyList()
        return phoneLikeCandidate.findAll(bounded)
            .map { it.value.trim() }
            .filter { candidate -> candidate.count(Char::isDigit) in 7..20 }
            .distinct()
            .take(MAX_CANDIDATES)
            .toList()
    }
}
