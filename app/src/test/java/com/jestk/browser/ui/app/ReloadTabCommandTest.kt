package com.jestk.browser.ui.app

import android.app.Activity
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.engine.WebViewPool
import com.jestk.browser.engine.WebViewStateStore
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** The per-tab reload behind the NavigationCommandEffect branch (PRO-200). */
@RunWith(RobolectricTestRunner::class)
class ReloadTabCommandTest {
    private lateinit var activity: Activity
    private lateinit var pool: WebViewPool
    private var created = 0

    @Before
    fun setUp() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        pool = WebViewPool(appContext)
    }

    @After
    fun clearStore() {
        WebViewStateStore.deleteAll()
    }

    private fun reload(tabId: String, url: String?, isActive: Boolean) =
        reloadTab(pool, tabId, url, isActive) {
            created++
            pool.getOrCreateWebView(tabId, activity)
        }

    @Test
    fun liveWebViewWithAPageReloadsInPlace() {
        val webView = pool.getOrCreateWebView("t", activity)
        webView.loadUrl("https://example.com/page")

        reload("t", "https://example.com/page", isActive = false)

        assertThat(shadowOf(webView).reloadInvocations).isEqualTo(1)
        assertThat(created).isEqualTo(0)
        assertThat(WebViewStateStore.takeReloadPending("t")).isFalse()
    }

    @Test
    fun liveBlankWebViewLoadsTheTabUrlInsteadOfReloading() {
        val webView = pool.getOrCreateWebView("t", activity)

        reload("t", "https://example.com/page", isActive = false)

        assertThat(shadowOf(webView).lastLoadedUrl).isEqualTo("https://example.com/page")
        assertThat(shadowOf(webView).reloadInvocations).isEqualTo(0)
    }

    @Test
    fun activeTabWithoutAWebViewGetsOneAndLoadsItsUrl() {
        reload("t", "https://example.com/page", isActive = true)

        assertThat(created).isEqualTo(1)
        val webView = pool.getWebView("t")!!
        assertThat(shadowOf(webView).lastLoadedUrl).isEqualTo("https://example.com/page")
        assertThat(WebViewStateStore.takeReloadPending("t")).isFalse()
    }

    @Test
    fun activeTabWithoutAWebViewDropsAStaleMark() {
        WebViewStateStore.markReloadPending("t")

        reload("t", "https://example.com/page", isActive = true)

        assertThat(WebViewStateStore.takeReloadPending("t")).isFalse()
    }

    @Test
    fun inactiveTabWithoutAWebViewIsMarkedAndNothingIsCreated() {
        reload("t", "https://example.com/page", isActive = false)

        assertThat(created).isEqualTo(0)
        assertThat(pool.getWebView("t")).isNull()
        assertThat(WebViewStateStore.takeReloadPending("t")).isTrue()
    }

    @Test
    fun activeBlankTabWithoutAUrlLoadsNothing() {
        reload("t", "about:blank", isActive = true)

        assertThat(shadowOf(pool.getWebView("t")!!).lastLoadedUrl).isNull()
    }
}
