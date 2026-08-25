package com.blacklist.app.data.local.dao

import androidx.room.*
import com.blacklist.app.data.local.entity.CallerReputationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CallerReputationDao {
    @Query("SELECT * FROM caller_reputation WHERE normalizedNumber = :normalized LIMIT 1")
    suspend fun find(normalized: String): CallerReputationEntity?

    @Query("SELECT * FROM caller_reputation ORDER BY riskScore DESC")
    fun observeAll(): Flow<List<CallerReputationEntity>>

    @Query("SELECT * FROM caller_reputation ORDER BY riskScore DESC LIMIT 50")
    suspend fun topRisk(): List<CallerReputationEntity>

    @Query("SELECT * FROM caller_reputation")
    suspend fun getAll(): List<CallerReputationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CallerReputationEntity)

    @Query("UPDATE caller_reputation SET blockedCalls = blockedCalls + 1, totalCalls = totalCalls + 1, lastSeen = :now WHERE normalizedNumber = :normalized")
    suspend fun incrementBlocked(normalized: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE caller_reputation SET allowedCalls = allowedCalls + 1, totalCalls = totalCalls + 1, lastSeen = :now WHERE normalizedNumber = :normalized")
    suspend fun incrementAllowed(normalized: String, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM caller_reputation WHERE normalizedNumber = :normalized")
    suspend fun delete(normalized: String)
}
