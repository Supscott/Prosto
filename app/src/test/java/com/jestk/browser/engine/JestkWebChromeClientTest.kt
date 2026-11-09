package com.jestk.browser.engine

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class JestkWebChromeClientTest {

    @Test
    fun onShowCustomView_twoArgs_defaultsToUnspecifiedOrientation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testView = View(context)
        var receivedView: View? = null
        var isFullscreenState = false
        var receivedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        var videoPlayingState = false

        val client = JestkWebChromeClient(
            tabId = "tab-1",
            onProgressChanged = { _, _ -> },
            onTitleReceived = { _, _ -> },
            onFullscreenCustomViewChanged = { view, isFullscreen, orientation ->
                receivedView = view
                isFullscreenState = isFullscreen
                receivedOrientation = orientation
            },
            onVideoPlayingStateChanged = { isPlaying ->
                videoPlayingState = isPlaying
            }
        )

        var hiddenCallbackCalled = false
        val callback = WebChromeClient.CustomViewCallback { hiddenCallbackCalled = true }

        client.onShowCustomView(testView, callback)

        assertThat(client.isCustomViewShowing()).isTrue()
        assertThat(isFullscreenState).isTrue()
        assertThat(receivedView).isSameInstanceAs(testView)
        assertThat(receivedOrientation).isEqualTo(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
        assertThat(testView.keepScreenOn).isTrue()
        assertThat(videoPlayingState).isTrue()

        // Hide custom view
        client.onHideCustomView()

        assertThat(client.isCustomViewShowing()).isFalse()
        assertThat(isFullscreenState).isFalse()
        assertThat(receivedView).isNull()
        assertThat(receivedOrientation).isEqualTo(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
        assertThat(testView.keepScreenOn).isFalse()
        assertThat(hiddenCallbackCalled).isTrue()
        assertThat(videoPlayingState).isFalse()
    }

    @Test
    fun onShowCustomView_twoArgs_preservesNaturalOrientation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val webView = NestedWebView(context).apply {
            loadedUrl = "https://m.youtube.com/shorts/12345"
        }
        val testView = View(context)
        var receivedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

        val client = JestkWebChromeClient(
            tabId = "tab-shorts",
            webView = webView,
            onProgressChanged = { _, _ -> },
            onTitleReceived = { _, _ -> },
            onFullscreenCustomViewChanged = { _, _, orientation ->
                receivedOrientation = orientation
            },
            onVideoPlayingStateChanged = { }
        )

        client.onShowCustomView(testView) { }

        assertThat(receivedOrientation).isEqualTo(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
    }

    @Test
    fun onShowCustomView_threeArgs_respectsPortraitOrientation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testView = View(context)
        var receivedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

        val client = JestkWebChromeClient(
            tabId = "tab-1",
            onProgressChanged = { _, _ -> },
            onTitleReceived = { _, _ -> },
            onFullscreenCustomViewChanged = { _, _, orientation ->
                receivedOrientation = orientation
            },
            onVideoPlayingStateChanged = { }
        )

        val callback = WebChromeClient.CustomViewCallback { }

        @Suppress("DEPRECATION")
        client.onShowCustomView(testView, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, callback)

        assertThat(receivedOrientation).isEqualTo(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT)
    }

    @Test
    fun onShowCustomView_threeArgs_mapsLandscapeToSensorLandscape() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testView = View(context)
        var receivedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

        val client = JestkWebChromeClient(
            tabId = "tab-1",
            onProgressChanged = { _, _ -> },
            onTitleReceived = { _, _ -> },
            onFullscreenCustomViewChanged = { _, _, orientation ->
                receivedOrientation = orientation
            },
            onVideoPlayingStateChanged = { }
        )

        val callback = WebChromeClient.CustomViewCallback { }

        @Suppress("DEPRECATION")
        client.onShowCustomView(testView, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, callback)

        assertThat(receivedOrientation).isEqualTo(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
    }

    @Test
    fun onShowCustomView_duplicateView_rejectsSecondCallAndPreservesFirstCustomView() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val view1 = View(context)
        val view2 = View(context)
        var callback1Called = false
        var callback2Called = false
        var lastReceivedView: View? = null

        val client = JestkWebChromeClient(
            tabId = "tab-1",
            onProgressChanged = { _, _ -> },
            onTitleReceived = { _, _ -> },
            onFullscreenCustomViewChanged = { view, _, _ -> lastReceivedView = view },
            onVideoPlayingStateChanged = { }
        )

        client.onShowCustomView(view1) { callback1Called = true }
        assertThat(client.isCustomViewShowing()).isTrue()
        assertThat(lastReceivedView).isSameInstanceAs(view1)

        // Calling onShowCustomView with a different view while already showing should dismiss the second callback and keep the first view
        client.onShowCustomView(view2) { callback2Called = true }

        assertThat(callback1Called).isFalse()
        assertThat(callback2Called).isTrue()
        assertThat(client.isCustomViewShowing()).isTrue()
        assertThat(lastReceivedView).isSameInstanceAs(view1)
    }

    @Test
    fun onShowCustomView_sameView_doesNotTriggerHideCustomView() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val view1 = View(context)
        var callback1Called = false

        val client = JestkWebChromeClient(
            tabId = "tab-1",
            onProgressChanged = { _, _ -> },
            onTitleReceived = { _, _ -> },
            onFullscreenCustomViewChanged = { _, _, _ -> },
            onVideoPlayingStateChanged = { }
        )

        client.onShowCustomView(view1) { callback1Called = true }
        assertThat(client.isCustomViewShowing()).isTrue()

        // Calling onShowCustomView with the same view instance should not trigger hidden callback
        client.onShowCustomView(view1) { }

        assertThat(callback1Called).isFalse()
        assertThat(client.isCustomViewShowing()).isTrue()
    }

    @Test
    fun onShowCustomView_triggersVideoResumptionCallback() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val view = View(context)
        var resumeCallbackCalled = false

        val client = JestkWebChromeClient(
            tabId = "tab-1",
            onProgressChanged = { _, _ -> },
            onTitleReceived = { _, _ -> },
            onFullscreenCustomViewChanged = { _, _, _ -> },
            onVideoPlayingStateChanged = { },
            onVideoResume = { resumeCallbackCalled = true }
        )

        client.onShowCustomView(view) { }
        assertThat(resumeCallbackCalled).isTrue()
        assertThat(view.keepScreenOn).isTrue()
    }

    @Test
    fun onShowCustomView_sameView_triggersVideoResumption() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val view = View(context)
        var resumeCount = 0

        val client = JestkWebChromeClient(
            tabId = "tab-1",
            onProgressChanged = { _, _ -> },
            onTitleReceived = { _, _ -> },
            onFullscreenCustomViewChanged = { _, _, _ -> },
            onVideoPlayingStateChanged = { },
            onVideoResume = { resumeCount++ }
        )

        client.onShowCustomView(view) { }
        assertThat(resumeCount).isEqualTo(1)

        // Calling onShowCustomView with the same view should re-trigger resumption check
        client.onShowCustomView(view) { }
        assertThat(resumeCount).isEqualTo(2)
    }

    @Test
    fun resumeVideoPlayback_invokesResumeCallback() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val webView = android.webkit.WebView(context)
        var resumeCallbackCalled = false

        val client = JestkWebChromeClient(
            tabId = "tab-1",
            webView = webView,
            onProgressChanged = { _, _ -> },
            onTitleReceived = { _, _ -> },
            onFullscreenCustomViewChanged = { _, _, _ -> },
            onVideoPlayingStateChanged = { },
            onVideoResume = { resumeCallbackCalled = true }
        )

        client.resumeVideoPlayback()
        assertThat(resumeCallbackCalled).isTrue()
    }

    @Test
    fun resolveOrientationFromAspectRatio_widescreenAndStandard_returnsSensorLandscape() {
        // 16:9 widescreen
        assertThat(JestkWebChromeClient.resolveOrientationFromAspectRatio(1920, 1080))
            .isEqualTo(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)

        // Exact 4:3
        assertThat(JestkWebChromeClient.resolveOrientationFromAspectRatio(1600, 1200))
            .isEqualTo(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
        assertThat(JestkWebChromeClient.resolveOrientationFromAspectRatio(4, 3))
            .isEqualTo(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)

        // 21:9 ultra-wide
        assertThat(JestkWebChromeClient.resolveOrientationFromAspectRatio(2560, 1080))
            .isEqualTo(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
    }

    @Test
    fun resolveOrientationFromAspectRatio_verticalAndSquare_returnsSensorPortrait() {
        // 9:16 vertical (Shorts, TikTok, Reels)
        assertThat(JestkWebChromeClient.resolveOrientationFromAspectRatio(1080, 1920))
            .isEqualTo(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT)

        // 1:1 square (< 4:3)
        assertThat(JestkWebChromeClient.resolveOrientationFromAspectRatio(1000, 1000))
            .isEqualTo(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT)

        // 3:4 vertical
        assertThat(JestkWebChromeClient.resolveOrientationFromAspectRatio(1200, 1600))
            .isEqualTo(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT)
    }

    @Test
    fun resolveOrientationFromAspectRatio_invalidDimensions_returnsUnspecified() {
        assertThat(JestkWebChromeClient.resolveOrientationFromAspectRatio(0, 0))
            .isEqualTo(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
        assertThat(JestkWebChromeClient.resolveOrientationFromAspectRatio(0, 1080))
            .isEqualTo(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
        assertThat(JestkWebChromeClient.resolveOrientationFromAspectRatio(1920, 0))
            .isEqualTo(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
        assertThat(JestkWebChromeClient.resolveOrientationFromAspectRatio(-1, 1080))
            .isEqualTo(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
        assertThat(JestkWebChromeClient.resolveOrientationFromAspectRatio(1920, -1))
            .isEqualTo(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
    }

    @Test
    fun onShowCustomView_withUnspecifiedOrientation_registersOrientationListener() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val webView = android.webkit.WebView(context)
        val view = View(context)
        var receivedOrientation: Int? = null

        val client = JestkWebChromeClient(
            tabId = "tab-1",
            webView = webView,
            onProgressChanged = { _, _ -> },
            onTitleReceived = { _, _ -> },
            onFullscreenCustomViewChanged = { _, _, _ -> },
            onVideoPlayingStateChanged = { },
            onFullscreenOrientationChanged = { orientation -> receivedOrientation = orientation }
        )

        client.onShowCustomView(view) { }
        assertThat(client.isCustomViewShowing()).isTrue()
    }

    @Test
    fun onShowCustomView_normalVideoUrl_defaultsToUnspecifiedOrientation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val webView = NestedWebView(context).apply {
            loadedUrl = "https://m.youtube.com/watch?v=dQw4w9WgXcQ"
        }
        val testView = View(context)
        var receivedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        val client = JestkWebChromeClient(
            tabId = "tab-youtube",
            webView = webView,
            onProgressChanged = { _, _ -> },
            onTitleReceived = { _, _ -> },
            onFullscreenCustomViewChanged = { _, _, orientation ->
                receivedOrientation = orientation
            },
            onVideoPlayingStateChanged = { }
        )

        client.onShowCustomView(testView) { }

        assertThat(receivedOrientation).isEqualTo(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
    }

    @Test
    fun onShowFileChooser_defaultNullCallback_returnsFalse() {
        val client = JestkWebChromeClient(
            tabId = "tab-1",
            onProgressChanged = { _, _ -> },
            onTitleReceived = { _, _ -> },
            onFullscreenCustomViewChanged = { _, _, _ -> },
            onVideoPlayingStateChanged = { }
        )

        val result = client.onShowFileChooser(null, null, null)
        assertThat(result).isFalse()
    }

    @Test
    fun onShowFileChooser_delegatesToCallbackAndReturnsTrue() {
        var callbackReceived: ValueCallback<Array<Uri>>? = null
        var paramsReceived: WebChromeClient.FileChooserParams? = null
        var callbackInvoked = false

        val testCallback = ValueCallback<Array<Uri>> { }
        val testParams = object : WebChromeClient.FileChooserParams() {
            override fun getMode(): Int = MODE_OPEN
            override fun getAcceptTypes(): Array<String> = arrayOf("image/*")
            override fun isCaptureEnabled(): Boolean = false
            override fun getTitle(): CharSequence = "Choose"
            override fun getFilenameHint(): String? = null
            override fun createIntent(): Intent = Intent(Intent.ACTION_GET_CONTENT)
        }

        val client = JestkWebChromeClient(
            tabId = "tab-1",
            onProgressChanged = { _, _ -> },
            onTitleReceived = { _, _ -> },
            onFullscreenCustomViewChanged = { _, _, _ -> },
            onVideoPlayingStateChanged = { },
            onShowFileChooser = { callback, params ->
                callbackInvoked = true
                callbackReceived = callback
                paramsReceived = params
                true
            }
        )

        val result = client.onShowFileChooser(null, testCallback, testParams)

        assertThat(callbackInvoked).isTrue()
        assertThat(result).isTrue()
        assertThat(callbackReceived).isSameInstanceAs(testCallback)
        assertThat(paramsReceived).isSameInstanceAs(testParams)
    }

    @Test
    fun onShowFileChooser_whenCallbackReturnsFalse_returnsFalse() {
        val client = JestkWebChromeClient(
            tabId = "tab-1",
            onProgressChanged = { _, _ -> },
            onTitleReceived = { _, _ -> },
            onFullscreenCustomViewChanged = { _, _, _ -> },
            onVideoPlayingStateChanged = { },
            onShowFileChooser = { _, _ -> false }
        )

        val result = client.onShowFileChooser(null, null, null)
        assertThat(result).isFalse()
    }

    @Test
    fun onReceivedIcon_invokesCallbackWithTabIdIconAndPageUrl() {
        var receivedTabId: String? = null
        var receivedIcon: Bitmap? = null
        var receivedUrl: String? = null

        val client = JestkWebChromeClient(
            tabId = "tab-icon-1",
            onProgressChanged = { _, _ -> },
            onTitleReceived = { _, _ -> },
            onFullscreenCustomViewChanged = { _, _, _ -> },
            onVideoPlayingStateChanged = { },
            onFaviconReceived = { tabId, icon, pageUrl ->
                receivedTabId = tabId
                receivedIcon = icon
                receivedUrl = pageUrl
            }
        )

        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val webView = NestedWebView(context).apply {
            loadedUrl = "https://example.com/test"
        }

        client.onReceivedIcon(webView, bitmap)

        assertThat(receivedTabId).isEqualTo("tab-icon-1")
        assertThat(receivedIcon).isSameInstanceAs(bitmap)
        assertThat(receivedUrl).isEqualTo("https://example.com/test")
    }

    @Test
    fun onCreateWindow_delegatesToCallbackAndReturnsResult() {
        var createCalled = false
        val client = JestkWebChromeClient(
            tabId = "tab-popup",
            onProgressChanged = { _, _ -> },
            onTitleReceived = { _, _ -> },
            onFullscreenCustomViewChanged = { _, _, _ -> },
            onVideoPlayingStateChanged = { },
            onCreateWindowRequest = { _, _, _, _ ->
                createCalled = true
                true
            }
        )

        val result = client.onCreateWindow(null, false, true, null)
        assertThat(result).isTrue()
        assertThat(createCalled).isTrue()
    }

    @Test
    fun onCloseWindow_notifiesCallbackWithTabId() {
        var closedId: String? = null
        val client = JestkWebChromeClient(
            tabId = "tab-to-close",
            onProgressChanged = { _, _ -> },
            onTitleReceived = { _, _ -> },
            onFullscreenCustomViewChanged = { _, _, _ -> },
            onVideoPlayingStateChanged = { },
            onCloseWindowRequest = { closedId = it }
        )

        client.onCloseWindow(null)
        assertThat(closedId).isEqualTo("tab-to-close")
    }

    @Test
    fun handleCreateWindow_bindsTransportAndReturnsTrue() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val webViewPool = WebViewPool(context)
        val transport = android.webkit.WebView(context).WebViewTransport()

        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val message = handler.obtainMessage()
        message.obj = transport

        var popupTabId: String? = null
        val handled = WindowTransportHandler.handleCreateWindow(
            resultMsg = message,
            context = context,
            webViewPool = webViewPool,
            onPopupOpened = { popupTabId = it }
        )

        assertThat(handled).isTrue()
        assertThat(popupTabId).isNotNull()
        assertThat(transport.webView).isNotNull()
        assertThat(webViewPool.getWebView(popupTabId!!)).isSameInstanceAs(transport.webView)
    }

    @Test
    fun handleCreateWindow_whenMessageNullOrNotTransport_returnsFalse() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val webViewPool = WebViewPool(context)

        assertThat(WindowTransportHandler.handleCreateWindow(null, context, webViewPool) {}).isFalse()

        val invalidMessage = android.os.Message.obtain()
        invalidMessage.obj = "not a transport"
        assertThat(WindowTransportHandler.handleCreateWindow(invalidMessage, context, webViewPool) {}).isFalse()
    }
}
