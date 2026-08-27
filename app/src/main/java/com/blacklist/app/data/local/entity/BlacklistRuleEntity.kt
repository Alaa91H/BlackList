package com.blacklist.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Unified blacklist rule covering exact/prefix/range/country/hidden/unknown.
 * Superset of old blocked_numbers (which stored only exact). Migration copies old data as EXACT.
 */
@Entity(tableName = "blacklist_rules")
data class BlacklistRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val isEnabled: Boolean = true,
    val priority: Int = 30, // Default EXPLICIT_BLOCK
    val ruleType: String, // EXACT, PREFIX, RANGE, COUNTRY, HIDDEN, UNKNOWN
    val pattern: String? = null, // for EXACT/PREFIX: normalized number/prefix
    val startNumber: String? = null, // for RANGE
    val endNumber: String? = null, // for RANGE
    val countryIso: String? = null, // for COUNTRY (e.g. DE)
    val displayName: String? = null,
    val showNotification: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val TYPE_EXACT = "EXACT"
        const val TYPE_PREFIX = "PREFIX"
        const val TYPE_SUFFIX = "SUFFIX"
        const val TYPE_CONTAINS = "CONTAINS"
        const val TYPE_RANGE = "RANGE"
        const val TYPE_COUNTRY = "COUNTRY"
        const val TYPE_HIDDEN = "HIDDEN"
        const val TYPE_UNKNOWN = "UNKNOWN"

        /** Internal temporary-firewall types (not user-selectable). See TemporaryFirewall for encoding. */
        const val TYPE_TEMP_BLOCK_ALL = "TEMP_BLOCK_ALL"
        const val TYPE_TEMP_ALLOW = "TEMP_ALLOW"
        const val TYPE_TEMP_OUTBOUND_CALLBACK = "TEMP_OUTBOUND_CALLBACK"
        const val TYPE_TEMP_BLOCK_EXACT = "TEMP_BLOCK_EXACT"

        /** All user-selectable match types (HIDDEN/UNKNOWN are policy toggles, not patterns; TEMP_* internal). */
        val USER_TYPES = listOf(TYPE_EXACT, TYPE_PREFIX, TYPE_SUFFIX, TYPE_CONTAINS, TYPE_RANGE, TYPE_COUNTRY)
    }
}
