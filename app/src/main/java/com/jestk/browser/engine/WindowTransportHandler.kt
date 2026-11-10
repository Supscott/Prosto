package com.jestk.browser.engine

import android.content.Context
import android.os.Message
import android.util.Log
import android.webkit.WebView
import java.util.UUID

/**
 * Handles window creation requests (`window.open` / `<a target="_blank">`) initiated by web applications,
 * binding the target [WebView] into [WebView.WebViewTransport] and dispatching the result
 * so that `window.opener` and popup authentication flows function seamlessly.
 */
object WindowTransportHandler {
    private const val TAG = "WindowTransportHandler"

    fun handleCreateWindow(
        resultMsg: Message?,
        context: Context,
        webViewPool: WebViewPool,
        openerUrl: String? = null,
        profileName: String? = null,
        isUserGesture: Boolean = true,
        onPopupOpened: (tabId: String) -> Unit
    ): Boolean {
        if (resultMsg == null) return false
        val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
        if (resultMsg.target == null) return false

        val newTabId = UUID.randomUUID().toString()
        val newWv = try {
            webViewPool.getOrCreateWebView(newTabId, context, profileName = profileName)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to create popup WebView", t)
            return false
        }

        newWv.tag = newTabId
        (newWv as? NestedWebView)?.apply {
            this.openerUrl = openerUrl
            this.hasOpenerUserGesture = isUserGesture
        }

        return try {
            transport.webView = newWv
            resultMsg.sendToTarget()
            onPopupOpened(newTabId)
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to bind and dispatch WebViewTransport", t)
            try {
                webViewPool.removeWebView(newTabId)
            } catch (_: Throwable) {}
            false
        }
    }
}
