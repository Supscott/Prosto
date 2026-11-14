package com.jestk.browser.engine.adblock

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PopupGateTest {
    private var now = 0L
    private val adHosts = mutableSetOf("ads.example-ad.com")
    private val revealed = mutableListOf<String>()
    private val dropped = mutableListOf<String?>()

    private val gate = PopupGate(
        clock = { now },
        isAdPopup = { url, _, sameSite -> !sameSite && adHosts.any { url.contains(it) } },
    )

    private fun open(tabId: String = "p1", opener: String? = "https://news.example.org/story") {
        gate.register(tabId, opener, onReveal = { revealed += tabId }, onDrop = { dropped += it })
    }

    @Test
    fun allowedUrlReveals() {
        open()
        assertThat(gate.onUrl("p1", "https://shop.other.com/")).isEqualTo(PopupOutcome.REVEALED)
        assertThat(revealed).containsExactly("p1")
        assertThat(dropped).isEmpty()
    }

    @Test
    fun blockedUrlDrops() {
        open()
        assertThat(gate.onUrl("p1", "https://ads.example-ad.com/click?id=1")).isEqualTo(PopupOutcome.DROPPED)
        assertThat(dropped).containsExactly("https://ads.example-ad.com/click?id=1")
        assertThat(revealed).isEmpty()
    }

    @Test
    fun popupHostListMatchIsDropped() {
        PopupHostList.reset()
        PopupHostList.load("# comment\nbad-popup.example\n".byteInputStream())
        val hostGate = PopupGate(clock = { now }, isAdPopup = { url, _, _ -> PopupHostList.containsHostOf(url) })
        hostGate.register("p1", "https://news.example.org/", onReveal = { revealed += "p1" }, onDrop = { dropped += it })
        assertThat(hostGate.onUrl("p1", "https://sub.bad-popup.example/landing")).isEqualTo(PopupOutcome.DROPPED)
        PopupHostList.reset()
    }

    @Test
    fun aboutBlankThenRealUrlDecidesOnTheRealUrl() {
        open()
        assertThat(gate.onUrl("p1", "about:blank")).isNull()
        assertThat(gate.isPending("p1")).isTrue()
        assertThat(gate.onUrl("p1", "https://ads.example-ad.com/")).isEqualTo(PopupOutcome.DROPPED)
    }

    @Test
    fun timeoutRevealsAPopupWithoutUrl() {
        open()
        now = PopupGate.DEFAULT_TIMEOUT_MS - 1
        gate.sweep()
        assertThat(revealed).isEmpty()
        now = PopupGate.DEFAULT_TIMEOUT_MS
        gate.sweep()
        assertThat(revealed).containsExactly("p1")
        assertThat(gate.isPending("p1")).isFalse()
    }

    @Test
    fun authDomainReveals() {
        open()
        adHosts += "challenges.cloudflare.com"
        assertThat(gate.onUrl("p1", "https://challenges.cloudflare.com/turnstile")).isEqualTo(PopupOutcome.REVEALED)
    }

    @Test
    fun sameSiteReveals() {
        adHosts += "news.example.org"
        open()
        assertThat(gate.onUrl("p1", "https://news.example.org/other")).isEqualTo(PopupOutcome.REVEALED)
    }

    @Test
    fun sameSitePopupIsDroppedOnlyByTheCheckThatAcceptsSameSite() {
        val seen = mutableListOf<Boolean>()
        val ruleGate = PopupGate(
            clock = { now },
            isAdPopup = { url, _, sameSite -> seen += sameSite; sameSite && url.contains("/go.php") },
        )
        ruleGate.register("p1", "https://news.example.org/story", onReveal = {}, onDrop = {})
        assertThat(ruleGate.onUrl("p1", "https://news.example.org/go.php?to=ad")).isEqualTo(PopupOutcome.DROPPED)
        assertThat(seen).containsExactly(true)
    }

    @Test
    fun allowlistedOpenerReveals() {
        gate.allowlist = PopupSiteAllowlist { it == "https://news.example.org/story" }
        open()
        assertThat(gate.onUrl("p1", "https://ads.example-ad.com/")).isEqualTo(PopupOutcome.REVEALED)
    }

    @Test
    fun nonHttpUrlReveals() {
        open()
        assertThat(gate.onUrl("p1", "intent://ads.example-ad.com/#Intent;end")).isEqualTo(PopupOutcome.REVEALED)
    }

    @Test
    fun lateInputsAfterDecisionAreIgnored() {
        open()
        gate.onUrl("p1", "https://ads.example-ad.com/")
        assertThat(gate.onUrl("p1", "https://shop.other.com/")).isNull()
        now = 10_000
        gate.sweep()
        assertThat(dropped).hasSize(1)
        assertThat(revealed).isEmpty()
    }

    @Test
    fun unknownTabIsIgnored() {
        assertThat(gate.onUrl("nope", "https://ads.example-ad.com/")).isNull()
    }

    private fun gateWith(allowlist: SiteAllowlist) = PopupGate(
        clock = { now },
        isAdPopup = { url, _, sameSite -> !sameSite && adHosts.any { url.contains(it) } },
        isSiteAllowlisted = allowlist::isAllowlisted,
    )

    private val persisted = SiteAllowlist().apply {
        update(
            listOf(
                com.jestk.browser.domain.model.SiteAllowlistEntry("news.example.org", "work", 0L),
                com.jestk.browser.domain.model.SiteAllowlistEntry("everywhere.example", null, 0L),
            ),
        )
    }

    @Test
    fun persistedAllowlist_revealsOnlyInItsWorkspace() {
        val g = gateWith(persisted)
        g.register("in-work", "https://news.example.org/story", { revealed += "in-work" }, { dropped += it }, openerWorkspaceId = "work")
        g.register("in-home", "https://news.example.org/story", { revealed += "in-home" }, { dropped += it }, openerWorkspaceId = "home")

        assertThat(g.onUrl("in-work", "https://ads.example-ad.com/")).isEqualTo(PopupOutcome.REVEALED)
        assertThat(g.onUrl("in-home", "https://ads.example-ad.com/")).isEqualTo(PopupOutcome.DROPPED)
    }

    @Test
    fun persistedAllWorkspacesEntry_revealsEverywhere() {
        val g = gateWith(persisted)
        g.register("p", "https://www.everywhere.example/", { revealed += "p" }, { dropped += it }, openerWorkspaceId = "home")

        assertThat(g.onUrl("p", "https://ads.example-ad.com/")).isEqualTo(PopupOutcome.REVEALED)
    }
}
