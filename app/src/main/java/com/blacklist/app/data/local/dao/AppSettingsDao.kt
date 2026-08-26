package com.blacklist.app.data.local.dao

import androidx.room.*
import com.blacklist.app.data.local.entity.AppSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppSettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1")
    fun observe(): Flow<AppSettingsEntity?>

    @Query("SELECT * FROM app_settings WHERE id = 1")
    suspend fun get(): AppSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AppSettingsEntity)

    @Query("UPDATE app_settings SET blockUnknown = :v, updatedAt = :ts WHERE id = 1")
    suspend fun setBlockUnknown(v: Boolean, ts: Long = System.currentTimeMillis())

    @Query("UPDATE app_settings SET blockPrivate = :v, updatedAt = :ts WHERE id = 1")
    suspend fun setBlockPrivate(v: Boolean, ts: Long = System.currentTimeMillis())

    @Query("UPDATE app_settings SET blockAllExceptWhitelist = :v, updatedAt = :ts WHERE id = 1")
    suspend fun setBlockAllExceptWhitelist(v: Boolean, ts: Long = System.currentTimeMillis())

    @Query("UPDATE app_settings SET emergencyCallbackGraceUntil = :expiry, updatedAt = :ts WHERE id = 1")
    suspend fun setEmergencyCallbackGraceUntil(expiry: Long, ts: Long = System.currentTimeMillis())
}
