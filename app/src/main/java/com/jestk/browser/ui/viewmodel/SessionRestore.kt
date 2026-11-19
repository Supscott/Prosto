package com.jestk.browser.ui.viewmodel

import android.util.Log
import com.jestk.browser.domain.model.FeedbackPromptState
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.domain.repository.SearchEngineRepository
import com.jestk.browser.domain.repository.SettingsRepository
import com.jestk.browser.domain.repository.WorkspaceRepository
import com.jestk.browser.ui.viewmodel.tabs.TabSessionSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

private const val TAG = "BrowserViewModel"

internal fun calculateFeedbackCardVisible(
    settings: com.jestk.browser.domain.model.BrowserSettings,
    currentTimeProvider: () -> Long,
): Boolean {
    return when (settings.feedbackPromptState) {
        FeedbackPromptState.DISMISSED -> false
        FeedbackPromptState.SNOOZED -> currentTimeProvider() >= settings.feedbackPromptSnoozedUntil
        FeedbackPromptState.VISIBLE -> true
    }
}

internal suspend fun restoreSession(
    workspaceRepository: WorkspaceRepository?,
    settingsRepository: SettingsRepository,
    searchEngineRepository: SearchEngineRepository?,
    state: MutableStateFlow<BrowserUiState>,
    currentTimeProvider: () -> Long,
    sessionSync: TabSessionSync,
) {
    Log.d(TAG, "Restoring session")
    workspaceRepository?.ensureDefaultWorkspaces()
    val initialSettings = settingsRepository.getCurrentSettings()
    state.update { it.copy(isFeedbackCardVisible = calculateFeedbackCardVisible(initialSettings, currentTimeProvider)) }
    val currentEngines =
        if (searchEngineRepository != null) {
            searchEngineRepository.ensureDefaultEnginesSeeded()
            val engines = searchEngineRepository.getAllSearchEngines()
            if (engines.isNotEmpty()) engines else SearchEngine.DEFAULT_ENGINES
        } else {
            SearchEngine.DEFAULT_ENGINES
        }
    sessionSync.restoreSession(initialSettings, currentEngines)
}
