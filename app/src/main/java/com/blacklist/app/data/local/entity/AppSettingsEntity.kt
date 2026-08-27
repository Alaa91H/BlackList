package com.blacklist.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1, // singleton
    val blockUnknown: Boolean = false,
    val blockPrivate: Boolean = true,
    val blockAllExceptWhitelist: Boolean = false,
    val showBlockedNotification: Boolean = true,
    /** Keeps blocked-call history out of Android's shared call log while preserving BlackList's local log. */
    val hideBlockedCallsFromSystemLog: Boolean = false,
    /** Enables a short local-only callback window after the user dials a non-emergency number. */
    val allowOutboundCallbackGrace: Boolean = false,
    val activeProfileId: String = "custom", // custom, normal, focus, whitelist_only
    val themeMode: String = "SYSTEM", // SYSTEM, LIGHT, DARK
    /** UTC expiry for the short local allowance after an outgoing emergency call. */
    val emergencyCallbackGraceUntil: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis()
)
