package com.blacklist.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "offline_reputation_entries",
    foreignKeys = [
        ForeignKey(
            entity = OfflineReputationSourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sourceId"]),
        Index(value = ["normalizedNumber"]),
        Index(value = ["sourceId", "normalizedNumber"], unique = true)
    ]
)
data class OfflineReputationEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val normalizedNumber: String,
    val riskScore: Int,
    val category: String?,
    val createdAt: Long = System.currentTimeMillis()
)
