package com.jestk.browser.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.util.Log
import android.webkit.WebView
import com.jestk.browser.engine.adblock.PopupGate
import com.jestk.browser.engine.adblock.PopupHostList
import com.jestk.browser.engine.adblock.PopupSiteAllowlist
import com.jestk.browser.engine.adblock.isAdPopupUrl
import java.util.UUID

/**
 * Handles window creation requests (`window.open` / `<a target="_blank">`) initiated by web applications,
 * binding the target [WebView] into [WebView.WebViewTransport] and dispatching the result
 * so that `window.opener` and popup authentication flows function seamlessly.
 *
 * The transport is bound at once, but the popup only becomes a visible tab once [PopupGate] has
 * seen its first main-frame URL (or timed out); an ad popup is destroyed without ever showing.
 */
object WindowTransportHandler {
    private const val TAG = "WindowTransportHandler"

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var adsEnabled: () -> Boolean = { true }

    /** Opener sites whose popups are never dropped (filled by the per-site allowlist). */
    var popupAllowlist: PopupSiteAllowlist
        get() = gate.allowlist
        set(value) {
            gate.allowlist = value
        }

    /** Called on the main thread after an ad popup was dropped: (opener url, dropped url). */
    @Volatile
    var onPopupDropped: ((openerUrl: String?, droppedUrl: String?) -> Unit)? = null

    internal var gate: PopupGate = newGate()

    internal fun newGate(clock: () -> Long = SystemClock::elapsedRealtime): PopupGate =
        PopupGate(clock = clock, isAdPopup = { adsEnabled() && isAdPopupUrl(it) })

    /** True while the popup [tabId] waits for its reveal decision; the pool must keep its WebView. */
    fun isPopupPending(tabId: String): Boolean = gate.isPending(tabId)

    fun handleCreateWindow(
        resultMsg: Message?,
        context: Context,
        webViewPool: WebViewPool,
        openerUrl: String? = null,
        profileName: String? = null,
        isUserGesture: Boolean = true,
        isAdBlockerEnabled: () -> Boolean = { true },
        onPopupOpened: (tabId: String) -> Unit
    ): Boolean {
        if (resultMsg == null) return false
        val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
        if (resultMsg.target == null) return false

        adsEnabled = isAdBlockerEnabled
        PopupHostList.warm(context)
        val newTabId = UUID.randomUUID().toString()
        val newWv = try {
            webViewPool.getOrCreateWebView(newTabId, context, profileName = profileName)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to create popup WebView", t)
            return false
        }

        val activeGate = gate
        newWv.tag = newTabId
        (newWv as? NestedWebView)?.apply {
            this.openerUrl = openerUrl
            this.hasOpenerUserGesture = isUserGesture
            this.popupGate = activeGate
        }
        activeGate.register(
            tabId = newTabId,
            openerUrl = openerUrl,
            onReveal = {
                onMain {
                    (newWv as? NestedWebView)?.popupGate = null
                    onPopupOpened(newTabId)
                }
            },
            onDrop = { url -> onMain { dropPopup(webViewPool, newWv, newTabId, openerUrl, url) } },
        )

        return try {
            transport.webView = newWv
            resultMsg.sendToTarget()
            mainHandler.postDelayed({ activeGate.sweep() }, PopupGate.DEFAULT_TIMEOUT_MS)
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to bind and dispatch WebViewTransport", t)
            activeGate.forget(newTabId)
            try {
                webViewPool.removeWebView(newTabId)
            } catch (_: Throwable) {}
            false
        }
    }

    private fun dropPopup(pool: WebViewPool, webView: WebView, tabId: String, openerUrl: String?, url: String?) {
        (webView as? NestedWebView)?.popupGate = null
        runCatching { webView.stopLoading() }
        pool.removeWebView(tabId, keepState = false)
        AdBlockerEngine.recordBlockedRequest(false)
        onPopupDropped?.invoke(openerUrl, url)
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }
}
