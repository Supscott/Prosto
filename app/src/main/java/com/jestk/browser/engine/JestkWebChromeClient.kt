package com.jestk.browser.engine

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView

class JestkWebChromeClient(
    private val tabId: String,
    private val webView: WebView? = null,
    private val onProgressChanged: (tabId: String, progress: Int) -> Unit,
    private val onTitleReceived: (tabId: String, title: String) -> Unit,
    private val onFullscreenCustomViewChanged: (view: View?, isFullscreen: Boolean, requestedOrientation: Int) -> Unit,
    private val onVideoPlayingStateChanged: (isPlaying: Boolean) -> Unit,
    private val onFaviconReceived: ((tabId: String, icon: Bitmap) -> Unit)? = null,
    private val onVideoResume: (() -> Unit)? = null,
    private val onShowFileChooser: ((ValueCallback<Array<Uri>>?, FileChooserParams?) -> Boolean)? = null
) : WebChromeClient() {

    private var customView: View? = null
    private var customViewCallback: CustomViewCallback? = null

    override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: FileChooserParams?
    ): Boolean {
        return onShowFileChooser?.invoke(filePathCallback, fileChooserParams) ?: false
    }

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        onProgressChanged(tabId, newProgress)
    }

    override fun onReceivedTitle(view: WebView?, title: String?) {
        super.onReceivedTitle(view, title)
        title?.let { onTitleReceived(tabId, it) }
    }

    override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
        super.onReceivedIcon(view, icon)
        icon?.let { onFaviconReceived?.invoke(tabId, it) }
    }

    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
        onShowCustomView(view, ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, callback)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onShowCustomView(view: View?, requestedOrientation: Int, callback: CustomViewCallback?) {
        if (customView != null) {
            if (customView === view) {
                customViewCallback = callback ?: customViewCallback
                resumeVideoPlayback()
                return
            }
            onHideCustomView()
        }
        customView = view
        customViewCallback = callback
        customView?.keepScreenOn = true

        val orientation = when (requestedOrientation) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

        onFullscreenCustomViewChanged(view, true, orientation)
        onVideoPlayingStateChanged(true)
        resumeVideoPlayback()
    }

    override fun onHideCustomView() {
        if (customView == null) return
        customView?.keepScreenOn = false

        onFullscreenCustomViewChanged(null, false, ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
        onVideoPlayingStateChanged(false)
        try {
            customViewCallback?.onCustomViewHidden()
        } catch (_: Exception) {}
        customView = null
        customViewCallback = null
    }

    fun isCustomViewShowing(): Boolean = customView != null

    fun resumeVideoPlayback() {
        onVideoResume?.invoke()
        val targetWebView = webView ?: return
        val js = """
            (function() {
                try {
                    var videos = document.querySelectorAll('video');
                    for (var i = 0; i < videos.length; i++) {
                        var v = videos[i];
                        if (v && v.paused) {
                            var p = v.play();
                            if (p && typeof p.catch === 'function') {
                                p.catch(function(e) {});
                            }
                        }
                    }
                } catch(e) {}
            })();
        """.trimIndent()

        try {
            targetWebView.post {
                try {
                    targetWebView.evaluateJavascript(js, null)
                } catch (_: Exception) {}
            }
            targetWebView.postDelayed({
                try {
                    targetWebView.evaluateJavascript(js, null)
                } catch (_: Exception) {}
            }, 300)
        } catch (_: Exception) {}
    }
}
