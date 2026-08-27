package com.blacklist.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Metadata that keeps an imported offline reputation list auditable on-device. */
@Entity(
    tableName = "offline_reputation_sources",
    indices = [Index(value = ["importedAt"])]
)
data class OfflineReputationSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceName: String,
    val sourceVersion: String?,
    val sourceUrl: String?,
    val fingerprintSha256: String,
    val entryCount: Int,
    val importedAt: Long = System.currentTimeMillis()
)
