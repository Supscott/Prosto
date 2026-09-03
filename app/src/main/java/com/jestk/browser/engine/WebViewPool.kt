package com.jestk.browser.engine

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import java.util.concurrent.ConcurrentHashMap

class WebViewPool(private val applicationContext: Context) {

    private val pool = ConcurrentHashMap<String, WebView>()

    @SuppressLint("SetJavaScriptEnabled")
    fun getOrCreateWebView(tabId: String): WebView {
        val existing = pool[tabId]
        if (existing != null) {
            // Ensure parent detachment before returning for Compose AndroidView mounting
            detachFromParent(existing)
            return existing
        }

        // Initialize with applicationContext to prevent leaking Activity context
        val webView = NestedWebView(applicationContext).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                builtInZoomControls = true
                displayZoomControls = false
                loadWithOverviewMode = true
                useWideViewPort = true
                databaseEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                setSupportZoom(true)
            }
            // Do NOT call pauseTimers() or onPause() when tabs are backgrounded,
            // keeping audio and video playback alive across tab switches.
        }

        pool[tabId] = webView
        return webView
    }

    fun detachFromParent(tabId: String) {
        pool[tabId]?.let { detachFromParent(it) }
    }

    fun detachFromParent(webView: WebView) {
        (webView.parent as? ViewGroup)?.removeView(webView)
    }

    fun getWebView(tabId: String): WebView? = pool[tabId]

    fun removeWebView(tabId: String) {
        val webView = pool.remove(tabId) ?: return
        detachFromParent(webView)
        try {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            (webView as? NestedWebView)?.loadedUrl = null
            webView.clearHistory()
            webView.destroy()
        } catch (_: Exception) {}
    }

    fun retainTabs(activeTabIds: Set<String>) {
        val toRemove = pool.keys().toList().filter { it !in activeTabIds }
        toRemove.forEach { removeWebView(it) }
    }

    fun clearAll() {
        pool.keys().toList().forEach { removeWebView(it) }
    }

    fun size(): Int = pool.size
}
