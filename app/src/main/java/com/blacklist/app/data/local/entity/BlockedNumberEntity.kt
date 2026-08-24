package com.blacklist.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "blocked_numbers",
    indices = [Index(value = ["normalizedNumber"], unique = true)]
)
data class BlockedNumberEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rawNumber: String,
    val normalizedNumber: String,
    val displayName: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
