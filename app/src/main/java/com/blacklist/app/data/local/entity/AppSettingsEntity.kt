package com.blacklist.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1, // singleton
    val blockUnknown: Boolean = false,
    /** When enabled with blockUnknown, screen eligible unknown callers quietly rather than rejecting them. */
    val silenceUnknown: Boolean = false,
    val blockPrivate: Boolean = true,
    /** When enabled with blockPrivate, screen private callers quietly rather than rejecting them. */
    val silencePrivate: Boolean = false,
    /** Optional policy for callers outside the device's configured home region. */
    val blockInternational: Boolean = false,
    /** When enabled with blockInternational, mute international callers rather than rejecting them. */
    val silenceInternational: Boolean = false,
    val blockAllExceptWhitelist: Boolean = false,
    val showBlockedNotification: Boolean = true,
    /** Keeps blocked-call history out of Android's shared call log while preserving BlackList's local log. */
    val hideBlockedCallsFromSystemLog: Boolean = false,
    /** Whole-day retention for BlackList's own blocked-call history; zero keeps it indefinitely. */
    val blockedLogRetentionDays: Long = 0L,
    /** Enables a short local-only callback window after the user dials a non-emergency number. */
    val allowOutboundCallbackGrace: Boolean = false,
    val activeProfileId: String = "custom", // custom, normal, focus, whitelist_only
    val themeMode: String = "SYSTEM", // SYSTEM, LIGHT, DARK
    /** UTC expiry for the short local allowance after an outgoing emergency call. */
    val emergencyCallbackGraceUntil: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis()
)
