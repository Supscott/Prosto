package com.jestk.browser.ui.viewmodel

import com.jestk.browser.engine.WebViewPool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal fun CoroutineScope.bindWebViewPool(
    pool: WebViewPool,
    state: MutableStateFlow<BrowserUiState>,
    downloads: DownloadCoordinator,
) {
    pool.onDownloadStarted = { item -> downloads.onDownloadStarted(item) }
    pool.onDownloadFailed = { fileName, message -> downloads.onDownloadFailed(fileName, message) }
    launch {
        pool.loadedTabIds.collect { loaded ->
            state.update { it.copy(loadedTabIds = loaded) }
        }
    }
    launch {
        pool.awaitingFirstPaintTabIds.collect { awaiting ->
            state.update { it.withAwaitingFirstPaint(awaiting) }
        }
    }
}

internal fun WebViewPool.unbindViewModel() {
    onDownloadStarted = null
    onDownloadFailed = null
}
