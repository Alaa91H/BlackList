package com.blacklist.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.blacklist.app.data.local.entity.OfflineReputationEntryEntity
import com.blacklist.app.data.local.entity.OfflineReputationSourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflineReputationDao {
    @Query("SELECT * FROM offline_reputation_sources ORDER BY importedAt DESC")
    fun observeSources(): Flow<List<OfflineReputationSourceEntity>>

    @Query("SELECT * FROM offline_reputation_sources ORDER BY importedAt DESC")
    suspend fun getSources(): List<OfflineReputationSourceEntity>

    @Query("SELECT * FROM offline_reputation_entries")
    fun observeEntries(): Flow<List<OfflineReputationEntryEntity>>

    @Query("SELECT * FROM offline_reputation_entries")
    suspend fun getEntries(): List<OfflineReputationEntryEntity>

    @Query("SELECT COUNT(*) FROM offline_reputation_entries")
    suspend fun countEntries(): Int

    @Query("SELECT COUNT(*) FROM offline_reputation_sources")
    suspend fun countSources(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM offline_reputation_sources WHERE fingerprintSha256 = :fingerprint)")
    suspend fun hasFingerprint(fingerprint: String): Boolean

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSource(source: OfflineReputationSourceEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEntries(entries: List<OfflineReputationEntryEntity>)

    @Query("DELETE FROM offline_reputation_sources WHERE id = :sourceId")
    suspend fun deleteSource(sourceId: Long)

    @Query("DELETE FROM offline_reputation_sources")
    suspend fun clearSources()
}
