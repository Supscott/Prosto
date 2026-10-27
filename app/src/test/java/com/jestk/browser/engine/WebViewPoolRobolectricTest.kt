package com.jestk.browser.engine

import android.app.Activity
import android.content.Context
import android.content.MutableContextWrapper
import android.os.Looper
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WebViewPoolRobolectricTest {

    private lateinit var appContext: Context
    private lateinit var webViewPool: WebViewPool

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
        webViewPool = WebViewPool(appContext)
    }

    @Test
    fun poolSizeConstraints_andLruEviction_evictsLeastRecentlyUsedWhenExceedingMax() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        // Create 5 WebViews (maximum capacity)
        val tab1 = webViewPool.getOrCreateWebView("tab-1", activity)
        val tab2 = webViewPool.getOrCreateWebView("tab-2", activity)
        val tab3 = webViewPool.getOrCreateWebView("tab-3", activity)
        val tab4 = webViewPool.getOrCreateWebView("tab-4", activity)
        val tab5 = webViewPool.getOrCreateWebView("tab-5", activity)

        assertThat(webViewPool.size()).isEqualTo(5)
        assertThat(webViewPool.getWebView("tab-1")).isSameInstanceAs(tab1)
        assertThat(webViewPool.getWebView("tab-5")).isSameInstanceAs(tab5)

        // Requesting a 6th tab triggers LRU eviction.
        // Since tab-1 was created first and never touched again, tab-1 is the LRU.
        val tab6 = webViewPool.getOrCreateWebView("tab-6", activity)

        // Pool size must remain bounded at MAX_CONCURRENT_WEBVIEWS (5)
        assertThat(webViewPool.size()).isEqualTo(5)
        // Least recently used (tab-1) must be evicted
        assertThat(webViewPool.getWebView("tab-1")).isNull()
        // Newly added tab-6 must exist
        assertThat(webViewPool.getWebView("tab-6")).isSameInstanceAs(tab6)
        // Remaining tabs 2-5 must still be in the pool
        assertThat(webViewPool.getWebView("tab-2")).isSameInstanceAs(tab2)
        assertThat(webViewPool.getWebView("tab-3")).isSameInstanceAs(tab3)
        assertThat(webViewPool.getWebView("tab-4")).isSameInstanceAs(tab4)
        assertThat(webViewPool.getWebView("tab-5")).isSameInstanceAs(tab5)
    }

    @Test
    fun lruEviction_safelyDetachesEvictedWebViewFromParent() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val parentLayout = FrameLayout(activity)

        // tab-1 is created and attached to a parent view
        val tab1 = webViewPool.getOrCreateWebView("tab-1", activity)
        parentLayout.addView(tab1)
        assertThat(tab1.parent).isSameInstanceAs(parentLayout)

        // Fill up to max and overflow
        webViewPool.getOrCreateWebView("tab-2", activity)
        webViewPool.getOrCreateWebView("tab-3", activity)
        webViewPool.getOrCreateWebView("tab-4", activity)
        webViewPool.getOrCreateWebView("tab-5", activity)
        webViewPool.getOrCreateWebView("tab-6", activity) // evicts tab-1

        // tab-1 must be detached from parentLayout on eviction
        assertThat(tab1.parent).isNull()
        assertThat(webViewPool.getWebView("tab-1")).isNull()
    }

    @Test
    fun onTabActivated_promotesTabToMostRecentlyUsed_preventingItsEviction() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        // Create 5 tabs: order from oldest to newest is tab-1, tab-2, tab-3, tab-4, tab-5
        val tab1 = webViewPool.getOrCreateWebView("tab-1", activity)
        val tab2 = webViewPool.getOrCreateWebView("tab-2", activity)
        val tab3 = webViewPool.getOrCreateWebView("tab-3", activity)
        val tab4 = webViewPool.getOrCreateWebView("tab-4", activity)
        val tab5 = webViewPool.getOrCreateWebView("tab-5", activity)

        // Activate tab-1: this touches LRU and moves tab-1 to MRU (front)
        webViewPool.onTabActivated("tab-1")

        // Now tab-2 is the least recently used tab!
        // Adding tab-6 should evict tab-2 instead of tab-1
        val tab6 = webViewPool.getOrCreateWebView("tab-6", activity)

        assertThat(webViewPool.size()).isEqualTo(5)
        // tab-1 was promoted and must NOT be evicted
        assertThat(webViewPool.getWebView("tab-1")).isSameInstanceAs(tab1)
        // tab-2 was the oldest untouched tab and must be evicted
        assertThat(webViewPool.getWebView("tab-2")).isNull()
        // tab-6 is present
        assertThat(webViewPool.getWebView("tab-6")).isSameInstanceAs(tab6)
    }

    @Test
    fun mutableContextWrapper_swapsContextWhenAttachingToNewActivity_andResetsOnRemove() {
        val activity1 = Robolectric.buildActivity(Activity::class.java).setup().get()
        val activity2 = Robolectric.buildActivity(Activity::class.java).setup().get()

        // Create with activity1
        val webView = webViewPool.getOrCreateWebView("tab-context", activity1)
        assertThat(webView.context).isInstanceOf(MutableContextWrapper::class.java)

        val wrapper = webView.context as MutableContextWrapper
        assertThat(wrapper.baseContext).isSameInstanceAs(activity1)

        // Retrieve same tab with activity2: baseContext must update to activity2
        val retrieved = webViewPool.getOrCreateWebView("tab-context", activity2)
        assertThat(retrieved).isSameInstanceAs(webView)
        assertThat(wrapper.baseContext).isSameInstanceAs(activity2)

        // On removeWebView: baseContext must be reset to applicationContext to prevent activity leaks
        webViewPool.removeWebView("tab-context")
        assertThat(wrapper.baseContext).isSameInstanceAs(appContext)
        assertThat(webViewPool.getWebView("tab-context")).isNull()
    }

    @Test
    fun removeWebView_cleansUpPoolAndDestroysInstance() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val parentLayout = FrameLayout(activity)

        val webView = webViewPool.getOrCreateWebView("tab-cleanup", activity) as NestedWebView
        parentLayout.addView(webView)
        webView.loadUrl("https://example.org")

        assertThat(webViewPool.size()).isEqualTo(1)
        assertThat(webView.loadedUrl).isEqualTo("https://example.org")
        assertThat(webView.parent).isNotNull()

        webViewPool.removeWebView("tab-cleanup")

        assertThat(webViewPool.size()).isEqualTo(0)
        assertThat(webViewPool.getWebView("tab-cleanup")).isNull()
        assertThat(webView.parent).isNull()
        assertThat(webView.loadedUrl).isNull()
    }

    @Test
    fun clearAll_releasesAllCachedWebViews() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        val webView1 = webViewPool.getOrCreateWebView("tab-a", activity) as NestedWebView
        val webView2 = webViewPool.getOrCreateWebView("tab-b", activity) as NestedWebView
        val webView3 = webViewPool.getOrCreateWebView("tab-c", activity) as NestedWebView
        webView1.loadUrl("https://a.com")
        webView2.loadUrl("https://b.com")
        webView3.loadUrl("https://c.com")

        assertThat(webViewPool.size()).isEqualTo(3)

        webViewPool.clearAll()

        assertThat(webViewPool.size()).isEqualTo(0)
        assertThat(webViewPool.getWebView("tab-a")).isNull()
        assertThat(webViewPool.getWebView("tab-b")).isNull()
        assertThat(webViewPool.getWebView("tab-c")).isNull()
        assertThat(webView1.loadedUrl).isNull()
        assertThat(webView2.loadedUrl).isNull()
        assertThat(webView3.loadedUrl).isNull()
    }

    @Test
    fun mediaPlayingTabs_protectedFromLruEvictionWhenAlternativeCandidateExists() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        // Create 5 tabs
        val tab1 = webViewPool.getOrCreateWebView("tab-media-1", activity)
        val tab2 = webViewPool.getOrCreateWebView("tab-media-2", activity)
        val tab3 = webViewPool.getOrCreateWebView("tab-media-3", activity)
        val tab4 = webViewPool.getOrCreateWebView("tab-media-4", activity)
        val tab5 = webViewPool.getOrCreateWebView("tab-media-5", activity)

        // Mark tab1 as playing media
        webViewPool.setMediaPlaying("tab-media-1", true)

        // Now tab-media-1 is the oldest, but is marked as playing media.
        // When tab-6 is added, trimPool should skip tab-media-1 and evict tab-media-2.
        webViewPool.getOrCreateWebView("tab-media-6", activity)

        assertThat(webViewPool.size()).isEqualTo(5)
        assertThat(webViewPool.getWebView("tab-media-1")).isNotNull()
        assertThat(webViewPool.getWebView("tab-media-2")).isNull()
    }

    @Test
    fun retainTabs_removesOnlyUntrackedTabs() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        webViewPool.getOrCreateWebView("keep-1", activity)
        webViewPool.getOrCreateWebView("keep-2", activity)
        webViewPool.getOrCreateWebView("remove-me", activity)

        assertThat(webViewPool.size()).isEqualTo(3)

        webViewPool.retainTabs(setOf("keep-1", "keep-2"))

        assertThat(webViewPool.size()).isEqualTo(2)
        assertThat(webViewPool.getWebView("keep-1")).isNotNull()
        assertThat(webViewPool.getWebView("keep-2")).isNotNull()
        assertThat(webViewPool.getWebView("remove-me")).isNull()
    }

    @Test
    fun loadedTabIdsFlow_updatesOnCreateEvictAndRemove() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        assertThat(webViewPool.loadedTabIds.value).isEmpty()
        assertThat(webViewPool.isTabLoaded("tab-1")).isFalse()

        webViewPool.getOrCreateWebView("tab-1", activity)
        assertThat(webViewPool.loadedTabIds.value).containsExactly("tab-1")
        assertThat(webViewPool.isTabLoaded("tab-1")).isTrue()

        webViewPool.getOrCreateWebView("tab-2", activity)
        assertThat(webViewPool.loadedTabIds.value).containsExactly("tab-1", "tab-2")
        assertThat(webViewPool.getLoadedTabIds()).containsExactly("tab-1", "tab-2")

        webViewPool.removeWebView("tab-1")
        assertThat(webViewPool.loadedTabIds.value).containsExactly("tab-2")
        assertThat(webViewPool.isTabLoaded("tab-1")).isFalse()
        assertThat(webViewPool.isTabLoaded("tab-2")).isTrue()

        webViewPool.clearAll()
        assertThat(webViewPool.loadedTabIds.value).isEmpty()
    }

    @Test
    fun getOrCreateWebView_configuresWebSettings() {
        val webView = webViewPool.getOrCreateWebView("tab-settings")
        assertThat(webView.settings.domStorageEnabled).isTrue()
        assertThat(webView.settings.javaScriptEnabled).isTrue()
    }

    @Test
    fun onTabActivated_whenCalledFromBackgroundThread_dispatchesLifecycleToMainLooper() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        webViewPool.getOrCreateWebView("tab-bg-1", activity)
        webViewPool.getOrCreateWebView("tab-bg-2", activity)

        val mainLooper = shadowOf(Looper.getMainLooper())
        mainLooper.idle()

        var backgroundException: Throwable? = null
        val thread = Thread {
            try {
                webViewPool.onTabActivated("tab-bg-2")
            } catch (t: Throwable) {
                backgroundException = t
            }
        }
        thread.start()
        thread.join(2000)

        assertThat(backgroundException).isNull()
        // Work was posted to the main looper from the background thread
        assertThat(mainLooper.isIdle).isFalse()
        mainLooper.idle()
        assertThat(mainLooper.isIdle).isTrue()

        // Both WebViews remain valid and accessible without crashing
        assertThat(webViewPool.getWebView("tab-bg-1")).isNotNull()
        assertThat(webViewPool.getWebView("tab-bg-2")).isNotNull()
    }

    @Test
    fun handleBlobDownload_whenWebViewIsNull_doesNotCrashAndDoesNotEvaluateOnArbitraryPool() {
        val webView = webViewPool.getOrCreateWebView("tab-isolated")
        val mainLooper = shadowOf(Looper.getMainLooper())
        mainLooper.idle()

        // Pass null webView
        webViewPool.handleBlobDownload(
            blobUrl = "blob:https://evil.com/test",
            contentDisposition = "attachment; filename=\"test.pdf\"",
            mimeType = "application/pdf",
            context = appContext,
            webView = null
        )
        mainLooper.idle()

        // Pool instance tab-isolated should remain undisturbed
        assertThat(webViewPool.getWebView("tab-isolated")).isNotNull()
    }

    @Test
    fun jsonQuoting_safelyEscapesSpecialCharactersAndNewlines() {
        val maliciousHeader = "attachment; filename=\"test\r\nInjected-Header: 1'; alert(1); //\""
        val quoted = org.json.JSONObject.quote(maliciousHeader)
        // Must start and end with double quotes and contain escaped carriage return/newline
        assertThat(quoted.startsWith("\"")).isTrue()
        assertThat(quoted.endsWith("\"")).isTrue()
        assertThat(quoted).contains("\\r\\n")
        assertThat(quoted).doesNotContain("\r\n")
    }
}
