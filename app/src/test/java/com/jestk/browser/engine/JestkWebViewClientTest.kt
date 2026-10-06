package com.jestk.browser.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class JestkWebViewClientTest {

    @Test
    fun onPageStarted_ignoresEmptyAndAboutBlank() {
        var startedUrl: String? = null
        val client = JestkWebViewClient(
            tabId = "tab_1",
            isAdBlockerEnabled = { false },
            onPageStarted = { _, url -> startedUrl = url },
            onPageFinished = { _, _, _ -> },
            onNavigationStateChanged = { _, _, _ -> },
            onSnapshotReady = { _, _ -> }
        )

        client.onPageStarted(null, "", null)
        assertThat(startedUrl).isNull()

        client.onPageStarted(null, "about:blank", null)
        assertThat(startedUrl).isNull()

        client.onPageStarted(null, "https://example.com", null)
        assertThat(startedUrl).isEqualTo("https://example.com")
    }

    @Test
    fun onPageFinished_ignoresEmptyAndAboutBlank() {
        var finishedUrl: String? = null
        val client = JestkWebViewClient(
            tabId = "tab_1",
            isAdBlockerEnabled = { false },
            onPageStarted = { _, _ -> },
            onPageFinished = { _, url, _ -> finishedUrl = url },
            onNavigationStateChanged = { _, _, _ -> },
            onSnapshotReady = { _, _ -> }
        )

        client.onPageFinished(null, "")
        assertThat(finishedUrl).isNull()

        client.onPageFinished(null, "about:blank")
        assertThat(finishedUrl).isNull()

        client.onPageFinished(null, "https://example.com")
        assertThat(finishedUrl).isEqualTo("https://example.com")
    }

    @Test
    fun shouldInterceptRequest_blocksAndInvokesTracking() {
        var interceptedTracker: Boolean? = null
        var interceptedUrl: String? = null

        val client = JestkWebViewClient(
            tabId = "tab_1",
            isAdBlockerEnabled = { true },
            onPageStarted = { _, _ -> },
            onPageFinished = { _, _, _ -> },
            onNavigationStateChanged = { _, _, _ -> },
            onSnapshotReady = { _, _ -> },
            onBlockedResource = { url, isTracker ->
                interceptedUrl = url
                interceptedTracker = isTracker
            }
        )

        val adRequest = object : android.webkit.WebResourceRequest {
            override fun getUrl(): android.net.Uri = android.net.Uri.parse("https://doubleclick.net/ad.js")
            override fun isForMainFrame(): Boolean = false
            override fun isRedirect(): Boolean = false
            override fun hasGesture(): Boolean = false
            override fun getMethod(): String = "GET"
            override fun getRequestHeaders(): Map<String, String> = emptyMap()
        }

        val adResponse = client.shouldInterceptRequest(null, adRequest)
        assertThat(adResponse).isNotNull()
        assertThat(interceptedUrl).isEqualTo("https://doubleclick.net/ad.js")
        assertThat(interceptedTracker).isFalse()

        val trackerRequest = object : android.webkit.WebResourceRequest {
            override fun getUrl(): android.net.Uri = android.net.Uri.parse("https://google-analytics.com/analytics.js")
            override fun isForMainFrame(): Boolean = false
            override fun isRedirect(): Boolean = false
            override fun hasGesture(): Boolean = false
            override fun getMethod(): String = "GET"
            override fun getRequestHeaders(): Map<String, String> = emptyMap()
        }

        val trackerResponse = client.shouldInterceptRequest(null, trackerRequest)
        assertThat(trackerResponse).isNotNull()
        assertThat(interceptedUrl).isEqualTo("https://google-analytics.com/analytics.js")
        assertThat(interceptedTracker).isTrue()
    }

    @Test
    fun doUpdateVisitedHistory_withValidUrlAndTitle_invokesPageStartedAndTitleReceived() {
        var startedUrl: String? = null
        var receivedTitle: String? = null
        val client = JestkWebViewClient(
            tabId = "tab_1",
            isAdBlockerEnabled = { false },
            onPageStarted = { _, url -> startedUrl = url },
            onPageFinished = { _, _, _ -> },
            onNavigationStateChanged = { _, _, _ -> },
            onSnapshotReady = { _, _ -> },
            onTitleReceived = { _, title -> receivedTitle = title }
        )

        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val webView = android.webkit.WebView(context)

        client.doUpdateVisitedHistory(webView, "https://example.com/spa-route", false)
        assertThat(startedUrl).isEqualTo("https://example.com/spa-route")
        // Title on empty webView is null/empty so onTitleReceived is not invoked
        assertThat(receivedTitle).isNull()
    }

    @Test
    fun doUpdateVisitedHistory_withBlankOrAboutBlankUrl_doesNotInvokePageStarted() {
        var startedUrl: String? = null
        val client = JestkWebViewClient(
            tabId = "tab_1",
            isAdBlockerEnabled = { false },
            onPageStarted = { _, url -> startedUrl = url },
            onPageFinished = { _, _, _ -> },
            onNavigationStateChanged = { _, _, _ -> },
            onSnapshotReady = { _, _ -> }
        )

        client.doUpdateVisitedHistory(null, "", false)
        assertThat(startedUrl).isNull()

        client.doUpdateVisitedHistory(null, "about:blank", false)
        assertThat(startedUrl).isNull()
    }
}
