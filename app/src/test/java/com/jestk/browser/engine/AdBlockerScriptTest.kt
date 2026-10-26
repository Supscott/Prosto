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
    }
}
