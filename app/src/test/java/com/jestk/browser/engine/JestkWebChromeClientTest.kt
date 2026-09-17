package com.jestk.browser.engine

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
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
        var receivedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
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
    fun onShowCustomView_replacesExistingCustomViewCleanly() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val view1 = View(context)
        val view2 = View(context)
        var callback1Called = false
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

        // Calling onShowCustomView while already showing should dismiss the first and mount the new view
        client.onShowCustomView(view2) { }

        assertThat(callback1Called).isTrue()
        assertThat(client.isCustomViewShowing()).isTrue()
        assertThat(lastReceivedView).isSameInstanceAs(view2)
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
    fun resumeVideoPlayback_evaluatesJavascriptOnWebView() {
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
}
