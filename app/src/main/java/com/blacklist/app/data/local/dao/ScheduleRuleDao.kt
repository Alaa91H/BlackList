package com.blacklist.app.data.local.dao

import androidx.room.*
import com.blacklist.app.data.local.entity.ScheduleRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleRuleDao {
    @Query("SELECT * FROM schedule_rules ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ScheduleRuleEntity>>

    @Query("SELECT * FROM schedule_rules WHERE isEnabled = 1")
    suspend fun getEnabled(): List<ScheduleRuleEntity>

    @Query("SELECT * FROM schedule_rules ORDER BY createdAt DESC")
    suspend fun getAll(): List<ScheduleRuleEntity>

    @Insert
    suspend fun insert(entity: ScheduleRuleEntity): Long

    @Update
    suspend fun update(entity: ScheduleRuleEntity)

    @Delete
    suspend fun delete(entity: ScheduleRuleEntity)

    @Query("DELETE FROM schedule_rules WHERE id = :id")
    suspend fun deleteById(id: Long)
}
