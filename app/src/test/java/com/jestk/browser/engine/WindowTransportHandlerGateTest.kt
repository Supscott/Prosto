package com.jestk.browser.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WindowTransportHandlerGateTest {
    private lateinit var context: Context
    private lateinit var pool: WebViewPool
    private var droppedUrl: String? = null
    private var revealedTab: String? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        pool = WebViewPool(context)
        WindowTransportHandler.gate = WindowTransportHandler.newGate()
        WindowTransportHandler.onPopupDropped = { _, url -> droppedUrl = url }
    }

    @After
    fun tearDown() {
        WindowTransportHandler.onPopupDropped = null
    }

    private fun open(): WebView.WebViewTransport {
        val transport = WebView(context).WebViewTransport()
        val message = Handler(Looper.getMainLooper()).obtainMessage().also { it.obj = transport }
        val handled = WindowTransportHandler.handleCreateWindow(
            resultMsg = message,
            context = context,
            webViewPool = pool,
            openerUrl = "https://news.example.org/",
            isAdBlockerEnabled = { true },
            onPopupOpened = { revealedTab = it },
        )
        assertThat(handled).isTrue()
        return transport
    }

    @Test
    fun transportIsBoundAtOnceButTabIsRevealedOnlyAfterDecision() {
        val transport = open()
        val view = transport.webView as NestedWebView
        val tabId = view.tag as String

        assertThat(revealedTab).isNull()
        assertThat(WindowTransportHandler.isPopupPending(tabId)).isTrue()
        assertThat(pool.getWebView(tabId)).isSameInstanceAs(view)

        assertThat(PopupGateHooks.onUrl(view, "https://shop.other.com/")).isFalse()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(revealedTab).isEqualTo(tabId)
        assertThat(view.popupGate).isNull()
        assertThat(pool.getWebView(tabId)).isSameInstanceAs(view)
    }

    @Test
    fun droppedPopupIsRemovedFromPoolAndReported() {
        val transport = open()
        val view = transport.webView as NestedWebView
        val tabId = view.tag as String
        assertThat(PopupGateHooks.onUrl(view, "https://doubleclick.net/x")).isTrue()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(revealedTab).isNull()
        assertThat(droppedUrl).isEqualTo("https://doubleclick.net/x")
        assertThat(pool.getWebView(tabId)).isNull()
    }

    @Test
    fun timeoutRevealsPendingPopup() {
        val transport = open()
        val tabId = (transport.webView as NestedWebView).tag as String

        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(2000))

        assertThat(revealedTab).isEqualTo(tabId)
    }

    @Test
    fun pendingPopupSurvivesRetainTabs() {
        val transport = open()
        val tabId = (transport.webView as NestedWebView).tag as String

        pool.retainTabs(emptySet())

        assertThat(pool.getWebView(tabId)).isNotNull()
    }
}
