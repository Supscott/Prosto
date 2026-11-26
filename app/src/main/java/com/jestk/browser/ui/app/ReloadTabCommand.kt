package com.jestk.browser.ui.app

import android.webkit.WebView
import com.jestk.browser.engine.WebViewPool
import com.jestk.browser.engine.WebViewStateStore

/**
 * Reloads [tabId] whether or not it is the active tab.
 * - A live WebView with a page reloads in place.
 * - A live WebView that never loaded (or sits on about:blank) loads [tabUrl].
 * - The active tab without a WebView gets one from [createWebView] and loads [tabUrl].
 * - An inactive tab without a WebView (evicted, or not opened since the app started) is marked
 *   reload-pending: the WebView created when it becomes active restores the saved history and
 *   reloads once, or loads the tab URL when there is no saved state.
 */
internal fun reloadTab(
    webViewPool: WebViewPool,
    tabId: String,
    tabUrl: String?,
    isActive: Boolean,
    createWebView: () -> WebView,
) {
    var webView = webViewPool.getWebView(tabId)
    if (webView == null) {
        if (!isActive) {
            WebViewStateStore.markReloadPending(tabId)
            return
        }
        WebViewStateStore.takeReloadPending(tabId)
        webView = createWebView()
    }
    val loadedUrl = webView.url
    if (loadedUrl.isNullOrEmpty() || loadedUrl == "about:blank") {
        if (!tabUrl.isNullOrEmpty() && tabUrl != "about:blank") webView.loadUrl(tabUrl)
    } else {
        webView.reload()
    }
}
