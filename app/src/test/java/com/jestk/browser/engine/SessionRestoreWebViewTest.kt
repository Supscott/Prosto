package com.jestk.browser.engine

import android.app.Activity
import android.content.Context
import android.content.MutableContextWrapper
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
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

    @Test
    fun webViewPool_createsWebViewWithMutableContextWrapper_andActivityContext() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        val pool = WebViewPool(appContext)
        val webView = pool.getOrCreateWebView("tab-input-1", activity)

        assertThat(webView.context).isInstanceOf(MutableContextWrapper::class.java)
        val wrapper = webView.context as MutableContextWrapper
        assertThat(wrapper.baseContext).isEqualTo(activity)
        assertThat(webView.isFocusable).isTrue()
        assertThat(webView.isFocusableInTouchMode).isTrue()
    }

    @Test
    fun webViewPool_updatesBaseContext_whenAttachedToNewActivity() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val activity1 = Robolectric.buildActivity(Activity::class.java).setup().get()
        val activity2 = Robolectric.buildActivity(Activity::class.java).setup().get()

        val pool = WebViewPool(appContext)
        val webView = pool.getOrCreateWebView("tab-reuse", activity1)

        val wrapper = webView.context as MutableContextWrapper
        assertThat(wrapper.baseContext).isEqualTo(activity1)

        val reusedWebView = pool.getOrCreateWebView("tab-reuse", activity2)
        assertThat(reusedWebView).isSameInstanceAs(webView)
        assertThat(wrapper.baseContext).isEqualTo(activity2)
    }

    @Test
    fun webViewPool_resetsBaseContextToApplicationContext_onRemove() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        val pool = WebViewPool(appContext)
        val webView = pool.getOrCreateWebView("tab-remove", activity)
        val wrapper = webView.context as MutableContextWrapper
        assertThat(wrapper.baseContext).isEqualTo(activity)

        pool.removeWebView("tab-remove")
        assertThat(wrapper.baseContext).isEqualTo(appContext)
    }

    @Test
    fun nestedWebView_isFocusableAndHandlesTouch() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val webView = NestedWebView(activity)

        assertThat(webView.isFocusable).isTrue()
        assertThat(webView.isFocusableInTouchMode).isTrue()

        val downEvent = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 100f, 100f, 0)
        webView.onTouchEvent(downEvent)
        downEvent.recycle()
    }

    @Test
    fun webViewPool_getOrCreateWebView_doesNotDetachFromExistingParent() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val pool = WebViewPool(appContext)

        val webView = pool.getOrCreateWebView("tab-1", activity)
        val parent = android.widget.FrameLayout(activity)
        parent.addView(webView)
        assertThat(webView.parent).isSameInstanceAs(parent)

        // Calling getOrCreateWebView or getting from pool must NOT detach webView from parent
        val retrieved = pool.getOrCreateWebView("tab-1", activity)
        assertThat(retrieved).isSameInstanceAs(webView)
        assertThat(retrieved.parent).isSameInstanceAs(parent)
    }
}
