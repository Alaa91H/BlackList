package com.blacklist.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "caller_reputation",
    indices = [Index(value = ["normalizedNumber"], unique = true)]
)
data class CallerReputationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val normalizedNumber: String,
    val firstSeen: Long = System.currentTimeMillis(),
    var lastSeen: Long = System.currentTimeMillis(),
    var totalCalls: Int = 0,
    var blockedCalls: Int = 0,
    var allowedCalls: Int = 0,
    var spamScore: Int = 0,
    var riskScore: Int = 0,
    var level: String = "NEUTRAL",
    var userVerdict: String? = null, // TRUSTED/SPAM/NOT_SPAM
    var behaviorFlags: String = "", // comma separated
    val createdAt: Long = System.currentTimeMillis()
)
