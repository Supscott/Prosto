package com.jestk.browser.domain.repository

import com.jestk.browser.domain.model.BrowserSettings
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun getSettings(): Flow<BrowserSettings>

    suspend fun getCurrentSettings(): BrowserSettings

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setSearchEngine(engine: SearchEngine)

    suspend fun setAdBlockerEnabled(enabled: Boolean)

    suspend fun setHiddenSearchPageEngineIds(hiddenIds: Set<String>)

    suspend fun setOpenInAppsEnabled(enabled: Boolean)

    suspend fun setAutoUpdateEnabled(enabled: Boolean)

    suspend fun setVibrationEnabled(enabled: Boolean)

    suspend fun setGrayUnloadedTabsEnabled(enabled: Boolean)

    suspend fun setSkippedUpdateVersion(version: String?)

    fun getBlockedAdsCount(): Flow<Long>

    fun getBlockedTrackersCount(): Flow<Long>

    suspend fun incrementBlockedAds(count: Long = 1)

    suspend fun incrementBlockedTrackers(count: Long = 1)
}
