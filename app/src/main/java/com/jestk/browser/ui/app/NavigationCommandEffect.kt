package com.jestk.browser.ui.app

import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.jestk.browser.engine.JestkWebChromeClient
import com.jestk.browser.engine.JestkWebViewClient
import com.jestk.browser.engine.WebViewPool
import com.jestk.browser.ui.viewmodel.BrowserViewModel
import com.jestk.browser.ui.viewmodel.NavigationCommand
import com.jestk.browser.util.findActivity
import kotlinx.coroutines.flow.collectLatest

@Composable
internal fun NavigationCommandEffect(
    viewModel: BrowserViewModel,
    webViewPool: WebViewPool,
) {
    val context = LocalContext.current
    // Handle navigation commands targeting WebView & Application
    LaunchedEffect(viewModel) {
        viewModel.navigationCommands.collectLatest { command ->
            if (command is NavigationCommand.ExitApp) {
                context.findActivity()?.finish()
                return@collectLatest
            }
            if (command is NavigationCommand.ReturnToCallerApp) {
                context.findActivity()?.moveTaskToBack(true)
                return@collectLatest
            }
            val activeId = viewModel.uiState.value.activeTabId ?: return@collectLatest
            val currentTab = viewModel.uiState.value.allTabs.find { it.id == activeId } ?: viewModel.uiState.value.tabs.find { it.id == activeId }
            val activeWorkspaceId = currentTab?.workspaceId ?: viewModel.uiState.value.activeWorkspaceId
            val profileName = viewModel.uiState.value.workspaces.find { it.id == activeWorkspaceId }?.profileName ?: activeWorkspaceId
            when (command) {
                is NavigationCommand.LoadUrl -> {
                    val webView = webViewPool.getWebView(activeId) ?: webViewPool.getOrCreateWebView(activeId, context, profileName = profileName)
                    val isFresh = (webView as? com.jestk.browser.engine.NestedWebView)?.loadedUrl.isNullOrEmpty()
                    if (!(isFresh && webViewPool.restoreSavedState(activeId, webView, command.url))) {
                        com.jestk.browser.engine.JestkWebViewClient.loadUrlOrHandleExternal(webView, command.url)
                    }
                }

                is NavigationCommand.GoBack -> {
                    webViewPool.getWebView(activeId)?.goBack()
                }

                is NavigationCommand.GoForward -> {
                    webViewPool.getWebView(activeId)?.goForward()
                }

                is NavigationCommand.Reload -> {
                    val webView = webViewPool.getWebView(activeId) ?: webViewPool.getOrCreateWebView(activeId, context, profileName = profileName)
                    if (webView.url.isNullOrEmpty() || webView.url == "about:blank") {
                        val url = viewModel.uiState.value.currentUrl
                        if (url.isNotEmpty() && url != "about:blank") {
                            webView.loadUrl(url)
                        }
                    } else {
                        webView.reload()
                    }
                }

                is NavigationCommand.StopLoading -> {
                    webViewPool.getWebView(activeId)?.stopLoading()
                }

                is NavigationCommand.ExitFullscreen -> {
                    val activeId = viewModel.uiState.value.activeTabId
                    val webView = activeId?.let { webViewPool.getWebView(it) }
                    (webView?.webChromeClient as? JestkWebChromeClient)?.onHideCustomView()
                    try {
                        webView?.evaluateJavascript(
                            "(function(){ if (document.exitFullscreen) document.exitFullscreen(); else if (document.webkitExitFullscreen) document.webkitExitFullscreen(); })()",
                            null,
                        )
                    } catch (_: Exception) {
                    }
                }

                is NavigationCommand.ExitApp, NavigationCommand.ReturnToCallerApp -> {
                    Unit
                }
            }
        }
    }
}
