package com.blacklist.app.domain.model

import com.blacklist.app.data.local.entity.AppSettingsEntity

/**
 * Curated local protection presets. A preset only changes existing policy fields;
 * emergency safeguards, temporary allows, and explicit whitelists always retain precedence.
 */
data class ProtectionProfilePreset(
    val id: String,
    val blockUnknown: Boolean,
    val blockPrivate: Boolean,
    val blockAllExceptWhitelist: Boolean
) {
    fun applyTo(settings: AppSettingsEntity): AppSettingsEntity = settings.copy(
        blockUnknown = blockUnknown,
        blockPrivate = blockPrivate,
        blockAllExceptWhitelist = blockAllExceptWhitelist,
        activeProfileId = id,
        updatedAt = System.currentTimeMillis()
    )
}

object ProtectionProfiles {
    const val CUSTOM = "custom"
    const val NORMAL = "normal"
    const val FOCUS = "focus"
    const val WHITELIST_ONLY = "whitelist_only"
    val presets = listOf(
        ProtectionProfilePreset(NORMAL, blockUnknown = false, blockPrivate = true, blockAllExceptWhitelist = false),
        ProtectionProfilePreset(FOCUS, blockUnknown = true, blockPrivate = true, blockAllExceptWhitelist = false),
        ProtectionProfilePreset(WHITELIST_ONLY, blockUnknown = false, blockPrivate = true, blockAllExceptWhitelist = true)
    )

    fun byId(id: String): ProtectionProfilePreset? = presets.firstOrNull { it.id == id }
}
