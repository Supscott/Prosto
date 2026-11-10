package com.jestk.browser.engine

import android.content.Context
import android.os.Message
import android.webkit.WebView
import java.util.UUID

/**
 * Handles window creation requests (`window.open`) initiated by web applications,
 * binding the target [WebView] into [WebView.WebViewTransport] and dispatching the result
 * so that `window.opener` and popup authentication flows function seamlessly.
 */
object WindowTransportHandler {

    fun handleCreateWindow(
        resultMsg: Message?,
        context: Context,
        webViewPool: WebViewPool,
        openerUrl: String? = null,
        profileName: String? = null,
        onPopupOpened: (tabId: String) -> Unit
    ): Boolean {
        val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
        val newTabId = UUID.randomUUID().toString()
        val newWv = webViewPool.getOrCreateWebView(newTabId, context, profileName)
        (newWv as? NestedWebView)?.openerUrl = openerUrl
        transport.webView = newWv
        if (resultMsg.target != null) {
            resultMsg.sendToTarget()
        }
        onPopupOpened(newTabId)
        return true
    }
}
