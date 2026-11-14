package com.jestk.browser.engine.adblock

import android.webkit.JavascriptInterface
import com.jestk.browser.engine.AdBlockerEngine
import com.jestk.browser.engine.JestkWebViewClient
import org.json.JSONArray
import org.json.JSONObject

/**
 * The page-facing bridge, registered per WebView under a random name by [ContentBlockingSetup].
 * The document-start bootstrap captures it and deletes the global before page scripts run, so
 * pages never see it. It answers only about the URL a frame passes in and reveals nothing but the
 * CSS and scriptlet data that frame needs.
 */
class ContentBlockingBridge(
    private val isEnabledProvider: () -> Boolean,
    private val onCleanPlayerRequested: ((url: String) -> Boolean)? = null,
    private val isCleanPlayerEnabledProvider: (() -> Boolean)? = null,
    private val onOrientationLockRequested: ((orientation: String?) -> Unit)? = null,
    private val onOrientationUnlockRequested: (() -> Unit)? = null,
    private val isTrackersEnabledProvider: () -> Boolean = { true },
    private val cosmeticProvider: (url: String, ads: Boolean, trackers: Boolean) -> CosmeticResources =
        AdBlockerEngine::cosmeticResources,
    private val isSiteAllowlisted: (frameUrl: String) -> Boolean = { false },
) {
    constructor(isEnabledProvider: () -> Boolean) : this(isEnabledProvider, null, null, null, null)

    @JavascriptInterface
    fun isEnabled(): Boolean = isEnabledProvider()

    /**
     * Cosmetic data for [url] as JSON: `{css: [chunk, ...], generichide, exceptions, scriptlets}`.
     * `{}` when ads are off, for non-http(s) URLs, security-challenge and auth pages, on allowlisted
     * sites, and when the engines have nothing for the URL.
     */
    @JavascriptInterface
    fun cosmetic(url: String?): String {
        if (url.isNullOrEmpty() || !isEnabledProvider()) return EMPTY_JSON
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) return EMPTY_JSON
        if (JestkWebViewClient.isSecurityChallengePage(url, null)) return EMPTY_JSON
        if (isBlockingOffFor(url)) return EMPTY_JSON
        return try {
            encode(cosmeticProvider(url, true, isTrackersEnabledProvider()))
        } catch (_: Exception) {
            EMPTY_JSON
        }
    }

    /** Every method that serves blocking data for a frame calls this first: the user switched blocking off for its site. */
    private fun isBlockingOffFor(frameUrl: String): Boolean =
        try {
            isSiteAllowlisted(frameUrl)
        } catch (_: Exception) {
            false
        }

    @JavascriptInterface
    fun isCleanPlayerEnabled(): Boolean = isCleanPlayerEnabledProvider?.invoke() ?: false

    @JavascriptInterface
    fun openCleanPlayer(url: String): Boolean {
        if (isCleanPlayerEnabledProvider?.invoke() != true) return false
        return onCleanPlayerRequested?.invoke(url) ?: false
    }

    @JavascriptInterface
    fun lockOrientation(orientation: String?) {
        onOrientationLockRequested?.invoke(orientation)
    }

    @JavascriptInterface
    fun unlockOrientation() {
        onOrientationUnlockRequested?.invoke()
    }

    internal companion object {
        const val EMPTY_JSON = "{}"

        /** One rule stays below roughly this many characters; a longer selector list is split. */
        private const val CSS_CHUNK_CHARS = 8_000
        private const val HIDE_DECLARATION = "{display:none!important}"

        fun encode(resources: CosmeticResources): String {
            if (resources.hideSelectors.isEmpty() && resources.scriptlets.isEmpty()) return EMPTY_JSON
            return JSONObject()
                .put("css", JSONArray(cssChunks(resources.hideSelectors)))
                .put("generichide", resources.generichide)
                .put("exceptions", JSONArray(resources.exceptions.toList()))
                .put("scriptlets", JSONArray(resources.scriptlets.map { JSONArray(it) }))
                .toString()
        }

        internal fun cssChunks(selectors: List<String>): List<String> {
            val chunks = mutableListOf<String>()
            val current = StringBuilder()
            for (selector in selectors) {
                if (current.isNotEmpty() && current.length + selector.length > CSS_CHUNK_CHARS) {
                    chunks += current.append(HIDE_DECLARATION).toString()
                    current.setLength(0)
                }
                if (current.isNotEmpty()) current.append(',')
                current.append(selector)
            }
            if (current.isNotEmpty()) chunks += current.append(HIDE_DECLARATION).toString()
            return chunks
        }
    }
}
