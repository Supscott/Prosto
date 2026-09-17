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
    suspend fun setMenuExpandedOnWeb(expanded: Boolean)
}
