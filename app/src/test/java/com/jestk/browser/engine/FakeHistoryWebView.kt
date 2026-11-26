package com.jestk.browser.engine

import android.content.Context
import android.os.Bundle
import android.webkit.WebBackForwardList
import android.webkit.WebHistoryItem
import android.webkit.WebView

/**
 * A WebView whose history calls are faked: [hasHistory] decides whether a restore finds a history,
 * [restoredUrl] is the page a restore lands on, and [reloads] / [loadedUrls] record the navigations.
 */
internal class FakeHistoryWebView(
    context: Context,
    private val hasHistory: Boolean = true,
    private val restoredUrl: String? = null,
) : WebView(context) {
    var reloads = 0
    val loadedUrls = mutableListOf<String>()
    private var shownUrl: String? = null

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

    override fun restoreState(inState: Bundle): WebBackForwardList? {
        val restored = history()
        if (restored != null) shownUrl = restoredUrl
        return restored
    }

    override fun getUrl(): String? = shownUrl

    override fun loadUrl(url: String) {
        loadedUrls += url
        shownUrl = url
    }

    override fun reload() {
        reloads++
    }
}
