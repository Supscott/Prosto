package com.jestk.browser.ui.viewmodel.events

import com.jestk.browser.domain.model.BrowserSettings
import com.jestk.browser.domain.repository.SettingsRepository
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.ui.viewmodel.DismissOpenInAppExclusionPrompt
import com.jestk.browser.ui.viewmodel.ExcludeOpenInAppSite
import com.jestk.browser.ui.viewmodel.IncludeOpenInAppSite
import com.jestk.browser.ui.viewmodel.RecordOpenInAppDismissal
import com.jestk.browser.ui.viewmodel.SettingsUiEvent
import com.jestk.browser.ui.viewmodel.tabs.OpenInAppExclusions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Per-site "don't offer to open in the app" events: updates the UI state and persists the result. */
internal class OpenInAppExclusionHandler(
    private val settingsRepository: SettingsRepository,
    private val updateUiState: ((BrowserUiState) -> BrowserUiState) -> Unit,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
) {
    fun handle(event: SettingsUiEvent) {
        when (event) {
            is RecordOpenInAppDismissal -> change { OpenInAppExclusions.recordDismissal(it, event.domain) }
            is ExcludeOpenInAppSite -> change(clearPrompt = true) { OpenInAppExclusions.exclude(it, event.domain) }
            is IncludeOpenInAppSite -> change { OpenInAppExclusions.include(it, event.site) }
            is DismissOpenInAppExclusionPrompt ->
                change(clearPrompt = true) { OpenInAppExclusions.resetDismissals(it, event.domain) }
            else -> Unit
        }
    }

    private fun change(clearPrompt: Boolean = false, transform: (BrowserSettings) -> BrowserSettings) {
        var updated: BrowserSettings? = null
        updateUiState { state ->
            val settings = transform(state.settings)
            updated = settings
            val prompt = if (clearPrompt) null else state.openInAppExclusionPrompt
            state.copy(settings = settings, openInAppExclusionPrompt = prompt)
        }
        val settings = updated ?: return
        scope.launch(ioDispatcher) {
            settingsRepository.setOpenInAppExcludedSites(settings.openInAppExcludedSites)
            settingsRepository.setOpenInAppDismissCounts(settings.openInAppDismissCounts)
        }
    }
}
