package com.jestk.browser.engine

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.os.Build
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class JestkWebChromeClient(
    private val tabId: String,
    private val webView: WebView? = null,
    private val onProgressChanged: (tabId: String, progress: Int) -> Unit,
    private val onTitleReceived: (tabId: String, title: String) -> Unit,
    private val onFullscreenCustomViewChanged: (view: View?, isFullscreen: Boolean, requestedOrientation: Int) -> Unit,
    private val onVideoPlayingStateChanged: (isPlaying: Boolean) -> Unit,
    private val onFaviconReceived: ((tabId: String, icon: Bitmap) -> Unit)? = null
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

        val activity = webView?.context?.findActivity()
        // Keep requested orientation unspecified so the device follows the natural sensor
        // and does not flip the video or recreate the media cache
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.window?.let { window ->
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val attrs = window.attributes
                attrs.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                window.attributes = attrs
            }
        }

        onFullscreenCustomViewChanged(view, true, orientation)
        onVideoPlayingStateChanged(true)
    }

    override fun onHideCustomView() {
        if (customView == null) return
        customView?.keepScreenOn = false

        val activity = webView?.context?.findActivity()
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.window?.let { window ->
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.show(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val attrs = window.attributes
                attrs.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                window.attributes = attrs
            }
        }

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
        // Chromium manages HTML5 video playback in custom view natively;
        // avoid injecting video.play() which can disrupt MSE buffering.
    }
}
