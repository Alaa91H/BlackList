package com.blacklist.app.data.local.dao

import androidx.room.*
import com.blacklist.app.data.local.entity.SecurityEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SecurityEventDao {
    @Query("SELECT * FROM security_events ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<SecurityEventEntity>>

    @Query("SELECT * FROM security_events ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recent(limit: Int = 50): List<SecurityEventEntity>

    @Insert
    suspend fun insert(event: SecurityEventEntity): Long

    @Query("DELETE FROM security_events WHERE timestamp < :before")
    suspend fun prune(before: Long)

    @Query("DELETE FROM security_events")
    suspend fun clearAll()
}
