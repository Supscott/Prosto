package com.jestk.browser.engine

import android.content.Context
import android.content.MutableContextWrapper
import android.os.Looper
import android.util.Log
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Keeps one configured, unused WebView ready for the next tab, like Chrome's spare renderer:
 * building a WebView costs ~50 ms on the main thread, so it is built while the main thread is
 * idle instead of when the user opens a tab. Main thread only.
 */
internal class SpareWebView(
    private val create: (context: Context, profileName: String?) -> WebView,
) {
    private var spare: WebView? = null
    private var spareProfile: String? = null
    private var isScheduled = false

    /** Builds a spare for [profileName] on the next main-thread idle, unless one exists. */
    fun schedule(context: Context, profileName: String?) {
        if (spare != null || isScheduled) return
        isScheduled = true
        Looper.getMainLooper().queue.addIdleHandler {
            isScheduled = false
            if (spare == null) {
                try {
                    spare = create(MutableContextWrapper(context), profileName).also(::guardRendererDeath)
                    spareProfile = profileName
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed to build spare WebView", t)
                }
            }
            false
        }
    }

    /**
     * Hands out the spare when it was built for [profileName], rebound to [context]. A spare of
     * another profile is destroyed: a WebView's profile cannot change after it is set.
     */
    fun take(context: Context, profileName: String?): WebView? {
        val webView = spare ?: return null
        spare = null
        if (spareProfile != profileName) {
            destroy(webView)
            return null
        }
        (webView.context as? MutableContextWrapper)?.baseContext = context
        return webView
    }

    fun clear() {
        spare?.let(::destroy)
        spare = null
    }

    /**
     * The spare already shares the app's renderer, so it is told when that renderer dies. Without
     * a client of its own it would leave the death unhandled and Android would kill the whole
     * app. Taking the spare for a tab replaces this client with the tab's.
     */
    private fun guardRendererDeath(webView: WebView) {
        webView.webViewClient = object : WebViewClient() {
            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                Log.w(TAG, "Renderer gone, dropping the spare WebView")
                if (spare === view) spare = null
                view?.let(::destroy)
                return true
            }
        }
    }

    private fun destroy(webView: WebView) {
        try {
            webView.destroy()
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to destroy spare WebView", t)
        }
    }

    private companion object {
        const val TAG = "SpareWebView"
    }
}
