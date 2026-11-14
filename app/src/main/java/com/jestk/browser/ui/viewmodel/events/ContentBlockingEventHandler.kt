package com.jestk.browser.ui.viewmodel.events

import com.jestk.browser.domain.model.ProtectionLevel
import com.jestk.browser.engine.adblock.ContentBlockingControls
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.ui.viewmodel.ContentBlockingUiEvent
import com.jestk.browser.ui.viewmodel.SetProtectionLevel
import com.jestk.browser.ui.viewmodel.UpdateFilterListsNow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Protection level and filter-list updates. The controller owns the persisted level, so the UI
 * state only mirrors its flow. Choosing Strict saves the level (Standard stays active until the
 * lists are on disk) and then starts the download through the same worker as "Update now".
 */
internal class ContentBlockingEventHandler(
    private val controls: ContentBlockingControls?,
    private val updateUiState: ((BrowserUiState) -> BrowserUiState) -> Unit,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
) {
    fun observe() {
        val source = controls ?: return
        scope.launch(ioDispatcher) {
            source.status.collect { status -> updateUiState { it.copy(contentBlocking = status) } }
        }
    }

    fun handle(event: ContentBlockingUiEvent) {
        val source = controls ?: return
        when (event) {
            is SetProtectionLevel -> scope.launch(ioDispatcher) {
                source.setLevel(event.level)
                if (event.level == ProtectionLevel.STRICT) source.updateNow()
            }

            is UpdateFilterListsNow -> source.updateNow()
        }
    }
}
