package com.blacklist.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blocked_call_logs")
data class BlockedCallLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phoneNumber: String?, // null for private/hidden
    val displayName: String? = null,
    val reason: String, // e.g. BLACKLIST, UNKNOWN, PRIVATE, SCHEDULE, ALL_EXCEPT_WHITELIST
    val timestamp: Long = System.currentTimeMillis()
)
