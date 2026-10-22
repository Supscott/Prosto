package com.jestk.browser.data.repository

import com.jestk.browser.data.local.dao.SettingDao
import com.jestk.browser.data.local.entity.SettingEntity
import com.jestk.browser.data.local.entity.SettingKeys
import com.jestk.browser.data.mapper.toBrowserSettings
import com.jestk.browser.domain.model.BrowserSettings
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.domain.model.ThemeMode
import com.jestk.browser.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SettingsRepositoryImpl(
    private val settingDao: SettingDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SettingsRepository {
    override fun getSettings(): Flow<BrowserSettings> =
        settingDao
            .getAllSettingsFlow()
            .map { list -> list.toBrowserSettings() }
            .flowOn(ioDispatcher)

    override suspend fun getCurrentSettings(): BrowserSettings =
        withContext(ioDispatcher) {
            val themeStr = settingDao.getSetting(SettingKeys.THEME_MODE)
            val searchStr = settingDao.getSetting(SettingKeys.SEARCH_ENGINE)
            val adBlockerStr = settingDao.getSetting(SettingKeys.AD_BLOCKER_ENABLED)
            val hiddenStr = settingDao.getSetting(SettingKeys.HIDDEN_SEARCH_PAGE_ENGINES)
            val openInAppsStr = settingDao.getSetting(SettingKeys.OPEN_IN_APPS_ENABLED)
            val autoUpdateStr = settingDao.getSetting(SettingKeys.AUTO_UPDATE_ENABLED)
            val vibrationStr = settingDao.getSetting(SettingKeys.VIBRATION_ENABLED)
            val skippedUpdateStr = settingDao.getSetting(SettingKeys.SKIPPED_UPDATE_VERSION)

            val theme = themeStr?.let { runCatching { ThemeMode.valueOf(it) }.getOrDefault(ThemeMode.SYSTEM) } ?: ThemeMode.SYSTEM
            val search =
                searchStr?.let { runCatching { SearchEngine.valueOf(it) }.getOrDefault(SearchEngine.DUCKDUCKGO) } ?: SearchEngine.DUCKDUCKGO
            val adBlocker = adBlockerStr?.toBooleanStrictOrNull() ?: true
            val openInApps = openInAppsStr?.toBooleanStrictOrNull() ?: true
            val autoUpdate = autoUpdateStr?.toBooleanStrictOrNull() ?: true
            val vibration = vibrationStr?.toBooleanStrictOrNull() ?: true
            val skippedUpdate = skippedUpdateStr?.takeIf { it.isNotBlank() }
            val hiddenEngines =
                hiddenStr
                    ?.takeIf { it.isNotBlank() }
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    ?.toSet() ?: emptySet()

            BrowserSettings(
                themeMode = theme,
                searchEngine = search,
                isAdBlockerEnabled = adBlocker,
                hiddenSearchPageEngineIds = hiddenEngines,
                isOpenInAppsEnabled = openInApps,
                isAutoUpdateEnabled = autoUpdate,
                isVibrationEnabled = vibration,
                skippedUpdateVersion = skippedUpdate,
            )
        }

    override suspend fun setThemeMode(mode: ThemeMode) =
        withContext(ioDispatcher) {
            settingDao.setSetting(SettingEntity(SettingKeys.THEME_MODE, mode.name))
        }

    override suspend fun setSearchEngine(engine: SearchEngine) =
        withContext(ioDispatcher) {
            settingDao.setSetting(SettingEntity(SettingKeys.SEARCH_ENGINE, engine.name))
        }

    override suspend fun setAdBlockerEnabled(enabled: Boolean) =
        withContext(ioDispatcher) {
            settingDao.setSetting(SettingEntity(SettingKeys.AD_BLOCKER_ENABLED, enabled.toString()))
        }

    override suspend fun setHiddenSearchPageEngineIds(hiddenIds: Set<String>) =
        withContext(ioDispatcher) {
            val serialized = hiddenIds.filter { it.isNotBlank() }.joinToString(",")
            settingDao.setSetting(SettingEntity(SettingKeys.HIDDEN_SEARCH_PAGE_ENGINES, serialized))
        }

    override suspend fun setOpenInAppsEnabled(enabled: Boolean) =
        withContext(ioDispatcher) {
            settingDao.setSetting(SettingEntity(SettingKeys.OPEN_IN_APPS_ENABLED, enabled.toString()))
        }

    override suspend fun setAutoUpdateEnabled(enabled: Boolean) =
        withContext(ioDispatcher) {
            settingDao.setSetting(SettingEntity(SettingKeys.AUTO_UPDATE_ENABLED, enabled.toString()))
        }

    override suspend fun setVibrationEnabled(enabled: Boolean) =
        withContext(ioDispatcher) {
            settingDao.setSetting(SettingEntity(SettingKeys.VIBRATION_ENABLED, enabled.toString()))
        }

    override suspend fun setSkippedUpdateVersion(version: String?) =
        withContext(ioDispatcher) {
            settingDao.setSetting(SettingEntity(SettingKeys.SKIPPED_UPDATE_VERSION, version.orEmpty()))
        }

    override fun getBlockedAdsCount(): Flow<Long> =
        settingDao
            .getSettingFlow(SettingKeys.KEY_BLOCKED_ADS_COUNT)
            .map { it?.toLongOrNull() ?: 0L }
            .flowOn(ioDispatcher)

    override fun getBlockedTrackersCount(): Flow<Long> =
        settingDao
            .getSettingFlow(SettingKeys.KEY_BLOCKED_TRACKERS_COUNT)
            .map { it?.toLongOrNull() ?: 0L }
            .flowOn(ioDispatcher)

    override suspend fun incrementBlockedAds(count: Long) =
        withContext(ioDispatcher) {
            settingDao.incrementBlockedAds(count)
        }

    override suspend fun incrementBlockedTrackers(count: Long) =
        withContext(ioDispatcher) {
            settingDao.incrementBlockedTrackers(count)
        }
}
