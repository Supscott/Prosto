package com.jestk.browser.engine

import android.content.Context
import android.os.Bundle
import android.webkit.WebBackForwardList
import android.webkit.WebHistoryItem
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WebViewStateStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun writtenState_readsBackByteForByte() {
        val file = folder.newFile("tab.state")
        val entries = mapOf("WEBVIEW_CHROMIUM_STATE" to ByteArray(2048) { (it % 251).toByte() })

        WebViewStateStore.write(file, entries)
        val read = WebViewStateStore.readEntries(file)

        assertThat(read?.keys).containsExactly("WEBVIEW_CHROMIUM_STATE")
        assertThat(read!!.getValue("WEBVIEW_CHROMIUM_STATE")).isEqualTo(entries.getValue("WEBVIEW_CHROMIUM_STATE"))
    }

    /** A WebView whose history calls are faked: [hasHistory] controls whether a restore finds a history, [reloads] counts reloads. */
    private class FakeHistoryWebView(context: Context, private val hasHistory: Boolean) : WebView(context) {
        var reloads = 0

        private fun history(): WebBackForwardList? =
            if (!hasHistory) null else object : WebBackForwardList() {
                override fun getCurrentItem(): WebHistoryItem? = null
                override fun getCurrentIndex(): Int = 0
                override fun getItemAtIndex(index: Int): WebHistoryItem? = null
                override fun getSize(): Int = 1
                override fun clone(): WebBackForwardList = this
            }

        override fun saveState(outState: Bundle): WebBackForwardList? {
            outState.putByteArray("WEBVIEW_CHROMIUM_STATE", ByteArray(8))
            return history()
        }

        override fun restoreState(inState: Bundle): WebBackForwardList? = history()

        override fun reload() {
            reloads++
        }
    }

    private fun webView(hasHistory: Boolean = true) =
        FakeHistoryWebView(ApplicationProvider.getApplicationContext(), hasHistory)

    @Test
    fun pendingReload_restoresTheHistoryThenReloadsOnce() {
        WebViewStateStore.save("pending-tab", webView())
        WebViewStateStore.markReloadPending("pending-tab")
        val fresh = webView()

        assertThat(WebViewStateStore.restore("pending-tab", fresh)).isTrue()
        assertThat(fresh.reloads).isEqualTo(1)

        val next = webView()
        assertThat(WebViewStateStore.restore("pending-tab", next)).isTrue()
        assertThat(next.reloads).isEqualTo(0)
        WebViewStateStore.delete("pending-tab")
    }

    @Test
    fun withoutPendingReload_aRestoreDoesNotReload() {
        WebViewStateStore.save("plain-tab", webView())
        val fresh = webView()

        assertThat(WebViewStateStore.restore("plain-tab", fresh)).isTrue()
        assertThat(fresh.reloads).isEqualTo(0)
        WebViewStateStore.delete("plain-tab")
    }

    @Test
    fun pendingReloadWithoutSavedState_isDroppedAndTheCallerLoadsFresh() {
        WebViewStateStore.markReloadPending("nostate-tab")
        val fresh = webView()

        assertThat(WebViewStateStore.restore("nostate-tab", fresh)).isFalse()
        assertThat(fresh.reloads).isEqualTo(0)

        // Saved state appearing later must not trigger the stale mark.
        WebViewStateStore.save("nostate-tab", webView())
        val later = webView()
        assertThat(WebViewStateStore.restore("nostate-tab", later)).isTrue()
        assertThat(later.reloads).isEqualTo(0)
        WebViewStateStore.delete("nostate-tab")
    }

    @Test
    fun deletingTheTabDropsItsPendingReload() {
        WebViewStateStore.save("gone-tab", webView())
        WebViewStateStore.markReloadPending("gone-tab")
        WebViewStateStore.delete("gone-tab")
        WebViewStateStore.save("gone-tab", webView())
        val fresh = webView()

        assertThat(WebViewStateStore.restore("gone-tab", fresh)).isTrue()
        assertThat(fresh.reloads).isEqualTo(0)
        WebViewStateStore.delete("gone-tab")
    }

    @Test
    fun truncatedOrForeignFile_isRejected() {
        val file = folder.newFile("tab.state")
        WebViewStateStore.write(file, mapOf("k" to ByteArray(100)))
        file.writeBytes(file.readBytes().copyOf(20))
        assertThat(WebViewStateStore.readEntries(file)).isNull()

        file.writeText("not a state file")
        assertThat(WebViewStateStore.readEntries(file)).isNull()
    }
}
