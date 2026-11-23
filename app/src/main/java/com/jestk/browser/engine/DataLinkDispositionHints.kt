package com.jestk.browser.engine

import android.webkit.WebView
import java.util.WeakHashMap

/**
 * The `download="name"` hint of the last tapped `data:` link, per WebView. The content script
 * reports it on a background thread (JavascriptInterface) and the DownloadListener reads it on
 * the main thread right after, so access is synchronized. A hint is used once and expires after
 * [TTL_MS], so a later download without a name never inherits an old one. WebViews are weak
 * keys: a destroyed WebView takes its hint with it.
 */
internal class DataLinkDispositionHints(private val clock: () -> Long) {
    private class Hint(val contentDisposition: String, val atMs: Long)

    private val hints = WeakHashMap<WebView, Hint>()

    fun put(webView: WebView, contentDisposition: String) {
        synchronized(hints) { hints[webView] = Hint(contentDisposition, clock()) }
    }

    fun consume(webView: WebView): String? {
        val hint = synchronized(hints) { hints.remove(webView) } ?: return null
        return hint.contentDisposition.takeIf { clock() - hint.atMs <= TTL_MS }
    }

    companion object {
        const val TTL_MS = 2_000L
    }
}
