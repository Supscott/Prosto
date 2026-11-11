package com.jestk.browser.engine

import android.content.Context
import android.content.MutableContextWrapper
import android.os.Looper
import android.util.Log
import android.webkit.WebView

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
                    spare = create(MutableContextWrapper(context), profileName)
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
