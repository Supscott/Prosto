package com.jestk.browser.engine

import android.app.Activity
import android.content.Context
import android.content.MutableContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies the teardown contract of [WebViewPool]: the pooled WebViews must be destroyed and the
 * background scope cancelled, while the pool itself stays usable for the app that owns it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WebViewPoolShutdownTest {

    private lateinit var appContext: Context
    private lateinit var pool: WebViewPool

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
        pool = WebViewPool(appContext)
    }

    @Test
    fun shutdown_destroysThePooledWebViewsAndReleasesTheActivity() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val webView = pool.getOrCreateWebView("tab-1", activity)
        val contextWrapper = webView.context as MutableContextWrapper
        assertThat(pool.size()).isEqualTo(1)

        pool.shutdown()

        assertThat(pool.size()).isEqualTo(0)
        assertThat(pool.getWebView("tab-1")).isNull()
        // The destroyed WebView must not retain the Activity any more.
        assertThat(contextWrapper.baseContext).isSameInstanceAs(appContext)
    }

    @Test
    fun shutdown_cancelsTheBackgroundScopeAndThePoolStaysUsable() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        pool.getOrCreateWebView("tab-1", activity)

        assertThat(pool.isScopeActive()).isTrue()

        pool.shutdown()
        assertThat(pool.isScopeActive()).isFalse()
        assertThat(pool.size()).isEqualTo(0)

        // The call is idempotent.
        pool.shutdown()
        assertThat(pool.isScopeActive()).isFalse()

        // The scope is recreated lazily, so the pool keeps working after a shutdown.
        assertThat(pool.ensureScope()).isTrue()
        assertThat(pool.isScopeActive()).isTrue()

        val webView = pool.getOrCreateWebView("tab-2", activity)
        assertThat(pool.size()).isEqualTo(1)
        assertThat(pool.getWebView("tab-2")).isSameInstanceAs(webView)
    }

    @Test
    fun clearAll_releasesTheWebViewsButKeepsTheScopeRunning() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        pool.getOrCreateWebView("tab-1", activity)

        pool.clearAll()

        assertThat(pool.size()).isEqualTo(0)
        // Closing all tabs must not stop the pool from handling downloads afterwards.
        assertThat(pool.isScopeActive()).isTrue()
    }
}
