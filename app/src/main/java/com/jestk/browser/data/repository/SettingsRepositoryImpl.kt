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
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

class SettingsRepositoryImpl(
    private val settingDao: SettingDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SettingsRepository {
    override fun getSettings(): Flow<BrowserSettings> =
        settingDao
            .getAllSettingsFlow()
            .onStart { migrateCleanPlayerOnce() }
            .map { list -> list.toBrowserSettings() }
            .flowOn(ioDispatcher)

    override suspend fun getCurrentSettings(): BrowserSettings =
        withContext(ioDispatcher) {
            migrateCleanPlayerOnce()
            settingDao.getAllSettings().toBrowserSettings()
        }

    /** One-time reset: the clean player was turned off for everyone once; later opt-ins stick. */
    private suspend fun migrateCleanPlayerOnce() {
        if (settingDao.getSetting(SettingKeys.CLEAN_PLAYER_FORCED_OFF_V1) == "1") return
        settingDao.setSetting(SettingEntity(SettingKeys.CLEAN_YOUTUBE_PLAYER_ENABLED, "false"))
        settingDao.setSetting(SettingEntity(SettingKeys.CLEAN_PLAYER_FORCED_OFF_V1, "1"))
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

    override suspend fun setAdRedirectProtectionEnabled(enabled: Boolean) =
        withContext(ioDispatcher) {
            settingDao.setSetting(SettingEntity(SettingKeys.AD_REDIRECT_PROTECTION_ENABLED, enabled.toString()))
        }

    override suspend fun setAutoConsentEnabled(enabled: Boolean) =
        withContext(ioDispatcher) {
            settingDao.setSetting(SettingEntity(SettingKeys.AUTO_CONSENT_ENABLED, enabled.toString()))
        }

    override suspend fun setCleanYouTubePlayerEnabled(enabled: Boolean) =
        withContext(ioDispatcher) {
            settingDao.setSetting(SettingEntity(SettingKeys.CLEAN_YOUTUBE_PLAYER_ENABLED, enabled.toString()))
        }

    override suspend fun setTrackingProtectionEnabled(enabled: Boolean) =
        withContext(ioDispatcher) {
            settingDao.setSetting(SettingEntity(SettingKeys.TRACKING_PROTECTION_ENABLED, enabled.toString()))
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

    override suspend fun setGrayUnloadedTabsEnabled(enabled: Boolean) =
        withContext(ioDispatcher) {
            settingDao.setSetting(SettingEntity(SettingKeys.GRAY_UNLOADED_TABS_ENABLED, enabled.toString()))
        }

    override suspend fun setSkippedUpdateVersion(version: String?) =
        withContext(ioDispatcher) {
            settingDao.setSetting(SettingEntity(SettingKeys.SKIPPED_UPDATE_VERSION, version.orEmpty()))
        }

    override suspend fun setFeedbackPromptState(state: com.jestk.browser.domain.model.FeedbackPromptState) =
        withContext(ioDispatcher) {
            settingDao.setSetting(SettingEntity(SettingKeys.FEEDBACK_PROMPT_STATE, state.name))
        }

    override suspend fun snoozeFeedbackPrompt(snoozedUntil: Long) =
        withContext(ioDispatcher) {
            settingDao.setSetting(SettingEntity(SettingKeys.FEEDBACK_PROMPT_STATE, com.jestk.browser.domain.model.FeedbackPromptState.SNOOZED.name))
            settingDao.setSetting(SettingEntity(SettingKeys.FEEDBACK_PROMPT_SNOOZED_UNTIL, snoozedUntil.toString()))
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
