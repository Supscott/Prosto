package com.jestk.browser.engine.adblock

import com.google.common.truth.Truth.assertThat
import com.jestk.browser.engine.AdBlockerEngine
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NetworkBlockPolicyTest {
    private val block = MatchResult.BLOCK or MatchResult.MATCHED
    private val gif = "data:image/gif;base64,R0lGODlhAQABAAAAACw="

    private val ads = FakeContentMatcher(
        blocked = mapOf(
            "ads.example/banner.js" to block,
            "ads.example/pixel.gif" to (block or MatchResult.REDIRECT),
            "doc-blocked.example" to block,
            "challenges.cloudflare.com" to block,
            "/cdn-cgi/challenge-platform" to block,
        ),
        redirects = mapOf("ads.example/pixel.gif" to gif),
    )
    private val privacy = FakeContentMatcher(blocked = mapOf("tracker.example/t.js" to block))

    @Before
    fun setUp() {
        AdBlockerEngine.engineStore.swap(EngineSet(ads, privacy, rulesCount = 2))
    }

    @After
    fun tearDown() {
        AdBlockerEngine.engineStore.swap(null)
    }

    private fun sub(url: String, headers: Map<String, String> = emptyMap()) = NetworkRequest(url, false, headers)

    private fun evaluate(
        request: NetworkRequest,
        tabUrl: String? = "https://news.example/",
        ads: Boolean = true,
        trackers: Boolean = true,
        allowed: (String) -> Boolean = { false },
    ) = NetworkBlockPolicy.evaluate(request, tabUrl, ads, trackers, allowed)

    @Test
    fun adsEngineMatch_blocksAsAd() {
        val verdict = evaluate(sub("https://ads.example/banner.js"))

        assertThat(verdict).isInstanceOf(NetworkVerdict.Block::class.java)
        assertThat((verdict as NetworkVerdict.Block).isTracker).isFalse()
        assertThat(verdict.response.mimeType).contains("javascript")
    }

    @Test
    fun privacyEngineMatch_blocksAsTracker() {
        val verdict = evaluate(sub("https://tracker.example/t.js"))

        assertThat((verdict as NetworkVerdict.Block).isTracker).isTrue()
    }

    @Test
    fun redirectRule_returnsTheRedirectResource() {
        val verdict = evaluate(sub("https://ads.example/pixel.gif"))

        assertThat(verdict).isInstanceOf(NetworkVerdict.Redirect::class.java)
        assertThat((verdict as NetworkVerdict.Redirect).response.mimeType).isEqualTo("image/gif")
    }

    @Test
    fun unmatchedRequest_isAllowed() {
        assertThat(evaluate(sub("https://news.example/app.js"))).isEqualTo(NetworkVerdict.Allow)
    }

    @Test
    fun togglesAreIndependent() {
        assertThat(evaluate(sub("https://ads.example/banner.js"), ads = false)).isEqualTo(NetworkVerdict.Allow)
        assertThat(evaluate(sub("https://tracker.example/t.js"), trackers = false)).isEqualTo(NetworkVerdict.Allow)
        assertThat(evaluate(sub("https://tracker.example/t.js"), ads = false)).isInstanceOf(NetworkVerdict.Block::class.java)
        assertThat(evaluate(sub("https://ads.example/banner.js"), trackers = false)).isInstanceOf(NetworkVerdict.Block::class.java)
        assertThat(evaluate(sub("https://ads.example/banner.js"), ads = false, trackers = false)).isEqualTo(NetworkVerdict.Allow)
    }

    @Test
    fun authDomainAndChallengePath_areNeverBlocked() {
        assertThat(evaluate(sub("https://challenges.cloudflare.com/turnstile/v0/api.js"))).isEqualTo(NetworkVerdict.Allow)
        assertThat(evaluate(sub("https://some-site.example/cdn-cgi/challenge-platform/h/b/orchestrate/jsch/v1"))).isEqualTo(NetworkVerdict.Allow)
        assertThat(evaluate(NetworkRequest("https://challenges.cloudflare.com/", true, emptyMap()))).isEqualTo(NetworkVerdict.Allow)
    }

    @Test
    fun nonHttpSchemes_areAllowed() {
        assertThat(evaluate(sub("data:text/plain,ads.example/banner.js"))).isEqualTo(NetworkVerdict.Allow)
        assertThat(evaluate(sub("blob:https://ads.example/banner.js"))).isEqualTo(NetworkVerdict.Allow)
        assertThat(evaluate(sub("wss://ads.example/banner.js"))).isEqualTo(NetworkVerdict.Allow)
    }

    @Test
    fun sourceUrl_comesFromRefererThenTabUrl_andIsNeverTheTabForServiceWorkers() {
        evaluate(sub("https://news.example/a.js", mapOf("Referer" to "https://ref.example/page")))
        evaluate(sub("https://news.example/b.js"))
        evaluate(sub("https://news.example/c.js"), tabUrl = null)

        assertThat(ads.calls.map { it.sourceUrl }).containsExactly(
            "https://ref.example/page",
            "https://news.example/",
            "https://news.example/c.js",
        ).inOrder()
    }

    @Test
    fun mainFrame_blocksOnlyOnDocumentMatch() {
        val hit = evaluate(NetworkRequest("https://doc-blocked.example/landing", true, emptyMap()))
        val miss = evaluate(NetworkRequest("https://news.example/", true, emptyMap()))

        assertThat(hit).isEqualTo(NetworkVerdict.BlockDocument("https://doc-blocked.example/landing"))
        assertThat(miss).isEqualTo(NetworkVerdict.Allow)
        assertThat(ads.calls.first().type).isEqualTo(RequestType.DOCUMENT)
    }

    @Test
    fun mainFrame_proceedAnywayHostIsAllowed() {
        val verdict = evaluate(
            NetworkRequest("https://doc-blocked.example/landing", true, emptyMap()),
            allowed = { it.contains("doc-blocked.example") },
        )

        assertThat(verdict).isEqualTo(NetworkVerdict.Allow)
    }

    @Test
    fun mainFrame_adsToggleOffAllows_andTrackersToggleAloneDoesNotBlockDocuments() {
        val request = NetworkRequest("https://doc-blocked.example/landing", true, emptyMap())

        assertThat(evaluate(request, ads = false)).isEqualTo(NetworkVerdict.Allow)
    }

    @Test
    fun mainFrame_hostListFallbackNeverBlocksTheDocument() {
        AdBlockerEngine.engineStore.swap(null)

        val verdict = evaluate(NetworkRequest("https://doubleclick.net/", true, emptyMap()))

        assertThat(verdict).isEqualTo(NetworkVerdict.Allow)
    }

    @Test
    fun hostListFallback_stillBlocksSubResources() {
        AdBlockerEngine.engineStore.swap(null)

        val verdict = evaluate(sub("https://doubleclick.net/ad.js"))

        assertThat(verdict).isInstanceOf(NetworkVerdict.Block::class.java)
    }

    private val onNewsSite = SiteAllowlist().apply {
        update(listOf(com.jestk.browser.domain.model.SiteAllowlistEntry("news.example", null, 0L)))
    }

    private fun evaluateWithAllowlist(request: NetworkRequest, tabUrl: String?) = NetworkBlockPolicy.evaluate(
        request,
        tabUrl,
        adsEnabled = true,
        trackersEnabled = true,
        isSiteAllowlisted = { onNewsSite.isAllowlisted(it, null) },
    )

    @Test
    fun allowlistedSite_loadsAdsAndTrackers() {
        assertThat(evaluateWithAllowlist(sub("https://ads.example/banner.js"), "https://www.news.example/story")).isEqualTo(NetworkVerdict.Allow)
        assertThat(evaluateWithAllowlist(sub("https://tracker.example/t.js"), "https://news.example/")).isEqualTo(NetworkVerdict.Allow)
        assertThat(ads.calls).isEmpty()
    }

    @Test
    fun otherSite_isStillBlocked() {
        assertThat(evaluateWithAllowlist(sub("https://ads.example/banner.js"), "https://other.example/"))
            .isInstanceOf(NetworkVerdict.Block::class.java)
    }

    @Test
    fun allowlistedMainFrame_isNeverBlockedAsDocument() {
        val doc = NetworkRequest("https://doc-blocked.example/", true, emptyMap())
        val allowed = NetworkBlockPolicy.evaluate(doc, null, true, true, isSiteAllowlisted = { true })

        assertThat(allowed).isEqualTo(NetworkVerdict.Allow)
        assertThat(evaluate(doc)).isInstanceOf(NetworkVerdict.BlockDocument::class.java)
    }

    @Test
    fun serviceWorkerRequest_usesTheRefererSite() {
        val fromNews = sub("https://ads.example/banner.js", mapOf("Referer" to "https://news.example/story"))
        val fromOther = sub("https://ads.example/banner.js", mapOf("Referer" to "https://other.example/story"))

        assertThat(evaluateWithAllowlist(fromNews, tabUrl = null)).isEqualTo(NetworkVerdict.Allow)
        assertThat(evaluateWithAllowlist(fromOther, tabUrl = null)).isInstanceOf(NetworkVerdict.Block::class.java)
    }
}
