package com.jestk.browser.ui.app

import com.jestk.browser.engine.WebViewPool
import com.jestk.browser.engine.WebViewStateStore

/**
 * Reloads [tabId] whether or not it is the active tab.
 * - A live WebView with a page reloads in place.
 * - A live WebView that never loaded (or sits on about:blank) loads [tabUrl].
 * - A tab without a WebView (evicted or not opened since the app started) drops its saved history,
 *   so the WebView created when the tab becomes active loads [tabUrl] fresh instead of restoring
 *   the stale page.
 */
internal fun reloadTab(
    webViewPool: WebViewPool,
    tabId: String,
    tabUrl: String?,
) {
    val webView = webViewPool.getWebView(tabId)
    if (webView == null) {
        WebViewStateStore.delete(tabId)
        return
    }
    val loadedUrl = webView.url
    if (loadedUrl.isNullOrEmpty() || loadedUrl == "about:blank") {
        if (!tabUrl.isNullOrEmpty() && tabUrl != "about:blank") webView.loadUrl(tabUrl)
    } else {
        webView.reload()
    }
}
