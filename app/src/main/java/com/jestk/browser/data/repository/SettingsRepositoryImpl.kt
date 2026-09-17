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
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SettingsRepository {

    override fun getSettings(): Flow<BrowserSettings> =
        settingDao.getAllSettingsFlow()
            .map { list -> list.toBrowserSettings() }
            .flowOn(ioDispatcher)

    override suspend fun getCurrentSettings(): BrowserSettings = withContext(ioDispatcher) {
        val themeStr = settingDao.getSetting(SettingKeys.THEME_MODE)
        val searchStr = settingDao.getSetting(SettingKeys.SEARCH_ENGINE)
        val adBlockerStr = settingDao.getSetting(SettingKeys.AD_BLOCKER_ENABLED)
        val menuExpandedStr = settingDao.getSetting(SettingKeys.MENU_EXPANDED_ON_WEB)

        val theme = themeStr?.let { runCatching { ThemeMode.valueOf(it) }.getOrDefault(ThemeMode.SYSTEM) } ?: ThemeMode.SYSTEM
        val search = searchStr?.let { runCatching { SearchEngine.valueOf(it) }.getOrDefault(SearchEngine.DUCKDUCKGO) } ?: SearchEngine.DUCKDUCKGO
        val adBlocker = adBlockerStr?.toBooleanStrictOrNull() ?: true
        val menuExpanded = menuExpandedStr?.toBooleanStrictOrNull() ?: false

        BrowserSettings(
            themeMode = theme,
            searchEngine = search,
            isAdBlockerEnabled = adBlocker,
            isMenuExpandedOnWeb = menuExpanded
        )
    }

    override suspend fun setThemeMode(mode: ThemeMode) = withContext(ioDispatcher) {
        settingDao.setSetting(SettingEntity(SettingKeys.THEME_MODE, mode.name))
    }

    override suspend fun setSearchEngine(engine: SearchEngine) = withContext(ioDispatcher) {
        settingDao.setSetting(SettingEntity(SettingKeys.SEARCH_ENGINE, engine.name))
    }

    override suspend fun setAdBlockerEnabled(enabled: Boolean) = withContext(ioDispatcher) {
        settingDao.setSetting(SettingEntity(SettingKeys.AD_BLOCKER_ENABLED, enabled.toString()))
    }

    override suspend fun setMenuExpandedOnWeb(expanded: Boolean) = withContext(ioDispatcher) {
        settingDao.setSetting(SettingEntity(SettingKeys.MENU_EXPANDED_ON_WEB, expanded.toString()))
    }
}
