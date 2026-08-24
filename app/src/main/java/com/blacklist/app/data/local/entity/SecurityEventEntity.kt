package com.blacklist.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "security_events")
data class SecurityEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val severity: String, // LOW, MEDIUM, HIGH, CRITICAL
    val title: String,
    val description: String,
    val relatedNumber: String? = null,
    val campaignId: String? = null,
    val riskScore: Int? = null,
    val metadata: String? = null // JSON
)
