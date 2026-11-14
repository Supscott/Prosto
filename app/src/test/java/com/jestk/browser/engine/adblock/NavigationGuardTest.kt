package com.jestk.browser.engine.adblock

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Redirect-protection cases ported from `JestkWebViewClientTest`, plus the popunder rule. */
@RunWith(RobolectricTestRunner::class)
class NavigationGuardTest {
    private var now = 0L
    private val detector = PopunderDetector(clock = { now })
    private val listed = mutableSetOf<String>()
    private var allowlist = PopupSiteAllowlist.NONE

    private val guard = NavigationGuard(
        popunderDetector = detector,
        popupAllowlist = { allowlist },
        isListedAdUrl = { it in listed },
    )

    private val on = GuardSettings(adBlockActive = true, redirectProtectionActive = true)

    private fun nav(
        url: String,
        current: String? = "https://news.example/article",
        gesture: Boolean = false,
        redirect: Boolean = false,
        tab: String = "t1",
    ) = GuardedNavigation(tab, url, current, gesture, redirect)

    @Test
    fun listedUrlIsBlockedEvenWithAGesture() {
        listed += "https://ads.example/click"
        assertThat(guard.evaluate(nav("https://ads.example/click", gesture = true), on)).isEqualTo(GuardVerdict.BLOCK_LISTED)
    }

    @Test
    fun nothingIsBlockedWhenBothProtectionsAreOff() {
        listed += "https://ads.example/click"
        val off = GuardSettings(adBlockActive = false, redirectProtectionActive = false)
        assertThat(guard.evaluate(nav("https://ads.example/click"), off)).isEqualTo(GuardVerdict.ALLOW)
    }

    @Test
    fun listIsIgnoredWhenOnlyRedirectProtectionIsOn() {
        listed += "https://ads.example/click"
        val redirectOnly = GuardSettings(adBlockActive = false, redirectProtectionActive = true)
        assertThat(guard.evaluate(nav("https://ads.example/click", gesture = true), redirectOnly)).isEqualTo(GuardVerdict.ALLOW)
    }

    @Test
    fun scriptDrivenCrossSiteJumpWithoutGestureIsBlocked() {
        assertThat(guard.evaluate(nav("https://casino.test/"), on)).isEqualTo(GuardVerdict.BLOCK_REDIRECT)
    }

    @Test
    fun redirectProtectionOffAllowsTheJump() {
        val adsOnly = GuardSettings(adBlockActive = true, redirectProtectionActive = false)
        assertThat(guard.evaluate(nav("https://casino.test/"), adsOnly)).isEqualTo(GuardVerdict.ALLOW)
    }

    @Test
    fun serverRedirectsAreAllowed() {
        assertThat(guard.evaluate(nav("https://bank-acs.test/3ds", current = "https://shop.example/checkout", redirect = true), on))
            .isEqualTo(GuardVerdict.ALLOW)
    }

    @Test
    fun leavingASignInPageIsAllowed() {
        assertThat(
            guard.evaluate(nav("https://app.example/callback?code=1", current = "https://accounts.google.com/signin/oauth/consent"), on),
        ).isEqualTo(GuardVerdict.ALLOW)
    }

    @Test
    fun navigationsWithAGestureAreAllowed() {
        assertThat(guard.evaluate(nav("https://other.example/", gesture = true), on)).isEqualTo(GuardVerdict.ALLOW)
    }

    @Test
    fun sameSiteSearchEngineAndBlankPageAreAllowed() {
        assertThat(guard.evaluate(nav("https://news.example/next"), on)).isEqualTo(GuardVerdict.ALLOW)
        assertThat(guard.evaluate(nav("https://shop.example/", current = "https://www.google.com/search?q=x"), on))
            .isEqualTo(GuardVerdict.ALLOW)
        assertThat(guard.evaluate(nav("https://shop.example/", current = "about:blank"), on)).isEqualTo(GuardVerdict.ALLOW)
        assertThat(guard.evaluate(nav("https://shop.example/", current = null), on)).isEqualTo(GuardVerdict.ALLOW)
    }

    @Test
    fun staticAssetsAndNonWebSchemesAreAllowed() {
        assertThat(guard.evaluate(nav("https://cdn.other.example/pic.png"), on)).isEqualTo(GuardVerdict.ALLOW)
        assertThat(guard.evaluate(nav("tg://resolve?domain=x"), on)).isEqualTo(GuardVerdict.ALLOW)
    }

