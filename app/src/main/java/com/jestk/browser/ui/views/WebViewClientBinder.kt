package com.jestk.browser.ui.views

import android.graphics.Bitmap
import android.webkit.WebView
import com.jestk.browser.engine.FaviconManager
import com.jestk.browser.engine.JestkWebChromeClient
import com.jestk.browser.engine.JestkWebViewClient
import com.jestk.browser.engine.WebViewPool
import com.jestk.browser.engine.WindowTransportHandler
import com.jestk.browser.ui.FileChooserHandler
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.ui.viewmodel.isSameDomain
import com.jestk.browser.util.UrlSecurityUtils
import com.jestk.browser.util.YouTubeUrlHelper
import com.jestk.browser.util.findActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * What the WebView clients need from the UI layer. Every member is read when a callback fires,
 * so a client installed once keeps seeing the current state, theme and settings.
 */
internal class WebViewClientDeps(
    val webViewPool: WebViewPool,
    val uiState: () -> BrowserUiState,
    val onEvent: (BrowserUiEvent) -> Unit,
    val isDark: () -> Boolean,
    val shouldInterceptForCleanPlayer: (String) -> Boolean,
    val scope: CoroutineScope,
)

/**
 * Installs the [JestkWebViewClient] and [JestkWebChromeClient] of a tab WebView.
 * This is the only place that builds them (review finding WV-3).
 */
internal fun bindWebViewClients(webView: WebView, tabId: String, deps: WebViewClientDeps) {
    val onEvent = deps.onEvent
    webView.tag = tabId
    webView.webViewClient = JestkWebViewClient(
        tabId = tabId,
        isAdBlockerEnabled = { deps.uiState().settings.isAdBlockerEnabled },
        onPageStarted = { id, url -> onEvent(BrowserUiEvent.OnPageStarted(id, url)) },
        onPageFinished = { id, url, title -> onEvent(BrowserUiEvent.OnPageFinished(id, url, title)) },
        onNavigationStateChanged = { id, canBack, canForward ->
            onEvent(BrowserUiEvent.OnNavigationStateChanged(id, canBack, canForward))
        },
        onSnapshotReady = { id, path -> onEvent(BrowserUiEvent.OnSnapshotCaptured(id, path)) },
        onThemeColorDetected = { id, color -> onEvent(BrowserUiEvent.OnThemeColorReceived(id, color)) },
        onThemeColorsDetected = { id, topColor, bottomColor ->
            onEvent(BrowserUiEvent.OnThemeColorsReceived(id, topColor, bottomColor))
        },
        isDarkTheme = deps.isDark,
        onTitleReceived = { id, title -> onEvent(BrowserUiEvent.OnTitleReceived(id, title)) },
        isOpenInAppsEnabled = { deps.uiState().settings.isOpenInAppsEnabled },
        onExternalAppOffered = { id, prompt, userInitiated ->
            onEvent(BrowserUiEvent.OnExternalAppOffered(id, prompt, userInitiated))
        },
        isTrackingProtectionEnabled = { deps.uiState().settings.isTrackingProtectionEnabled },
        onInsecureHttpWarning = { id, url -> onEvent(BrowserUiEvent.ShowInsecureHttpWarning(url = url, tabId = id)) },
        isInsecureHostAllowed = { urlOrHost ->
            val host = UrlSecurityUtils.extractHost(urlOrHost)
            host.isNotEmpty() && deps.uiState().allowedInsecureHosts.contains(host)
        },
        isCleanPlayerEnabled = { deps.uiState().settings.isCleanYouTubePlayerEnabled },
        onCleanPlayerRequested = { url -> openCleanPlayerIfIntercepted(url, deps) },
        onBlockedAdNavigation = { id, url -> onEvent(BrowserUiEvent.ShowBlockedAdNavigation(url = url, tabId = id)) },
        isAdRedirectProtectionEnabled = { deps.uiState().settings.isAdRedirectProtectionEnabled },
        isAllowedAdNavigation = { url -> deps.uiState().allowedAdNavigationUrls.contains(url) },
        isAutoConsentEnabled = { deps.uiState().settings.autoConsentEnabled },
        onRenderProcessGone = { id -> deps.webViewPool.discardCrashedWebView(id) },
    )
    webView.webChromeClient = JestkWebChromeClient(
        tabId = tabId,
        webView = webView,
        onProgressChanged = { id, progress -> onEvent(BrowserUiEvent.OnProgressChanged(id, progress)) },
        onTitleReceived = { id, title -> onEvent(BrowserUiEvent.OnTitleReceived(id, title)) },
        onFullscreenCustomViewChanged = { view, isFullscreen, orientation ->
            onEvent(BrowserUiEvent.SetFullscreenCustomView(view, isFullscreen, orientation))
        },
        onVideoPlayingStateChanged = { isPlaying -> onEvent(BrowserUiEvent.SetVideoPlaying(isPlaying)) },
        onFaviconReceived = { id, icon, pageUrl -> saveFavicon(webView, id, icon, pageUrl, deps) },
        onShowFileChooser = { callback, params ->
            val handler = webView.context.findActivity() as? FileChooserHandler
            handler?.openFileChooser(callback, params) ?: false
        },
        onCreateWindowRequest = { originView, _, isUserGesture, resultMsg ->
            val state = deps.uiState()
            val openerWorkspaceId = state.tabs.find { it.id == tabId }?.workspaceId ?: state.activeWorkspaceId
            val profileName = state.workspaces.find { it.id == openerWorkspaceId }?.profileName
            WindowTransportHandler.handleCreateWindow(
                resultMsg = resultMsg,
                context = webView.context,
                webViewPool = deps.webViewPool,
                openerUrl = originView?.url ?: webView.url,
                profileName = profileName,
                isUserGesture = isUserGesture,
                onPopupOpened = { onEvent(BrowserUiEvent.OpenPopupTab(it)) },
            )
        },
        onCloseWindowRequest = { id -> onEvent(BrowserUiEvent.CloseTab(id)) },
        onFullscreenOrientationChanged = { orientation ->
            onEvent(BrowserUiEvent.UpdateFullscreenOrientation(orientation))
        },
    )
}

