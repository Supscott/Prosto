package com.jestk.browser.engine

import android.content.Context
import android.util.Log
import android.webkit.WebView
import java.util.concurrent.ConcurrentHashMap

/**
 * Builds the injected scripts: the document-start content-blocking bootstrap (list-driven cosmetic
 * rules, scriptlets, clean-player and orientation relays) and the autoconsent scriptlet.
 */
object AdBlockerScript {

    private const val TAG = "AdBlockerScript"

    private const val BOOTSTRAP_ASSET = "scripts/content_bootstrap.js"
    private const val YOUTUBE_FAST_START_ASSET = "scripts/youtube_fast_start.js"
    private const val AUTOCONSENT_ASSET = "scripts/autoconsent.js"
    private const val SCRIPTLETS_ASSET = "scripts/scriptlets.js"
    private const val SCRIPTLETS_PLACEHOLDER = "/*@@SCRIPTLETS@@*/"
    private const val PROCEDURAL_ASSET = "scripts/procedural.js"
    private const val PROCEDURAL_PLACEHOLDER = "/*@@PROCEDURAL@@*/"
    private const val YOUTUBE_PLACEHOLDER = "/*@@YOUTUBE_FAST_START@@*/"
    private const val BRIDGE_PLACEHOLDER = "@@BRIDGE_NAME@@"

    private val assetCache = ConcurrentHashMap<String, String>()

    /**
     * The YouTube scriptlet (YouTube hosts only), from `assets/scripts/youtube_fast_start.js`:
     * 1. Clean-player link interception (clicks and `history.pushState/replaceState`), always on.
     * 2. When the ad blocker is on (`adBlockerActive` from the enclosing document-start script),
     *    prunes adPlacements, playerAds, adSlots and adBreakHeartbeatParams from
     *    `ytInitialPlayerResponse`, `ytInitialData`, `ytplayer`, `JSON.parse`,
     *    `Response.prototype.json/text` and XHR `responseText/response`, and fast-forwards
     *    fallback ad breaks.
     */
    fun buildYouTubeFastStartScript(context: Context): String =
        readAsset(context, YOUTUBE_FAST_START_ASSET)

    /**
     * The document-start bootstrap, from `assets/scripts/content_bootstrap.js`, with the bridge
     * name, the YouTube scriptlet, the scriptlet bundle (`assets/scripts/scriptlets.js`) and the
     * procedural cosmetic runner (`assets/scripts/procedural.js`) filled in; the last two are function
     * expressions that stay closure-local and are evaluated only by frames that need them. It runs in
     * every frame before page scripts: it captures and deletes the hidden bridge global, skips
     * security challenges, relays fullscreen orientation locks, runs the list scriptlets and applies
     * list-driven cosmetic CSS (site rules, then generic class/id rules as the page's DOM grows) and
     * procedural filters.
     */
    fun buildBootstrapScript(context: Context, bridgeName: String): String {
        return readAsset(context, BOOTSTRAP_ASSET)
            .replace(YOUTUBE_PLACEHOLDER, buildYouTubeFastStartScript(context))
            .replace(SCRIPTLETS_PLACEHOLDER, readAsset(context, SCRIPTLETS_ASSET))
            .replace(PROCEDURAL_PLACEHOLDER, readAsset(context, PROCEDURAL_ASSET))
            .replace(BRIDGE_PLACEHOLDER, bridgeName)
    }

    /**
     * The Autoconsent scriptlet for automated dismissal and rejection of cookie banners, from
     * `assets/scripts/autoconsent.js`. Follows a 3-tier hierarchy:
     * 1. Locate and click "Reject All" / "Отклонить все" / "Refuse" / "Deny".
     * 2. Locate and click "Necessary only" / "Только необходимые" / "Essential".
     * 3. Fallback: hide CMP banner & backdrop elements, and unlock document.body and document.documentElement scroll styles.
     * Prevents infinite loops and re-triggering via data-hori-consent-handled attribute.
     */
    fun buildAutoConsentScript(context: Context): String = readAsset(context, AUTOCONSENT_ASSET)

    /**
     * Injects the autoconsent scriptlet on the provided WebView safely.
     */
    fun injectAutoConsent(webView: WebView) {
        try {
            val script = buildAutoConsentScript(webView.context)
            webView.evaluateJavascript(script, null)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to inject autoconsent script into WebView", t)
        }
    }

    /** Scripts are bundled and never change at runtime: each asset is read once per process. */
    private fun readAsset(context: Context, path: String): String =
        assetCache.getOrPut(path) {
            context.applicationContext.assets.open(path).bufferedReader().use { it.readText() }.trimEnd()
        }
}
