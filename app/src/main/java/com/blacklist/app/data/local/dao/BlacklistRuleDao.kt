package com.blacklist.app.data.local.dao

import androidx.room.*
import com.blacklist.app.data.local.entity.BlacklistRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlacklistRuleDao {
    @Query("SELECT * FROM blacklist_rules ORDER BY priority ASC, createdAt DESC")
    fun observeAll(): Flow<List<BlacklistRuleEntity>>

    @Query("SELECT * FROM blacklist_rules WHERE isEnabled = 1 ORDER BY priority ASC")
    suspend fun getEnabled(): List<BlacklistRuleEntity>

    @Query("SELECT * FROM blacklist_rules ORDER BY priority ASC")
    suspend fun getAll(): List<BlacklistRuleEntity>

    @Insert
    suspend fun insert(entity: BlacklistRuleEntity): Long

    @Update
    suspend fun update(entity: BlacklistRuleEntity)

    @Delete
    suspend fun delete(entity: BlacklistRuleEntity)

    @Query("DELETE FROM blacklist_rules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE blacklist_rules SET isEnabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)
}