/** Opens the clean player for [url] when the ViewModel intercepts it; returns whether it did. */
internal fun openCleanPlayerIfIntercepted(url: String, deps: WebViewClientDeps): Boolean {
    if (!deps.shouldInterceptForCleanPlayer(url)) return false
    val videoId = YouTubeUrlHelper.extractVideoId(url) ?: return false
    val startSeconds = YouTubeUrlHelper.extractStartTimeSeconds(url)
    deps.onEvent(BrowserUiEvent.OpenCleanPlayer(videoId, url, startSeconds))
    return true
}

/**
 * Stores a favicon reported by onReceivedIcon. A favicon whose page is on another domain than
 * the tab only goes to the domain cache (favicon domain guard, see CLAUDE.md).
 */
private fun saveFavicon(webView: WebView, tabId: String, icon: Bitmap, pageUrl: String?, deps: WebViewClientDeps) {
    val context = webView.context.applicationContext
    deps.scope.launch {
        val tabUrl = deps.uiState().tabs.find { it.id == tabId }?.url?.takeIf { it.isNotBlank() && it != "about:blank" }
        val webViewUrl = webView.url?.takeIf { it.isNotBlank() && it != "about:blank" }
        val effectiveUrl = pageUrl?.takeIf { it.isNotBlank() && it != "about:blank" } ?: tabUrl ?: webViewUrl

        if (tabUrl != null && effectiveUrl != null && !isSameDomain(tabUrl, effectiveUrl)) {
            FaviconManager.cacheFaviconForDomain(effectiveUrl, icon, context)
            return@launch
        }

        val path = FaviconManager.saveFavicon(context, tabId, icon, effectiveUrl)
        if (path != null) {
            deps.onEvent(BrowserUiEvent.UpdateFaviconUrl(tabId, path, effectiveUrl))
        }
    }
}
