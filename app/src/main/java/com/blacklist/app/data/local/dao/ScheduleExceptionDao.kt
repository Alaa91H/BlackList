package com.blacklist.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.blacklist.app.data.local.entity.ScheduleExceptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleExceptionDao {
    @Query("SELECT * FROM schedule_exceptions ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<ScheduleExceptionEntity>>

    @Query("SELECT * FROM schedule_exceptions ORDER BY createdAt ASC")
    suspend fun getAll(): List<ScheduleExceptionEntity>

    @Query("SELECT * FROM schedule_exceptions WHERE scheduleRuleId = :scheduleRuleId ORDER BY createdAt ASC")
    fun observeForSchedule(scheduleRuleId: Long): Flow<List<ScheduleExceptionEntity>>

    @Query("SELECT COUNT(*) FROM schedule_exceptions WHERE scheduleRuleId = :scheduleRuleId")
    suspend fun countForSchedule(scheduleRuleId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: ScheduleExceptionEntity): Long

    @Query("DELETE FROM schedule_exceptions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM schedule_exceptions WHERE scheduleRuleId = :scheduleRuleId")
    suspend fun deleteForSchedule(scheduleRuleId: Long)
}
