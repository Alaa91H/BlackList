package com.blacklist.app.domain.model

enum class BlockReason {
    BLACKLIST,
    UNKNOWN,
    PRIVATE,
    SCHEDULE,
    ALL_EXCEPT_WHITELIST;

    fun toEntityString(): String = name

    companion object {
        fun fromString(s: String): BlockReason = try { valueOf(s) } catch (_: Exception) { BLACKLIST }
    }
}
