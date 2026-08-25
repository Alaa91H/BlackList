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
    val activeProfileId: String = "custom", // custom, normal, focus, whitelist_only
    val themeMode: String = "SYSTEM", // SYSTEM, LIGHT, DARK
    val updatedAt: Long = System.currentTimeMillis()
)
