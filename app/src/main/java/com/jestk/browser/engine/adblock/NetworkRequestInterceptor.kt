package com.jestk.browser.engine.adblock

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.jestk.browser.R
import com.jestk.browser.engine.AdBlockerEngine
import com.jestk.browser.engine.NestedWebView
import com.jestk.browser.util.UrlSecurityUtils
import java.util.concurrent.ConcurrentHashMap

/**
 * Applies [NetworkBlockPolicy] to a WebView request and performs the side effects of a block
 * (stats, callbacks). Shared by tab WebViews and the service-worker client; may run on any thread.
 */
class NetworkRequestInterceptor(
    private val adsEnabled: () -> Boolean,
    private val trackersEnabled: () -> Boolean,
    private val isDocumentAllowed: (url: String) -> Boolean = { false },
    private val siteAllowlist: SiteAllowlist = SiteAllowlist.shared,
    private val onBlockedResource: ((url: String, isTracker: Boolean) -> Unit)? = null,
    /** A block page replaces the main-frame document of [url]; runs before the page commits. */
    private val onBlockedDocument: ((url: String) -> Unit)? = null,
) {
    /** Same for a tab WebView: its page URL and workspace feed the allowlist check. */
    fun interceptForTab(request: WebResourceRequest, webView: NestedWebView?, context: Context?): WebResourceResponse? =
        intercept(request, webView?.loadedUrl, context, webView?.workspaceId())

    /**
     * Returns the response that replaces the request, or null to let it load normally.
     * [workspaceId] is the workspace of the requesting tab, or null for service-worker requests
     * (then only all-workspaces allowlist entries apply).
     */
    fun intercept(
        request: WebResourceRequest,
        tabUrl: String?,
        context: Context? = null,
        workspaceId: String? = null,
    ): WebResourceResponse? {
        val url = request.url?.toString() ?: return null
        val headers = request.requestHeaders ?: emptyMap()
        if (context != null) HeaderLog.note(context, url, headers)
        val verdict = NetworkBlockPolicy.evaluate(
            request = NetworkRequest(url, request.isForMainFrame, headers, request.method.orEmpty()),
            tabUrl = tabUrl,
            adsEnabled = adsEnabled(),
            trackersEnabled = trackersEnabled(),
            isDocumentAllowed = isDocumentAllowed,
            isSiteAllowlisted = { siteAllowlist.isAllowlisted(it, workspaceId) },
        )
        return when (verdict) {
            NetworkVerdict.Allow -> null
            is NetworkVerdict.Block -> blocked(url, verdict.isTracker, verdict.response)
            is NetworkVerdict.Redirect -> blocked(url, verdict.isTracker, verdict.response)
            is NetworkVerdict.BlockDocument -> {
                AdBlockerEngine.recordBlockedRequest(false)
                onBlockedDocument?.invoke(verdict.url)
                BlockPage.response(
                    text = BlockPageText(
                        title = context?.getString(R.string.blocked_document_title).orEmpty(),
                        body = context?.getString(R.string.blocked_document_body).orEmpty(),
                        goBack = context?.getString(R.string.blocked_document_go_back).orEmpty(),
                        proceed = context?.getString(R.string.blocked_document_proceed).orEmpty(),
                    ),
                    host = UrlSecurityUtils.extractDisplayHost(verdict.url),
                )
            }
        }
    }

    private fun blocked(url: String, isTracker: Boolean, response: WebResourceResponse): WebResourceResponse {
        onBlockedResource?.invoke(url, isTracker)
        AdBlockerEngine.recordBlockedRequest(isTracker)
        return response
    }

    /** Debug builds log each distinct set of request header names once, to confirm what WebView supplies. */
    private object HeaderLog {
        private val seen = ConcurrentHashMap.newKeySet<String>()

        fun note(context: Context, url: String, headers: Map<String, String>) {
            if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
            val names = headers.keys.map { it.lowercase() }.sorted().joinToString(",")
            if (seen.add(names)) Log.d("AdBlockHeaders", "request headers [$names] first seen on $url")
        }
    }
}
