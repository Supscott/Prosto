package com.jestk.browser.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jestk.browser.data.local.entity.SettingEntity
import com.jestk.browser.data.local.entity.SettingKeys
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingDao {
    @Query("SELECT value FROM settings WHERE `key` = :key LIMIT 1")
    fun getSettingFlow(key: String): Flow<String?>

    @Query("SELECT value FROM settings WHERE `key` = :key LIMIT 1")
    suspend fun getSetting(key: String): String?

    @Query("SELECT * FROM settings")
    fun getAllSettingsFlow(): Flow<List<SettingEntity>>

    @Query("SELECT * FROM settings")
    suspend fun getAllSettings(): List<SettingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSetting(setting: SettingEntity)

    @Query("DELETE FROM settings WHERE `key` = :key")
    suspend fun deleteSetting(key: String)

    @Query("DELETE FROM settings")
    suspend fun clearAllSettings()

    @Query("INSERT OR IGNORE INTO settings (`key`, value) VALUES (:key, '0')")
    suspend fun initSettingIfNotExists(key: String)

    @Query("UPDATE settings SET value = CAST(CAST(value AS INTEGER) + :count AS TEXT) WHERE `key` = :key")
    suspend fun updateIncrementSetting(key: String, count: Long): Int

    suspend fun incrementBlockedAds(count: Long = 1) {
        initSettingIfNotExists(SettingKeys.KEY_BLOCKED_ADS_COUNT)
        updateIncrementSetting(SettingKeys.KEY_BLOCKED_ADS_COUNT, count)
    }

    suspend fun incrementBlockedTrackers(count: Long = 1) {
        initSettingIfNotExists(SettingKeys.KEY_BLOCKED_TRACKERS_COUNT)
        updateIncrementSetting(SettingKeys.KEY_BLOCKED_TRACKERS_COUNT, count)
    }

    fun getBlockedAdsCountFlow(): Flow<String?> = getSettingFlow(SettingKeys.KEY_BLOCKED_ADS_COUNT)

    fun getBlockedTrackersCountFlow(): Flow<String?> = getSettingFlow(SettingKeys.KEY_BLOCKED_TRACKERS_COUNT)
}
