package com.jestk.browser.engine

import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Client of a WebView that has no tab client: one not bound to a tab yet, or one being torn down.
 * A renderer death then must not kill the app.
 */
internal object DestroyingWebViewClient : WebViewClient() {
    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean = true
}
