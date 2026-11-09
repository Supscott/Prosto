package com.jestk.browser.engine

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Message
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
    private val onFaviconReceived: ((tabId: String, icon: Bitmap, pageUrl: String?) -> Unit)? = null,
    private val onShowFileChooser: ((ValueCallback<Array<Uri>>?, FileChooserParams?) -> Boolean)? = null,
    private val onCreateWindowRequest: ((view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?) -> Boolean)? = null,
    private val onCloseWindowRequest: ((tabId: String) -> Unit)? = null,
    private val onFullscreenOrientationChanged: ((orientation: Int) -> Unit)? = null
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

    override fun onCreateWindow(
        view: WebView?,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message?
    ): Boolean {
        return onCreateWindowRequest?.invoke(view, isDialog, isUserGesture, resultMsg)
            ?: super.onCreateWindow(view, isDialog, isUserGesture, resultMsg)
    }

    override fun onCloseWindow(window: WebView?) {
        super.onCloseWindow(window)
        onCloseWindowRequest?.invoke(tabId)
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
        val pageUrl = view?.url ?: (view as? NestedWebView)?.loadedUrl ?: webView?.url
        icon?.let { onFaviconReceived?.invoke(tabId, it, pageUrl) }
    }

    override fun getDefaultVideoPoster(): Bitmap? {
        // Return 1x1 transparent bitmap (matching DuckDuckGo BrowserChromeClient) to suppress
        // Android WebView's default gray box and oval play icon poster overlay.
        return Bitmap.createBitmap(intArrayOf(Color.TRANSPARENT), 1, 1, Bitmap.Config.ARGB_8888)
    }

    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
        if (customView != null) {
            if (customView === view) {
                customViewCallback = callback ?: customViewCallback
                return
            }
            // A custom view is already active; dismiss the duplicate request and preserve the active view
            try {
                callback?.onCustomViewHidden()
            } catch (_: Exception) {}
            return
        }
        customView = view
        customViewCallback = callback
        customView?.keepScreenOn = true

        onFullscreenCustomViewChanged(view, true, ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
        onVideoPlayingStateChanged(true)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onShowCustomView(view: View?, requestedOrientation: Int, callback: CustomViewCallback?) {
        onShowCustomView(view, callback)
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

    companion object {
        fun resolveOrientationFromAspectRatio(videoWidth: Int, videoHeight: Int): Int {
            if (videoWidth <= 0 || videoHeight <= 0) {
                return ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            return if (videoWidth.toLong() * 3L >= videoHeight.toLong() * 4L) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
        }
    }
}