    @Test
    fun popunderIsBlockedInsideTheThresholdAndReportedAsSuch() {
        detector.onPopupOpened("t1")
        now += 300
        assertThat(guard.evaluate(nav("https://casino.test/"), on)).isEqualTo(GuardVerdict.BLOCK_POPUNDER)
    }

    @Test
    fun popunderRuleWorksWithRedirectProtectionOff() {
        detector.onPopupOpened("t1")
        now += 300
        val adsOnly = GuardSettings(adBlockActive = true, redirectProtectionActive = false)
        assertThat(guard.evaluate(nav("https://casino.test/"), adsOnly)).isEqualTo(GuardVerdict.BLOCK_POPUNDER)
    }

    @Test
    fun jumpAfterTheThresholdFallsBackToTheRedirectHeuristic() {
        detector.onPopupOpened("t1")
        now += PopunderDetector.DEFAULT_THRESHOLD_MS + 1
        assertThat(guard.evaluate(nav("https://casino.test/"), on)).isEqualTo(GuardVerdict.BLOCK_REDIRECT)
    }

    @Test
    fun popunderIgnoresGesturesSignInAndOtherTabs() {
        detector.onPopupOpened("t1")
        now += 300
        assertThat(guard.evaluate(nav("https://other.example/", gesture = true), on)).isEqualTo(GuardVerdict.ALLOW)
        assertThat(guard.evaluate(nav("https://accounts.google.com/o/oauth2/auth"), on)).isEqualTo(GuardVerdict.ALLOW)
        assertThat(guard.evaluate(nav("https://casino.test/", tab = "t2"), on)).isEqualTo(GuardVerdict.BLOCK_REDIRECT)
    }

    @Test
    fun allowedOpenerSiteSkipsThePopunderRule() {
        allowlist = PopupSiteAllowlist { it?.contains("news.example") == true }
        detector.onPopupOpened("t1")
        now += 300
        val adsOnly = GuardSettings(adBlockActive = true, redirectProtectionActive = false)
        assertThat(guard.evaluate(nav("https://casino.test/"), adsOnly)).isEqualTo(GuardVerdict.ALLOW)
    }

    @Test
    fun isLoginPage_onlyMatchesSignInPagesOfMultiPurposeHosts() {
        assertThat(NavigationGuard.isLoginPage("https://github.com/login/oauth/authorize?client_id=x")).isTrue()
        assertThat(NavigationGuard.isLoginPage("https://github.com/developer/repo")).isFalse()
        assertThat(NavigationGuard.isLoginPage("https://appleid.apple.com/auth/authorize")).isTrue()
        assertThat(NavigationGuard.isLoginPage("https://news.example/login")).isFalse()
    }

    private val persisted = SiteAllowlist().apply {
        update(listOf(com.jestk.browser.domain.model.SiteAllowlistEntry("news.example", "work", 0L)))
    }
    private val persistedGuard = NavigationGuard(
        popunderDetector = detector,
        isListedAdUrl = { it in listed },
        isSiteAllowlisted = persisted::isAllowlisted,
    )

    private fun navIn(workspace: String?, url: String, gesture: Boolean = false) =
        GuardedNavigation("t1", url, "https://news.example/article", gesture, false, workspace)

    @Test
    fun allowlistedSite_skipsTheAdListAndPopunderRule() {
        listed += "https://ads.example/click"
        assertThat(persistedGuard.evaluate(navIn("work", "https://ads.example/click", gesture = true), on)).isEqualTo(GuardVerdict.ALLOW)
        assertThat(persistedGuard.evaluate(navIn("home", "https://ads.example/click", gesture = true), on)).isEqualTo(GuardVerdict.BLOCK_LISTED)

        detector.onPopupOpened("t1")
        now += 300
        val adsOnly = GuardSettings(adBlockActive = true, redirectProtectionActive = false)
        assertThat(persistedGuard.evaluate(navIn("work", "https://casino.test/"), adsOnly)).isEqualTo(GuardVerdict.ALLOW)
        assertThat(persistedGuard.evaluate(navIn("home", "https://casino.test/"), adsOnly)).isEqualTo(GuardVerdict.BLOCK_POPUNDER)
    }

    @Test
    fun allowlistedSite_keepsRedirectProtection() {
        assertThat(persistedGuard.evaluate(navIn("work", "https://casino.test/"), on)).isEqualTo(GuardVerdict.BLOCK_REDIRECT)
    }
}
