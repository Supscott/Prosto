package com.jestk.browser.engine

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import com.jestk.browser.engine.adblock.PopupOutcome
import java.io.ByteArrayInputStream

/**
 * Feeds the first main-frame URL of a still-pending popup WebView into its gate. Called from
 * every hook that can see it first; a view that is not a pending popup is untouched.
 */
internal object PopupGateHooks {
    /** True when this URL dropped the popup, so the navigation must be cancelled. */
    fun onUrl(view: WebView?, url: String?): Boolean {
        val nested = view as? NestedWebView ?: return false
        val gate = nested.popupGate ?: return false
        val tabId = nested.tag as? String ?: return false
        return gate.onUrl(tabId, url) == PopupOutcome.DROPPED
    }

    /**
     * `shouldInterceptRequest` runs on a worker thread and sees the main-frame request before any
     * other hook. A dropped popup gets an empty response so the ad host is never contacted.
     */
    fun intercept(view: WebView?, request: WebResourceRequest): WebResourceResponse? {
        if (!request.isForMainFrame || !onUrl(view, request.url?.toString())) return null
        return WebResourceResponse("text/html", "utf-8", ByteArrayInputStream(ByteArray(0)))
    }
}
