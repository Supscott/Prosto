package com.jestk.browser.engine

import android.content.Context
import android.util.Log
import android.webkit.WebView
import java.util.concurrent.ConcurrentHashMap

/**
 * Provides client-side ad-blocking logic including:
 * 1. Cosmetic filtering CSS to collapse ad slots and placeholders to 0 dimensions.
 * 2. Scriptlet surrogates (mocks) for common ad network SDKs (Google AdSense, GPT, Yandex) to stop infinite loading spinners.
 * 3. DOM Sanitizer utilizing MutationObserver to hide dynamic ad wrappers and single-child ad parent containers.
 */
object AdBlockerScript {

    private const val TAG = "AdBlockerScript"

    private const val DOCUMENT_START_ASSET = "scripts/document_start.js"
    private const val YOUTUBE_FAST_START_ASSET = "scripts/youtube_fast_start.js"
    private const val AUTOCONSENT_ASSET = "scripts/autoconsent.js"
    private const val YOUTUBE_PLACEHOLDER = "/*@@YOUTUBE_FAST_START@@*/"
    private const val CSS_PLACEHOLDER = "@@COSMETIC_CSS@@"

    private val assetCache = ConcurrentHashMap<String, String>()

    /**
     * Common ad container and slot CSS selectors.
     */
    val AD_SELECTORS = listOf(
        // Google AdSense & DoubleClick
        "ins.adsbygoogle",
        ".adsbygoogle",
        "[id^=\"google_ads_iframe\"]",
        "[id^=\"div-gpt-ad\"]",
        "[data-google-query-id]",
        "[data-ad-client]",
        "[data-ad-slot]",
        "[data-ad-unit]",
        "[data-ad-format]",
        "iframe[src*=\"googlesyndication.com\"]",
        "iframe[src*=\"doubleclick.net\"]",

        // Yandex Direct, ADFOX & RTB
        "[id^=\"adfox\"]",
        "div[id^=\"adfox\"]",
        "[class*=\"adfox\"]",
        "[data-adfox]",
        ".yandex-adaptive",
        "[id^=\"yandex_rtb_\"]",
        "[id^=\"yandex_\"]",
        "[id^=\"ya_partner\"]",
        ".ya-partner",
        "[class*=\"ya-distr\"]",
        "div[data-ya-unit]",

        // Universal ad slots, banners, and sponsored containers (including Next.js CSS module patterns)
        ".ad-container",
        ".ad-wrapper",
        ".ad-box",
        ".ad-banner",
        ".ad-slot",
        ".ad-header",
        ".ad-footer",
        ".ad-sidebar",
        ".advertisement",
        ".banner-ad",
        ".sponsor-container",
        "[class*=\"ad-slot\"]",
        "[class*=\"ad_slot\"]",
        "[class*=\"sponsored-post\"]",
        "[class*=\"SponsoredMessage\"]",
        "[class*=\"BannerWrapper\"]",
        "[class*=\"advert-banner\"]",
        "[class*=\"native-ad\"]",
        "[data-ad-placeholder]",
        "[data-advertisement]",
        "[class*=\"bannerContainer\"]",
        "[class*=\"banner_container\"]",
        "[class*=\"banner-container\"]",
        "[class*=\"incontentBanner\"]",
        "[class*=\"IncontentBanner\"]",
        "[class*=\"incontent_banner\"]",
        "[class*=\"RightsideBanners\"]",
        "[class*=\"BannersBlock\"]",
        "[class*=\"adFeedback\"]",
        "[class*=\"banner-wrapper\"]",
        "[class*=\"banner_wrapper\"]",
        "[class*=\"bannerWrap\"]",
        "[class*=\"banner-block\"]",
        "[class*=\"banner_block\"]",
        "[class*=\"bannerBlock\"]",
        "[class*=\"tovarnayaCourusel\"]",
        "[class*=\"tovarnayaCarousel\"]"
    )

    /**
     * Builds the raw CSS string for cosmetic filtering.
     */
    fun buildCosmeticCss(): String {
        val selectorString = AD_SELECTORS.joinToString(",\n")
        return """
            $selectorString {
                display: none !important;
                height: 0 !important;
                min-height: 0 !important;
                max-height: 0 !important;
                margin: 0 !important;
                padding: 0 !important;
                visibility: hidden !important;
                opacity: 0 !important;
                pointer-events: none !important;
            }
        """.trimIndent()
    }

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
     * The complete document start script, from `assets/scripts/document_start.js`, with the
     * YouTube scriptlet and the cosmetic CSS filled in:
     * - Surrogates (Google AdSense, GPT, Yandex)
     * - YouTube Fast-Start (JSON player response pruning & ad skip accelerator)
     * - Immediate style element creation
     * - MutationObserver to collapse dynamic ad containers and empty parent wrappers
     */
    fun buildDocumentStartScript(context: Context): String {
        val cssContent = buildCosmeticCss().replace("\\", "\\\\").replace("`", "\\`").replace("\n", " ")
        return readAsset(context, DOCUMENT_START_ASSET)
            .replace(YOUTUBE_PLACEHOLDER, buildYouTubeFastStartScript(context))
            .replace(CSS_PLACEHOLDER, cssContent)
    }

    /**
     * Evaluates the ad blocker script on the provided WebView safely.
     */
    fun injectAdBlocker(webView: WebView) {
        try {
            val script = buildDocumentStartScript(webView.context)
            webView.evaluateJavascript(script, null)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to inject ad blocker script into WebView", t)
        }
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
