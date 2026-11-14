package com.jestk.browser.engine.adblock

import android.webkit.WebResourceResponse
import com.jestk.browser.engine.AdBlockerEngine

/** The parts of a `WebResourceRequest` the policy looks at. */
data class NetworkRequest(
    val url: String,
    val isForMainFrame: Boolean,
    val headers: Map<String, String>,
    val method: String = "GET",
)

/** What to do with one network request. */
sealed interface NetworkVerdict {
    data object Allow : NetworkVerdict

    data class Block(val response: WebResourceResponse, val isTracker: Boolean) : NetworkVerdict

    data class Redirect(val response: WebResourceResponse, val isTracker: Boolean) : NetworkVerdict

    /** A `$document` rule matched the top-level page: serve a block page instead of the site. */
    data class BlockDocument(val url: String) : NetworkVerdict
}

/**
 * The one policy every network hook asks (`shouldInterceptRequest` of tab WebViews and of the
 * service-worker client). Pure decision: callers do the stats and callbacks.
 */
object NetworkBlockPolicy {
    private const val CHALLENGE_PATH = "/cdn-cgi/challenge-platform"

    /**
     * Main-frame requests are only blocked by a native `$document` match; the host-list fallback
     * never blocks a top-level document. [tabUrl] is null for service-worker requests.
     */
    fun evaluate(
        request: NetworkRequest,
        tabUrl: String?,
        adsEnabled: Boolean,
        trackersEnabled: Boolean,
        isDocumentAllowed: (url: String) -> Boolean = { false },
    ): NetworkVerdict {
        if (!adsEnabled && !trackersEnabled) return NetworkVerdict.Allow
        val url = request.url
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            return NetworkVerdict.Allow
        }
        if (url.contains(CHALLENGE_PATH, ignoreCase = true)) return NetworkVerdict.Allow
        val host = UrlParser.parseUrl(url)?.host.orEmpty()
        if (host.isEmpty() || AuthDomainAllowlist.isAllowlistedAuthDomain(host)) return NetworkVerdict.Allow

        if (request.isForMainFrame) return documentVerdict(url, adsEnabled, isDocumentAllowed)

        val type = RequestClassifier.type(url, isForMainFrame = false, request.headers)
        val source = RequestClassifier.sourceUrl(url, isForMainFrame = false, request.headers, tabUrl)
        val decision = AdBlockerEngine.decide(url, source, type, adsEnabled, trackersEnabled)
        if (!decision.block) return NetworkVerdict.Allow
        val redirect = decision.redirectDataUrl
        return if (redirect != null) {
            NetworkVerdict.Redirect(BlockedResponses.fromDataUrl(redirect, request.headers), decision.isTracker)
        } else {
            NetworkVerdict.Block(
                BlockedResponses.createSmartBlockedResponse(url, requestHeaders = request.headers),
                decision.isTracker,
            )
        }
    }

    private fun documentVerdict(url: String, adsEnabled: Boolean, isDocumentAllowed: (String) -> Boolean): NetworkVerdict {
        if (!adsEnabled || !AdBlockerEngine.isNativeReady()) return NetworkVerdict.Allow
        if (isDocumentAllowed(url)) return NetworkVerdict.Allow
        val decision = AdBlockerEngine.decide(url, url, RequestType.DOCUMENT, adsEnabled = true, trackersEnabled = false)
        return if (decision.block) NetworkVerdict.BlockDocument(url) else NetworkVerdict.Allow
    }
}
