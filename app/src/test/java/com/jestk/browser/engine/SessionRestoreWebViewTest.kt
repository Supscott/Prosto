package com.jestk.browser.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SessionRestoreWebViewTest {

    @Test
    fun nestedWebView_tracksLoadedUrl() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val webView = NestedWebView(context)
        assertThat(webView.loadedUrl).isNull()

        webView.loadUrl("https://example.com")
        assertThat(webView.loadedUrl).isEqualTo("https://example.com")
    }

    @Test
    fun restoredTab_loadsUrlWhenNewlyCreated() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pool = WebViewPool(context)

        val tabId = "restored-tab-1"
        val tabUrl = "https://news.ycombinator.com"

        val webView = pool.getOrCreateWebView(tabId) as NestedWebView
        assertThat(webView.loadedUrl).isNull()

        // Simulate WebViewContainer load logic for restored tab
        if (webView.loadedUrl == null && tabUrl.isNotBlank() && tabUrl != "about:blank") {
            webView.loadUrl(tabUrl)
        }

        assertThat(webView.loadedUrl).isEqualTo("https://news.ycombinator.com")

        // Next time the tab is accessed, it already has loadedUrl, so no reload
        val existingWebView = pool.getOrCreateWebView(tabId) as NestedWebView
        assertThat(existingWebView).isSameInstanceAs(webView)
        assertThat(existingWebView.loadedUrl).isEqualTo("https://news.ycombinator.com")
    }

    @Test
    fun webViewPool_removeWebView_clearsLoadedUrl() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pool = WebViewPool(context)

        val tabId = "tab-to-remove"
        val webView = pool.getOrCreateWebView(tabId) as NestedWebView
        webView.loadUrl("https://example.com")
        assertThat(webView.loadedUrl).isEqualTo("https://example.com")

        pool.removeWebView(tabId)
        assertThat(webView.loadedUrl).isNull()
        assertThat(pool.getWebView(tabId)).isNull()
    }

    @Test
    fun swipeRefreshLayout_canChildScrollUp_falseAtTop() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val webView = NestedWebView(context)
        val swipeRefresh = androidx.swiperefreshlayout.widget.SwipeRefreshLayout(context).apply {
            addView(webView)
            setOnChildScrollUpCallback { _, _ ->
                webView.canScrollVertically(-1) || webView.scrollY > 0
            }
        }

        assertThat(swipeRefresh.canChildScrollUp()).isFalse()
    }
}
