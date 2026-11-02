package com.jestk.browser.engine

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
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
    private val onVideoResume: (() -> Unit)? = null,
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
            // A custom view is already active (e.g. YouTube remounting/transitioning).
            // Reject the duplicate call to avoid destroying the active video surface and rebuffering.
            try {
                callback?.onCustomViewHidden()
            } catch (_: Exception) {}
            return
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
            else -> {
                // If the URL explicitly indicates a vertical video / short, default to portrait
                val currentUrl = webView?.url ?: (webView as? NestedWebView)?.loadedUrl
                val url = currentUrl?.lowercase().orEmpty()
                if (url.contains("/shorts/") || url.contains("/reel/") || url.contains("/clip/")) {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                } else {
                    // Fullscreen video default is landscape (>= 4:3, e.g. 16:9 on YouTube, VK, Twitch)
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
            }
        }

        onFullscreenCustomViewChanged(view, true, orientation)
        onVideoPlayingStateChanged(true)
        resumeVideoPlayback()

        // Query the DOM with retries to confirm aspect ratio and detect vertical videos (< 4:3)
        queryVideoAspectRatioAndAdjustOrientation()
    }

    private fun queryVideoAspectRatioAndAdjustOrientation(attempt: Int = 0) {
        val targetWebView = webView ?: return
        val js = """
            (function() {
                try {
                    function checkVideo(v) {
                        if (v && v.videoWidth > 0 && v.videoHeight > 0) {
                            return v.videoWidth + ":" + v.videoHeight;
                        }
                        return null;
                    }

                    // 1. Check fullscreenElement
                    var fs = document.fullscreenElement || document.webkitFullscreenElement;
                    if (fs) {
                        if (fs instanceof HTMLVideoElement) {
                            var r = checkVideo(fs);
                            if (r) return r;
                        }
                        if (fs.querySelector) {
                            var r = checkVideo(fs.querySelector('video'));
                            if (r) return r;
                        }
                    }

                    // 2. Check all video elements in document (prefer playing video)
                    var videos = document.querySelectorAll('video');
                    var fallback = null;
                    for (var i = 0; i < videos.length; i++) {
                        var v = videos[i];
                        if (v && v.videoWidth > 0 && v.videoHeight > 0) {
                            if (!v.paused) return v.videoWidth + ":" + v.videoHeight;
                            if (!fallback) fallback = v.videoWidth + ":" + v.videoHeight;
                        }
                    }
                    if (fallback) return fallback;

                    // 3. Check iframes (VK, embeds)
                    var iframes = document.querySelectorAll('iframe');
                    for (var j = 0; j < iframes.length; j++) {
                        try {
                            var iDoc = iframes[j].contentDocument || iframes[j].contentWindow.document;
                            if (iDoc) {
                                var iVideos = iDoc.querySelectorAll('video');
                                for (var k = 0; k < iVideos.length; k++) {
                                    var r = checkVideo(iVideos[k]);
                                    if (r) return r;
                                }
                            }
                        } catch(e) {}
                    }
                } catch(e) {}
                return "";
            })();
        """.trimIndent()

        targetWebView.post {
            try {
                targetWebView.evaluateJavascript(js) { rawResult ->
                    if (!isCustomViewShowing()) return@evaluateJavascript
                    val cleaned = rawResult?.trim()?.removeSurrounding("\"")?.removeSurrounding("'").orEmpty()
                    if (cleaned.contains(":")) {
                        val parts = cleaned.split(":")
                        if (parts.size == 2) {
                            val w = parts[0].toIntOrNull() ?: 0
                            val h = parts[1].toIntOrNull() ?: 0
                            val resolved = resolveOrientationFromAspectRatio(w, h)
                            if (resolved != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
                                onFullscreenOrientationChanged?.invoke(resolved)
                                    ?: onFullscreenCustomViewChanged(customView, true, resolved)
                                return@evaluateJavascript
                            }
                        }
                    }

                    // Retry up to 3 times (at 150ms, 350ms, 700ms) if video metadata was not yet ready
                    if (attempt < 3 && isCustomViewShowing()) {
                        val delay = when (attempt) {
                            0 -> 150L
                            1 -> 200L
                            else -> 350L
                        }
                        targetWebView.postDelayed({
                            if (isCustomViewShowing()) {
                                queryVideoAspectRatioAndAdjustOrientation(attempt + 1)
                            }
                        }, delay)
                    }
                }
            } catch (_: Exception) {}
        }
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
