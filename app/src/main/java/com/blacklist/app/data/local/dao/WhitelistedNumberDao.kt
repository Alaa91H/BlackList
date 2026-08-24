package com.blacklist.app.data.local.dao

import androidx.room.*
import com.blacklist.app.data.local.entity.WhitelistedNumberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WhitelistedNumberDao {
    @Query("SELECT * FROM whitelisted_numbers ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<WhitelistedNumberEntity>>

    @Query("SELECT * FROM whitelisted_numbers ORDER BY createdAt DESC")
    suspend fun getAll(): List<WhitelistedNumberEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM whitelisted_numbers WHERE normalizedNumber = :normalized)")
    suspend fun exists(normalized: String): Boolean

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: WhitelistedNumberEntity): Long

    @Delete
    suspend fun delete(entity: WhitelistedNumberEntity)

    @Query("DELETE FROM whitelisted_numbers WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM whitelisted_numbers")
    suspend fun clearAll()
}
