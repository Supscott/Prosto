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
    private val genericProvider: (classes: List<String>, ids: List<String>, exceptions: Set<String>) -> List<String> =
        AdBlockerEngine::hiddenClassIdSelectors,
) {
    private class FrameContext(val exceptions: Set<String>, val generichide: Boolean)

    /** Small LRU of the generic-rule exceptions the last `cosmetic()` answers computed, keyed by frame URL. */
    private val frames = object : LinkedHashMap<String, FrameContext>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FrameContext>) = size > FRAME_CACHE
    }

    constructor(isEnabledProvider: () -> Boolean) : this(isEnabledProvider, null, null, null, null)

    @JavascriptInterface
    fun isEnabled(): Boolean = isEnabledProvider()

    /**
     * Cosmetic data for [url] as JSON: `{css: [chunk, ...], generichide, exceptions, scriptlets}`.
     * `{}` when ads are off, for non-http(s) URLs, security-challenge and auth pages, and when
     * the engines have nothing for the URL.
     */
    @JavascriptInterface
    fun cosmetic(url: String?): String {
        if (url.isNullOrEmpty() || !isEnabledProvider()) return EMPTY_JSON
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) return EMPTY_JSON
        if (JestkWebViewClient.isSecurityChallengePage(url, null)) return EMPTY_JSON
        return try {
            val resources = cosmeticProvider(url, true, isTrackersEnabledProvider())
            synchronized(frames) { frames[url] = FrameContext(resources.exceptions, resources.generichide) }
            encode(resources)
        } catch (_: Exception) {
            EMPTY_JSON
        }
    }

    /**
     * CSS rules (one `selector{display:none!important}` per line) for the generic class/id hide
     * rules that match [classes] and [ids], two space-separated token lists from the frame at [url].
     * Empty when ads are off, for non-http(s) and challenge pages, for `$generichide` frames and
     * on any engine failure. Oversized input is truncated, never rejected.
     */
    @JavascriptInterface
    fun generic(url: String?, classes: String?, ids: String?): String {
        if (url.isNullOrEmpty() || !isEnabledProvider()) return ""
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) return ""
        if (JestkWebViewClient.isSecurityChallengePage(url, null)) return ""
        val classTokens = tokens(classes)
        val idTokens = tokens(ids)
        if (classTokens.isEmpty() && idTokens.isEmpty()) return ""
        return try {
            val frame = frameContext(url)
            if (frame.generichide) return ""
            genericProvider(classTokens, idTokens, frame.exceptions)
                .filter { it.none { c -> c == '{' || c == '}' || c == '\n' || c == '\r' } }
                .distinct()
                .joinToString("\n") { it + HIDE_DECLARATION }
        } catch (_: Exception) {
            ""
        }
    }

    private fun frameContext(url: String): FrameContext {
        synchronized(frames) { frames[url] }?.let { return it }
        val resources = cosmeticProvider(url, true, isTrackersEnabledProvider())
        return FrameContext(resources.exceptions, resources.generichide).also { synchronized(frames) { frames[url] = it } }
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

        private const val FRAME_CACHE = 16
        private const val MAX_TOKENS = 1_000
        private const val MAX_TOKEN_CHARS = 100
        private const val MAX_INPUT_CHARS = 60_000

        /** Splits a space-separated token list; the input and every list are capped, an over-long token is dropped. */
        internal fun tokens(input: String?): List<String> {
            if (input.isNullOrBlank()) return emptyList()
            val bounded = if (input.length > MAX_INPUT_CHARS) input.take(MAX_INPUT_CHARS).substringBeforeLast(' ') else input
            return bounded.split(' ').filter { it.isNotEmpty() && it.length <= MAX_TOKEN_CHARS }.distinct().take(MAX_TOKENS)
        }

        fun encode(resources: CosmeticResources): String {
            // `{}` means "nothing for this frame, no generic rules either"; a frame without
            // site-specific rules still gets a real answer so the page can ask for generic ones.
            if (resources.generichide && resources.hideSelectors.isEmpty() && resources.scriptlets.isEmpty()) return EMPTY_JSON
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
