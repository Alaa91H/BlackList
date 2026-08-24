package com.blacklist.app.data.local.dao

import androidx.room.*
import com.blacklist.app.data.local.entity.BlockedNumberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedNumberDao {
    @Query("SELECT * FROM blocked_numbers ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BlockedNumberEntity>>

    @Query("SELECT * FROM blocked_numbers ORDER BY createdAt DESC")
    suspend fun getAll(): List<BlockedNumberEntity>

    @Query("SELECT * FROM blocked_numbers WHERE normalizedNumber = :normalized LIMIT 1")
    suspend fun findByNormalized(normalized: String): BlockedNumberEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_numbers WHERE normalizedNumber = :normalized)")
    suspend fun exists(normalized: String): Boolean

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: BlockedNumberEntity): Long

    @Delete
    suspend fun delete(entity: BlockedNumberEntity)

    @Query("DELETE FROM blocked_numbers WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM blocked_numbers")
    suspend fun clearAll()

    @Query("UPDATE blocked_numbers SET showNotification = :enabled WHERE id = :id")
    suspend fun setNotificationEnabled(id: Long, enabled: Boolean)
}
