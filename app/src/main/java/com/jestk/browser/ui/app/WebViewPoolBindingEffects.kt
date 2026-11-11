package com.jestk.browser.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.jestk.browser.engine.WebViewPool
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.ui.viewmodel.BrowserViewModel
import com.jestk.browser.ui.views.WebViewClientDeps
import com.jestk.browser.ui.views.bindWebViewClients
import com.jestk.browser.ui.views.openCleanPlayerIfIntercepted

@Composable
internal fun WebViewPoolBindingEffects(
    uiState: BrowserUiState,
    webViewPool: WebViewPool,
    viewModel: BrowserViewModel,
    clientDeps: WebViewClientDeps,
) {
    // Automatically destroy closed WebViews when tabs are closed
    val currentTabIds =
        androidx.compose.runtime.remember(uiState.tabs) {
            uiState.tabs.map { it.id }.toSet()
        }
    LaunchedEffect(currentTabIds) {
        if (currentTabIds.isNotEmpty()) {
            webViewPool.retainTabs(currentTabIds)
        }
    }

    // Connect WebViewPool download listener and web client initializer to ViewModel
    LaunchedEffect(webViewPool, viewModel) {
        webViewPool.onDownloadStarted = { item ->
            viewModel.onDownloadStarted(item)
        }
        webViewPool.isAdBlockerEnabled = { viewModel.uiState.value.settings.isAdBlockerEnabled }
        webViewPool.isCleanPlayerEnabled = { viewModel.uiState.value.settings.isCleanYouTubePlayerEnabled }
        webViewPool.onCleanPlayerRequested = { url -> openCleanPlayerIfIntercepted(url, clientDeps) }
        webViewPool.onWebViewCreated = { tabId, wv ->
            if (wv.webChromeClient == null) {
                bindWebViewClients(wv, tabId, clientDeps)
            }
        }
    }
}
