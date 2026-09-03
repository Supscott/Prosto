package com.jestk.browser.engine

import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView

class JestkWebChromeClient(
    private val tabId: String,
    private val onProgressChanged: (tabId: String, progress: Int) -> Unit,
    private val onTitleReceived: (tabId: String, title: String) -> Unit,
    private val onFullscreenCustomViewChanged: (view: View?, isFullscreen: Boolean) -> Unit,
    private val onVideoPlayingStateChanged: (isPlaying: Boolean) -> Unit
) : WebChromeClient() {

    private var customView: View? = null
    private var customViewCallback: CustomViewCallback? = null

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        onProgressChanged(tabId, newProgress)
    }

    override fun onReceivedTitle(view: WebView?, title: String?) {
        super.onReceivedTitle(view, title)
        title?.let { onTitleReceived(tabId, it) }
    }

    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
        if (customView != null) {
            onHideCustomView()
            return
        }
        customView = view
        customViewCallback = callback
        onFullscreenCustomViewChanged(view, true)
        onVideoPlayingStateChanged(true)
    }

    override fun onHideCustomView() {
        if (customView == null) return
        onFullscreenCustomViewChanged(null, false)
        try {
            customViewCallback?.onCustomViewHidden()
        } catch (_: Exception) {}
        customView = null
        customViewCallback = null
    }

    fun isCustomViewShowing(): Boolean = customView != null
}
