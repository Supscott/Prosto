package com.jestk.browser.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AdBlockerScriptTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private companion object {
        const val BRIDGE = "qWxYzAbCdEfGhI"
    }

    @Test
    fun buildBootstrapScript_hasNoStaticAdSelectorsOrDomCleaner() {
        val js = AdBlockerScript.buildBootstrapScript(context, BRIDGE)
        assertThat(js).doesNotContain("@@COSMETIC_CSS@@")
        assertThat(js).doesNotContain("ins.adsbygoogle")
        assertThat(js).doesNotContain("[id^=\"adfox\"]")
        assertThat(js).doesNotContain("bannerContainer")
        assertThat(js).doesNotContain("collapseElement")
        assertThat(js).doesNotContain("parent.children.length === 1")
        assertThat(js).doesNotContain("pendingNodes")
        assertThat(js).doesNotContain("processPendingNodes")
    }

    @Test
    fun buildBootstrapScript_collectsClassesAndIdsForTheGenericPath() {
        val js = AdBlockerScript.buildBootstrapScript(context, BRIDGE)
        assertThat(js).contains("cosmetic.generichide === false")
        assertThat(js).contains("bridge.generic(location.href")
        assertThat(js).contains("new MutationObserver")
        assertThat(js).contains("attributeFilter: ['class', 'id']")
        assertThat(js).contains("GENERIC_MAX_TOKENS")
        assertThat(js).contains("new Set()")
        assertThat(js).contains("cosmeticSheet.insertRule")
        assertThat(js).doesNotContain("window.generic")
    }

    @Test
    fun buildBootstrapScript_leavesNoFingerprintableGlobals() {
        // Review finding BOT-2: page-level stubs and guards showed up in Object.getOwnPropertyNames(window).
        val js = AdBlockerScript.buildBootstrapScript(context, BRIDGE)
        assertThat(js).doesNotContain("window.googletag =")
        assertThat(js).doesNotContain("window.adsbygoogle =")
        assertThat(js).doesNotContain("window.Ya =")
        assertThat(js).doesNotContain("window.x5media =")
        assertThat(js).doesNotContain("__hori_adblock_initialized")
        assertThat(js).doesNotContain("__hori_adblock_css")
        assertThat(js).doesNotContain(".toString = function")
        // The orientation bridge is installed only while the document is fullscreen.
        assertThat(js).contains("fullscreenchange")
    }

    @Test
    fun buildBootstrapScript_containsBridgeCheckAndStyleInjection() {
        val js = AdBlockerScript.buildBootstrapScript(context, BRIDGE)
        // Bridge check through the captured reference
        assertThat(js).contains("bridge.isEnabled()")
        assertThat(js).contains("bridge.cosmetic(location.href)")
        assertThat(js).doesNotContain("window.HoriAdBlockBridge")

        // Constructed stylesheet with a <style> fallback
        assertThat(js).contains("adoptedStyleSheets")
        assertThat(js).contains("document.createElement('style')")

        // YouTube Fast-Start integrated
        assertThat(js).contains("ytInitialPlayerResponse")
        assertThat(js).contains("adPlacements")
        assertThat(js).contains("adSlots")
        assertThat(js).contains("adBlockerActive")
    }

    @Test
    fun buildBootstrapScript_capturesAndDeletesTheBridgeBeforeAnythingElse() {
        val js = AdBlockerScript.buildBootstrapScript(context, BRIDGE)

        assertThat(js).doesNotContain("@@")
        assertThat(js).contains("window['$BRIDGE']")
        val capture = js.indexOf("var bridge = window['$BRIDGE'];")
        val delete = js.indexOf("delete window['$BRIDGE']")
        val challengeExemption = js.indexOf("challenges.cloudflare.com")
        assertThat(capture).isAtLeast(0)
        assertThat(delete).isGreaterThan(capture)
        assertThat(js.substring(capture, delete)).doesNotContain("\n        ")
        assertThat(challengeExemption).isGreaterThan(delete)
        assertThat(js.indexOf("bridge.")).isGreaterThan(delete)
    }

    @Test
    fun buildBootstrapScript_assignsNothingToWindow() {
        // The scriptlet bundle patches page globals on purpose when a list rule asks for it
        // (alert-buster, window.name-defuser); the bootstrap itself must assign none.
        val bundle = context.assets.open("scripts/scriptlets.js").bufferedReader().use { it.readText() }.trimEnd()
        val runnerText = context.assets.open("scripts/procedural.js").bufferedReader().use { it.readText() }.trimEnd()
        val js = AdBlockerScript.buildBootstrapScript(context, BRIDGE).replace(bundle, "").replace(runnerText, "")
        assertThat(Regex("""window\.[A-Za-z_$][\w$]*\s*=[^=]""").findAll(js).map { it.value }.toList()).isEmpty()
        assertThat(js).doesNotContain("HoriAdBlockBridge")
        val runner = context.assets.open("scripts/procedural.js").bufferedReader().use { it.readText() }.trimEnd()
        assertThat(Regex("""(window|win|globalThis|self)\.[A-Za-z_$][\w$]*\s*=[^=]""").findAll(runner).map { it.value }.toList()).isEmpty()
        assertThat(runner).doesNotContain("window.__")
        assertThat(runner).doesNotContain("new Function")
        assertThat(runner).doesNotContain("eval(")
    }

    @Test
    fun buildYouTubeFastStartScript_containsPrunerHooksAndSkipAccelerator() {
        val js = AdBlockerScript.buildYouTubeFastStartScript(context)

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
    fun buildBootstrapScript_doesNotScanThePageForOverlays() {
        // A full-page getComputedStyle scan on every mutation froze heavy pages and hid the tap
        // layers of video players and maps (review finding AB-5).
        val script = AdBlockerScript.buildBootstrapScript(context, BRIDGE)
        assertThat(script).doesNotContain("neutralizeClickjackingOverlays")
        assertThat(script).doesNotContain("querySelectorAll('div, a, span')")
    }

    @Test
    fun buildBootstrapScript_doesNotMonkeyPatchWindowOpen() {
        val script = AdBlockerScript.buildBootstrapScript(context, BRIDGE)
        assertThat(script).doesNotContain("window.open =")
        assertThat(script).doesNotContain("origWindowOpen")
        assertThat(script).doesNotContain("patchedOpen")
    }

    @Test
    fun buildAutoConsentScript_containsLoopGuardAndHandlingFlag() {
        val script = AdBlockerScript.buildAutoConsentScript(context)
        assertThat(script).contains("data-hori-consent-handled")
        assertThat(script).doesNotContain("__hori_autoconsent_initialized")
    }

    @Test
    fun buildAutoConsentScript_containsKeyCmpSelectors() {
        val script = AdBlockerScript.buildAutoConsentScript(context)
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
        val script = AdBlockerScript.buildAutoConsentScript(context)
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
        val script = AdBlockerScript.buildAutoConsentScript(context)
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
        val script = AdBlockerScript.buildAutoConsentScript(context)
        assertThat(script).contains("MutationObserver")
        assertThat(script).contains("childList")
        assertThat(script).contains("subtree")
    }

    @Test
    fun buildBootstrapScript_whitelistsCloudflareAndSecurityChallenges() {
        val script = AdBlockerScript.buildBootstrapScript(context, BRIDGE)
        assertThat(script).contains("challenges.cloudflare.com")
        assertThat(script).contains("cloudflarecaptcha.com")
        assertThat(script).contains("hcaptcha.com")
        assertThat(script).contains("recaptcha.net")
        assertThat(script).contains("/cdn-cgi/challenge-platform")
        assertThat(script).contains("_cf_chl_opt")
        assertThat(script).contains("challenge-stage")
    }

    @Test
    fun buildBootstrapScript_inlinesTheScriptletBundleOnceInsideTheClosure() {
        val js = AdBlockerScript.buildBootstrapScript(context, BRIDGE)
        val bundle = context.assets.open("scripts/scriptlets.js").bufferedReader().use { it.readText() }.trimEnd()

        assertThat(js).doesNotContain("@@SCRIPTLETS@@")
        assertThat(js.split("function prostoScriptlets()").size - 1).isEqualTo(1)
        assertThat(js).contains(bundle)
        // The bundle sits inside the bootstrap's own closure, after the bridge answer is read.
        assertThat(js.trimStart()).startsWith("(function() {")
        assertThat(js.indexOf("prostoScriptlets")).isGreaterThan(js.indexOf("bridge.cosmetic(location.href)"))
        assertThat(js.trimEnd()).endsWith("})();")
    }

    @Test
    fun buildBootstrapScript_runsScriptletsFromTheCosmeticAnswerWithoutGlobals() {
        val js = AdBlockerScript.buildBootstrapScript(context, BRIDGE)

        assertThat(js).contains("cosmetic.scriptlets")
        assertThat(js).contains("hasOwnProperty.call(scriptletMap")
        assertThat(js).doesNotContain("window.scriptlets")
        assertThat(js).doesNotContain("globalThis.scriptlets")
        assertThat(js).doesNotContain("new Function")
    }

    @Test
    fun buildBootstrapScript_inlinesTheProceduralRunnerOnceAndEvaluatesItOnlyWhenFiltersExist() {
        val js = AdBlockerScript.buildBootstrapScript(context, BRIDGE)
        val runner = context.assets.open("scripts/procedural.js").bufferedReader().use { it.readText() }.trimEnd()

        assertThat(js).doesNotContain("@@PROCEDURAL@@")
        assertThat(js).contains(runner)
        assertThat(js.split("function prostoProcedural(").size - 1).isEqualTo(1)
        // The call sits behind the frame's own answer, so frames without procedural filters never run it.
        assertThat(js).contains("cosmetic.procedural && cosmetic.procedural.length")
        assertThat(js).contains("(proceduralNatives, cosmetic.procedural)")
        assertThat(js.indexOf("prostoProcedural")).isGreaterThan(js.indexOf("bridge.cosmetic(location.href)"))
        assertThat(js.trimStart()).startsWith("(function() {")
        assertThat(js.trimEnd()).endsWith("})();")
    }

    @Test
    fun buildBootstrapScript_takesTheProceduralNativesBeforeTheScriptletsRun() {
        val js = AdBlockerScript.buildBootstrapScript(context, BRIDGE)

        val natives = js.indexOf("var proceduralNatives")
        assertThat(natives).isAtLeast(0)
        assertThat(natives).isLessThan(js.indexOf("var scriptletMap"))
        assertThat(js).contains("MutationObserver: window.MutationObserver")
        assertThat(js).contains("getComputedStyle: window.getComputedStyle")
    }

    @Test
    fun buildBootstrapScript_sharesTheCosmeticSheetBetweenGenericAndProceduralRules() {
        val js = AdBlockerScript.buildBootstrapScript(context, BRIDGE)

        assertThat(js.split("function appendCosmeticRules(").size - 1).isEqualTo(1)
        assertThat(js).contains("proceduralNatives.addRules = appendCosmeticRules")
        assertThat(js).contains("appendCosmeticRules(css)")
        assertThat(js).doesNotContain("appendGenericRules")
    }

    @Test
    fun buildAutoConsentScript_whitelistsCloudflareAndSecurityChallenges() {
        val script = AdBlockerScript.buildAutoConsentScript(context)
        assertThat(script).contains("challenges.cloudflare.com")
        assertThat(script).contains("cloudflarecaptcha.com")
        assertThat(script).contains("hcaptcha.com")
        assertThat(script).contains("recaptcha.net")
        assertThat(script).contains("/cdn-cgi/challenge-platform")
        assertThat(script).contains("_cf_chl_opt")
        assertThat(script).contains("challenge-stage")
    }
}
