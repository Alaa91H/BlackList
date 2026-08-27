package com.blacklist.app.data.local.dao

import androidx.room.*
import com.blacklist.app.data.local.entity.BlockedCallLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedCallLogDao {
    @Query("SELECT * FROM blocked_call_logs ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<BlockedCallLogEntity>>

    @Query("SELECT * FROM blocked_call_logs ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getPaged(limit: Int, offset: Int): List<BlockedCallLogEntity>

    @Query("SELECT COUNT(*) FROM blocked_call_logs")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM blocked_call_logs")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM blocked_call_logs WHERE timestamp >= :since")
    suspend fun countSince(since: Long): Int

    @Insert
    suspend fun insert(entity: BlockedCallLogEntity): Long

    @Query("DELETE FROM blocked_call_logs")
    suspend fun clearAll()

    @Query("DELETE FROM blocked_call_logs WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Deletes only BlackList-owned history older than the exclusive local cutoff. */
    @Query("DELETE FROM blocked_call_logs WHERE timestamp < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long): Int
}
