package com.jestk.browser.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A reload requested for an evicted tab runs as exactly one navigation when its WebView is recreated (PRO-200). */
@RunWith(RobolectricTestRunner::class)
class ReloadPendingRestoreTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var pool: WebViewPool

    @Before
    fun setUp() {
        pool = WebViewPool(context)
    }

    @After
    fun clearStore() {
        WebViewStateStore.deleteAll()
    }

    private fun saveHistory(tabId: String) = WebViewStateStore.save(tabId, FakeHistoryWebView(context))

    @Test
    fun restoreOntoTheTabUrl_reloadsOnceAndClearsTheMark() {
        saveHistory("t")
        WebViewStateStore.markReloadPending("t")
        val fresh = FakeHistoryWebView(context, restoredUrl = "https://example.com/page")

        val restored = pool.restoreSavedState("t", fresh, "https://example.com/page")

        assertThat(restored).isTrue()
        assertThat(fresh.reloads).isEqualTo(1)
        assertThat(fresh.loadedUrls).isEmpty()
        assertThat(WebViewStateStore.takeReloadPending("t")).isFalse()
    }

    @Test
    fun restoreWithoutMark_doesNotReload() {
        saveHistory("t")
        val fresh = FakeHistoryWebView(context, restoredUrl = "https://example.com/page")

        assertThat(pool.restoreSavedState("t", fresh, "https://example.com/page")).isTrue()

        assertThat(fresh.reloads).isEqualTo(0)
    }

    @Test
    fun restoredUrlDiffersFromTheTabUrl_noReloadBecauseTheCallerLoadsTheTabUrl() {
        saveHistory("t")
        WebViewStateStore.markReloadPending("t")
        val fresh = FakeHistoryWebView(context, restoredUrl = "https://example.com/old")

        val restored = pool.restoreSavedState("t", fresh, "https://example.com/new")

        assertThat(restored).isFalse()
        assertThat(fresh.reloads).isEqualTo(0)
        assertThat(WebViewStateStore.takeReloadPending("t")).isFalse()
    }

    @Test
    fun emptyHistoryWithAMark_dropsTheMarkAndDoesNotReload() {
        saveHistory("t")
        WebViewStateStore.markReloadPending("t")
        val fresh = FakeHistoryWebView(context, hasHistory = false)

        assertThat(pool.restoreSavedState("t", fresh, "https://example.com/page")).isFalse()

        assertThat(fresh.reloads).isEqualTo(0)
        assertThat(WebViewStateStore.takeReloadPending("t")).isFalse()
    }

    @Test
    fun noSavedStateWithAMark_dropsTheMarkSoTheCallerLoadsFresh() {
        WebViewStateStore.markReloadPending("t")
        val fresh = FakeHistoryWebView(context)

        assertThat(pool.restoreSavedState("t", fresh, "https://example.com/page")).isFalse()

        assertThat(fresh.reloads).isEqualTo(0)
        assertThat(WebViewStateStore.takeReloadPending("t")).isFalse()
    }
}
