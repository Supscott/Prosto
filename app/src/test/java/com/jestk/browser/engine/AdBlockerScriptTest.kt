package com.jestk.browser.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AdBlockerScriptTest {

    @Test
    fun adSelectors_isPopulatedWithKeyAdSelectors() {
        assertThat(AdBlockerScript.AD_SELECTORS).isNotEmpty()
        assertThat(AdBlockerScript.AD_SELECTORS).contains("ins.adsbygoogle")
        assertThat(AdBlockerScript.AD_SELECTORS).contains("[id^=\"google_ads_iframe\"]")
        assertThat(AdBlockerScript.AD_SELECTORS).contains("[id^=\"div-gpt-ad\"]")
        assertThat(AdBlockerScript.AD_SELECTORS).contains(".yandex-adaptive")
        assertThat(AdBlockerScript.AD_SELECTORS).contains("[id^=\"yandex_rtb_\"]")
        assertThat(AdBlockerScript.AD_SELECTORS).contains("[id^=\"adfox\"]")
        assertThat(AdBlockerScript.AD_SELECTORS).contains("[class*=\"bannerContainer\"]")
        assertThat(AdBlockerScript.AD_SELECTORS).contains("[class*=\"incontentBanner\"]")
        assertThat(AdBlockerScript.AD_SELECTORS).contains("[class*=\"RightsideBanners\"]")
        assertThat(AdBlockerScript.AD_SELECTORS).contains(".ad-container")
        assertThat(AdBlockerScript.AD_SELECTORS).contains(".ad-banner")
        assertThat(AdBlockerScript.AD_SELECTORS).contains(".advertisement")
    }

    @Test
    fun buildCosmeticCss_containsCollapseStylesAndAllSelectors() {
        val css = AdBlockerScript.buildCosmeticCss()
        assertThat(css).contains("display: none !important")
        assertThat(css).contains("height: 0 !important")
        assertThat(css).contains("min-height: 0 !important")
        assertThat(css).contains("max-height: 0 !important")
        assertThat(css).contains("visibility: hidden !important")
        assertThat(css).contains("pointer-events: none !important")
        assertThat(css).contains("ins.adsbygoogle")
        assertThat(css).contains(".yandex-adaptive")
        assertThat(css).contains("[id^=\"adfox\"]")
        assertThat(css).contains("[class*=\"bannerContainer\"]")
        assertThat(css).contains(".ad-banner")
    }

    @Test
    fun buildSurrogateScript_declaresAdSenseGptAndYandexStubs() {
        val js = AdBlockerScript.buildSurrogateScript()
        // Google Ad Status
        assertThat(js).contains("window.google_ad_status = 1")

        // AdSense
        assertThat(js).contains("window.adsbygoogle = window.adsbygoogle || []")
        assertThat(js).contains("window.adsbygoogle.loaded = true")
        assertThat(js).contains("window.adsbygoogle.push")

        // GPT
        assertThat(js).contains("window.googletag = window.googletag || {}")
        assertThat(js).contains("window.googletag.cmd")
        assertThat(js).contains("window.googletag.pubads")
        assertThat(js).contains("window.googletag.apiReady = true")

        // Yandex & ADFOX
        assertThat(js).contains("window.yaContextCb = window.yaContextCb || []")
        assertThat(js).contains("window.yaContextCb.push")
        assertThat(js).contains("window.Ya = window.Ya || {}")
        assertThat(js).contains("window.Ya.adfoxCode")
        assertThat(js).contains("window.Ya.Context")

        // X5 Media
        assertThat(js).contains("window.x5media")
    }

    @Test
    fun buildDocumentStartScript_containsBridgeCheckStyleInjectionAndMutationObserver() {
        val js = AdBlockerScript.buildDocumentStartScript()
        // Idempotency guard
        assertThat(js).contains("window.__hori_adblock_initialized")

        // Bridge check
        assertThat(js).contains("window.HoriAdBlockBridge")
        assertThat(js).contains("window.HoriAdBlockBridge.isEnabled()")

        // Style tag injection
        assertThat(js).contains("__hori_adblock_css")
        assertThat(js).contains("document.createElement('style')")

        // MutationObserver & debounced addedNodes parent collapsing
        assertThat(js).contains("MutationObserver")
        assertThat(js).contains("observe(document.documentElement || document.body")
        assertThat(js).contains("mutations[i].addedNodes")
        assertThat(js).contains("requestAnimationFrame")
        assertThat(js).contains("parent.children.length === 1")
        assertThat(js).contains("[id^=\"adfox\"]")
        assertThat(js).contains("[class*=\"bannerContainer\"]")

        // YouTube Fast-Start integrated
        assertThat(js).contains("ytInitialPlayerResponse")
        assertThat(js).contains("adPlacements")
        assertThat(js).contains("adSlots")
        assertThat(js).contains("adBlockerActive")
    }

    @Test
    fun buildYouTubeFastStartScript_containsPrunerHooksAndSkipAccelerator() {
        val js = AdBlockerScript.buildYouTubeFastStartScript()

        // Domain guard
        assertThat(js).contains("youtube.com")
        assertThat(js).contains("youtu.be")

        // Clean YouTube Player interception hooks
        assertThat(js).contains("tryCleanPlayer")
        assertThat(js).contains("openCleanPlayer")
        assertThat(js).contains("origPushState = history.pushState")
        assertThat(js).contains("origReplaceState = history.replaceState")

        // JSON pruner properties
        assertThat(js).contains("adPlacements")
        assertThat(js).contains("playerAds")
        assertThat(js).contains("adSlots")
        assertThat(js).contains("adBreakHeartbeatParams")

        // Cold load interception hooks
        assertThat(js).contains("Object.defineProperty(window, 'ytInitialPlayerResponse'")
        assertThat(js).contains("Object.defineProperty(window, 'ytInitialData'")
        assertThat(js).contains("Object.defineProperty(window, 'ytplayer'")
        assertThat(js).contains("origParse = JSON.parse")
        assertThat(js).contains("Response.prototype.json")

        // Mobile DOM & Ad-skip acceleration
        assertThat(js).contains(".ytp-ad-skip-button")
        assertThat(js).contains("button[aria-label*=\"Skip\"]")
        assertThat(js).contains(".ad-showing")
        assertThat(js).contains("v.playbackRate = 16.0")
        assertThat(js).contains("v.currentTime = v.duration")

        // XHR / text interception without intrusive player watchdog or inline spoofing
        assertThat(js).contains("Response.prototype.text")
        assertThat(js).contains("XMLHttpRequest.prototype")
        assertThat(js).contains("pruneRawText")
        assertThat(js).doesNotContain("isInlinePlaybackNoAd")
        assertThat(js).doesNotContain("checkWatchPlayback")

        // Recursive array pruning in pruneAdProps
        assertThat(js).contains("Array.isArray(obj)")

        // Zero intrusive watchdog or periodic polling
        assertThat(js).doesNotContain("setInterval(checkWatchPlayback")
    }

    @Test
    fun buildDocumentStartScript_containsClickjackingOverlayNeutralizer() {
        val script = AdBlockerScript.buildDocumentStartScript()
        assertThat(script).contains("neutralizeClickjackingOverlays")
        assertThat(script).contains("pointer-events")
    }

    @Test
    fun buildAutoConsentScript_containsLoopGuardAndHandlingFlag() {
        val script = AdBlockerScript.buildAutoConsentScript()
        assertThat(script).contains("data-hori-consent-handled")
        assertThat(script).contains("__hori_autoconsent_initialized")
    }

    @Test
    fun buildAutoConsentScript_containsKeyCmpSelectors() {
        val script = AdBlockerScript.buildAutoConsentScript()
        // OneTrust
        assertThat(script).contains("onetrust")
        // Cookiebot
        assertThat(script).contains("CybotCookiebotDialog")
        // Didomi
        assertThat(script).contains("didomi")
        // Quantcast
        assertThat(script).contains("qc-cmp2")
        // Iubenda
        assertThat(script).contains("iubenda")
        // Yandex / generic cookie banners
        assertThat(script).contains("cookie")
    }

    @Test
    fun buildAutoConsentScript_containsPriorityHierarchyAndMultiLingualMatchers() {
        val script = AdBlockerScript.buildAutoConsentScript()
        // Priority 1: Reject All / Отклонить все
        assertThat(script).contains("reject all")
        assertThat(script).contains("отклонить")
        assertThat(script).contains("refuse")
        assertThat(script).contains("deny")

        // Priority 2: Necessary only / Только необходимые
        assertThat(script).contains("necessary")
        assertThat(script).contains("только необходимые")
        assertThat(script).contains("essential")
    }

    @Test
    fun buildAutoConsentScript_containsScrollUnlockAndModalLockSanitizer() {
        val script = AdBlockerScript.buildAutoConsentScript()
        // Scroll restoration
        assertThat(script).contains("overflow")
        assertThat(script).contains("touch-action")
        assertThat(script).contains("document.documentElement")
        assertThat(script).contains("document.body")
        // Class removal
        assertThat(script).contains("modal-open")
    }

    @Test
    fun buildAutoConsentScript_observesDynamicMutations() {
        val script = AdBlockerScript.buildAutoConsentScript()
        assertThat(script).contains("MutationObserver")
        assertThat(script).contains("childList")
        assertThat(script).contains("subtree")
    }

    @Test
    fun buildDocumentStartScript_whitelistsCloudflareAndSecurityChallenges() {
        val script = AdBlockerScript.buildDocumentStartScript()
        assertThat(script).contains("challenges.cloudflare.com")
        assertThat(script).contains("cloudflarecaptcha.com")
        assertThat(script).contains("hcaptcha.com")
        assertThat(script).contains("recaptcha.net")
        assertThat(script).contains("/cdn-cgi/challenge-platform")
        assertThat(script).contains("_cf_chl_opt")
        assertThat(script).contains("challenge-stage")
    }

    @Test
    fun buildAutoConsentScript_whitelistsCloudflareAndSecurityChallenges() {
        val script = AdBlockerScript.buildAutoConsentScript()
        assertThat(script).contains("challenges.cloudflare.com")
        assertThat(script).contains("cloudflarecaptcha.com")
        assertThat(script).contains("hcaptcha.com")
        assertThat(script).contains("recaptcha.net")
        assertThat(script).contains("/cdn-cgi/challenge-platform")
        assertThat(script).contains("_cf_chl_opt")
        assertThat(script).contains("challenge-stage")
    }
}
