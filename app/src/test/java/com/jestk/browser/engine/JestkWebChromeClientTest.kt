package com.jestk.browser.engine

import android.content.Context
import android.content.pm.ActivityInfo
import android.view.View
import android.webkit.WebChromeClient
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class JestkWebChromeClientTest {

    @Test
    fun onShowCustomView_twoArgs_defaultsToSensorLandscape() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testView = View(context)
        var receivedView: View? = null
        var isFullscreenState = false
        var receivedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        var videoPlayingState = false

        val client = JestkWebChromeClient(
            tabId = "tab-1",
            webView = null,
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
        assertThat(receivedOrientation).isEqualTo(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
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
            webView = null,
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
            webView = null,
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
            webView = null,
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
            webView = null,
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
}
