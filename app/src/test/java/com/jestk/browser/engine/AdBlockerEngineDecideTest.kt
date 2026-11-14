package com.jestk.browser.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.engine.adblock.EngineSet
import com.jestk.browser.engine.adblock.FakeContentMatcher
import com.jestk.browser.engine.adblock.FakeEngineFactory
import com.jestk.browser.engine.adblock.MatchResult
import com.jestk.browser.engine.adblock.NativeBackend
import com.jestk.browser.engine.adblock.RequestType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AdBlockerEngineDecideTest {
    private lateinit var context: Context
    private lateinit var ads: FakeContentMatcher
    private lateinit var privacy: FakeContentMatcher

    private val block = MatchResult.BLOCK

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AdBlockerEngine.resetForTests()
        ads = FakeContentMatcher(
            blocked = mapOf(
                "ads.example" to block,
                "both.example" to block,
                "important.example" to (block or MatchResult.IMPORTANT or MatchResult.EXCEPTION),
                "excepted.example" to (block or MatchResult.EXCEPTION or MatchResult.MATCHED),
                "redirect.example" to (block or MatchResult.REDIRECT),
                "plain.example" to block,
                "cloudflare" to block,
            ),
            redirects = mapOf(
                "redirect.example" to "data:text/javascript;base64,AAAA",
                "plain.example" to "data:text/javascript;base64,BBBB",
            ),
        )
        privacy = FakeContentMatcher(blocked = mapOf("tracker.example" to block, "both.example" to block))
        AdBlockerEngine.engineStore.swap(EngineSet(ads, privacy, 42))
    }

    @After
    fun tearDown() {
        AdBlockerEngine.resetForTests()
    }

    private fun decide(url: String, ads: Boolean = true, trackers: Boolean = true, source: String = "https://site.test/") =
        AdBlockerEngine.decide(url, source, RequestType.SCRIPT, ads, trackers)

    @Test
    fun adsOnly_blocksAdsAndIgnoresTrackers() {
        assertThat(decide("https://ads.example/a.js", trackers = false).block).isTrue()
        assertThat(decide("https://ads.example/a.js", trackers = false).isTracker).isFalse()
        assertThat(decide("https://tracker.example/t.js", trackers = false).block).isFalse()
        assertThat(privacy.calls).isEmpty()
    }

    @Test
    fun trackersOnly_blocksTrackersAndIgnoresAds() {
        val decision = decide("https://tracker.example/t.js", ads = false)

        assertThat(decision.block).isTrue()
        assertThat(decision.isTracker).isTrue()
        assertThat(decide("https://ads.example/a.js", ads = false).block).isFalse()
        assertThat(ads.calls).isEmpty()
    }

    @Test
    fun both_checksPrivacyFirstSoTheMatchIsATracker() {
        val decision = decide("https://both.example/x.js")

        assertThat(decision.block).isTrue()
        assertThat(decision.isTracker).isTrue()
        assertThat(ads.calls).isEmpty()
        assertThat(decide("https://ads.example/a.js").isTracker).isFalse()
    }

    @Test
    fun none_allowsWithoutTouchingTheEngines() {
        val decision = decide("https://both.example/x.js", ads = false, trackers = false)

        assertThat(decision.block).isFalse()
        assertThat(ads.calls).isEmpty()
        assertThat(privacy.calls).isEmpty()
    }

    @Test
    fun important_winsOverAnException() {
        assertThat(decide("https://important.example/x.js").block).isTrue()
    }

    @Test
    fun exception_cancelsAMatchedBlock() {
        assertThat(decide("https://excepted.example/x.js").block).isFalse()
    }

    @Test
    fun redirect_isFetchedOnlyWhenTheResultHasTheFlag() {
        assertThat(decide("https://redirect.example/x.js").redirectDataUrl).isEqualTo("data:text/javascript;base64,AAAA")
        val plain = decide("https://plain.example/x.js")
        assertThat(plain.block).isTrue()
        assertThat(plain.redirectDataUrl).isNull()
    }

    @Test
    fun authAndCaptchaDomains_areAlwaysAllowed() {
        assertThat(decide("https://challenges.cloudflare.com/turnstile/v0/api.js").block).isFalse()
        assertThat(decide("https://www.recaptcha.net/recaptcha/api.js").block).isFalse()
        assertThat(ads.calls).isEmpty()
    }

    @Test
    fun nonHttpOrEmptyUrls_areAllowed() {
        for (url in listOf("", "data:text/plain,ads.example", "ftp://ads.example/a", "about:blank")) {
            assertThat(decide(url).block).isFalse()
        }
        assertThat(ads.calls).isEmpty()
    }

    @Test
    fun theSourceUrlAndTypeReachTheEngine() {
        decide("https://ads.example/a.js", source = "https://news.test/page")

        assertThat(ads.calls.single().sourceUrl).isEqualTo("https://news.test/page")
        assertThat(ads.calls.single().type).isEqualTo(RequestType.SCRIPT)
    }

    @Test
    fun shouldBlockNavigation_usesADocumentCheckAgainstTheAdsEngine() {
        assertThat(AdBlockerEngine.shouldBlockNavigation("https://ads.example/click")).isTrue()
        assertThat(ads.calls.single().type).isEqualTo(RequestType.DOCUMENT)
        assertThat(AdBlockerEngine.shouldBlockNavigation("https://fine.example/")).isFalse()
        assertThat(AdBlockerEngine.shouldBlockNavigation("https://challenges.cloudflare.com/x")).isFalse()
    }

    @Test
    fun isTracker_usesThePrivacyEngineAndNeverFlagsSameSite() {
        assertThat(AdBlockerEngine.isTracker("https://tracker.example/t.js", "https://site.test/")).isTrue()
        assertThat(AdBlockerEngine.isTracker("https://ads.example/a.js", "https://site.test/")).isFalse()
        assertThat(AdBlockerEngine.isTracker("https://tracker.example/t.js", "https://www.tracker.example/")).isFalse()
    }

    @Test
    fun rulesCount_reportsTheCompiledRuleCount() {
        assertThat(AdBlockerEngine.getRulesCount()).isEqualTo(42)
        assertThat(AdBlockerEngine.isNativeReady()).isTrue()
    }

    @Test
    fun notReady_fallsBackToTheHostListWithTheOldAdTrackerSplit() {
        AdBlockerEngine.engineStore.swap(null)

        val ad = decide("https://doubleclick.net/pagead/x")
        val tracker = decide("https://google-analytics.com/analytics.js")

        assertThat(ad.block).isTrue()
        assertThat(ad.isTracker).isFalse()
        assertThat(tracker.block).isTrue()
        assertThat(tracker.isTracker).isTrue()
        assertThat(decide("https://google-analytics.com/analytics.js", trackers = false).block).isFalse()
        assertThat(decide("https://doubleclick.net/pagead/x", ads = false).block).isFalse()
        assertThat(decide("https://example.org/").block).isFalse()
    }

    @Test
    fun loadFailure_keepsTheHostListFallback() = runBlocking {
        AdBlockerEngine.engineStore.swap(null)
        val factory = FakeEngineFactory()
        AdBlockerEngine.backend = NativeBackend(load = { false }, version = { "v1" }, factory = factory)

        AdBlockerEngine.initialize(context)
        AdBlockerEngine.awaitInitialization()

        assertThat(AdBlockerEngine.isReady()).isTrue()
        assertThat(AdBlockerEngine.isNativeReady()).isFalse()
        assertThat(factory.compiles).isEqualTo(0)
        assertThat(decide("https://doubleclick.net/pagead/x").block).isTrue()
    }

    @Test
    fun initialize_publishesTheNativeEnginesWhenTheLibraryLoads() = runBlocking {
        AdBlockerEngine.engineStore.swap(null)
        val factory = FakeEngineFactory()
        AdBlockerEngine.backend = NativeBackend(load = { true }, version = { "v1" }, factory = factory)

        AdBlockerEngine.initialize(context)
        AdBlockerEngine.awaitInitialization()

        assertThat(AdBlockerEngine.isNativeReady()).isTrue()
        assertThat(AdBlockerEngine.getRulesCount()).isGreaterThan(0)
        // The empty fake engines decide now, so the host list no longer blocks doubleclick.net.
        assertThat(decide("https://doubleclick.net/pagead/x").block).isFalse()
    }
}
