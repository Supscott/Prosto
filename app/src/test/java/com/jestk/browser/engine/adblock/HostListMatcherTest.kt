package com.jestk.browser.engine.adblock

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.jestk.browser.engine.AdBlockerEngine
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HostListMatcherTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AdBlockerEngine.resetForTests()
    }

    @After
    fun tearDown() {
        AdBlockerEngine.resetForTests()
    }

    // --- 2. Cold Start Instant Readiness Tests ---

    @Test
    fun coldStart_defaultHostsImmediatelyBlockedWithoutWaitingForAssets() {
        // Engine is freshly reset, assets are NOT initialized yet
        assertThat(AdBlockerEngine.isReady()).isFalse()

        // Default hosts must be blocked immediately
        assertThat(HostListMatcher.shouldBlock("https://doubleclick.net")).isTrue()
        assertThat(HostListMatcher.shouldBlock("https://googleadservices.com/pagead")).isTrue()
        assertThat(HostListMatcher.shouldBlock("https://googlesyndication.com")).isTrue()
        assertThat(HostListMatcher.shouldBlock("https://google-analytics.com/analytics.js")).isTrue()
        assertThat(HostListMatcher.shouldBlock("https://criteo.com")).isTrue()
        assertThat(HostListMatcher.shouldBlock("https://taboola.com")).isTrue()
        assertThat(HostListMatcher.shouldBlock("https://outbrain.com")).isTrue()

        // Russian ad networks immediately blocked on cold start
        assertThat(HostListMatcher.shouldBlock("https://adfox.ru/banner")).isTrue()
        assertThat(HostListMatcher.shouldBlock("https://ad.mail.ru/static/ads-async.js")).isTrue()
        assertThat(HostListMatcher.shouldBlock("https://ads.x5media.ru/sdk/sdk.standalone.js")).isTrue()
        assertThat(HostListMatcher.shouldBlock("https://yabs.yandex.ru/count")).isTrue()
        assertThat(HostListMatcher.shouldBlock("https://mc.yandex.ru/metrika/tag.js")).isTrue()

        // Path-based ad blocking on portal domains
        assertThat(HostListMatcher.shouldBlock("https://yandex.ru/ads/system/context.js")).isTrue()
        assertThat(HostListMatcher.shouldBlock("https://yandex.ru/ads/system/header-bidding.js")).isTrue()
        // Normal Yandex search not blocked
        assertThat(HostListMatcher.shouldBlock("https://yandex.ru/search/?text=recipes")).isFalse()
    }

    // --- 3. Full Asset Initialization & Rule Blocking Tests ---

    @Test
    fun initialize_asynchronouslyLoadsEasyListAndEasyListCookie() =
        runBlocking {
            assertThat(AdBlockerEngine.isReady()).isFalse()

            val job = AdBlockerEngine.initialize(context)
            job.join()

            assertThat(AdBlockerEngine.isReady()).isTrue()
            assertThat(AdBlockerEngine.getRulesCount()).isGreaterThan(40000)

            // Verify known EasyList ad domains
            assertThat(HostListMatcher.shouldBlock("https://000491b06a.com/ad.js")).isTrue()
            assertThat(HostListMatcher.shouldBlock("https://0019x.com")).isTrue()
            assertThat(HostListMatcher.shouldBlock("https://a-ads.com")).isTrue()
            assertThat(HostListMatcher.shouldBlock("https://demand.supply/script.js")).isTrue()
            assertThat(HostListMatcher.shouldBlock("https://0i0i0i0.com")).isTrue()

            // Verify known EasyListCookie domains
            assertThat(HostListMatcher.shouldBlock("https://acconsento.click")).isTrue()
            assertThat(HostListMatcher.shouldBlock("https://ccpa.sp-prod.net")).isTrue()
            assertThat(HostListMatcher.shouldBlock("https://appconsent.io")).isTrue()
            assertThat(HostListMatcher.shouldBlock("https://axept.io")).isTrue()
            assertThat(HostListMatcher.shouldBlock("https://bigidcmp.cloud")).isTrue()
        }

    // --- 4. Subdomain Blocking Tests ---

    @Test
    fun shouldBlock_subdomainBlockingWalksHierarchy() =
        runBlocking {
            AdBlockerEngine.initialize(context).join()

            // Direct host match
            assertThat(HostListMatcher.shouldBlock("https://doubleclick.net")).isTrue()
            // Single level subdomain
            assertThat(HostListMatcher.shouldBlock("https://ad.doubleclick.net")).isTrue()
            // Multi level subdomain
            assertThat(HostListMatcher.shouldBlock("https://deeply.nested.sub.doubleclick.net/test.js")).isTrue()
            // Scheme-relative URL
            assertThat(HostListMatcher.shouldBlock("//stats.doubleclick.net/pixel")).isTrue()
            // Raw host string without scheme
            assertThat(HostListMatcher.shouldBlock("ad.doubleclick.net")).isTrue()
            // Cookie subdomain
            assertThat(HostListMatcher.shouldBlock("https://sdk.appconsent.io/embed.js")).isTrue()
        }

    @Test
    fun shouldBlock_subdomainOfLegitimateDomainIsNotBlocked() =
        runBlocking {
            AdBlockerEngine.initialize(context).join()

            // Legitimate domain containing blocked keyword as subdomain or vice versa
            // doubleclick.net is blocked, but doubleclick.net.example.com has parent example.com
            assertThat(HostListMatcher.shouldBlock("https://doubleclick.net.example.com")).isFalse()
            assertThat(HostListMatcher.shouldBlock("https://notdoubleclick.net")).isFalse()
        }

    // --- 5. Benign URLs Allowed Tests ---

    @Test
    fun shouldBlock_allowsLegitimateSites() =
        runBlocking {
            AdBlockerEngine.initialize(context).join()

            assertThat(HostListMatcher.shouldBlock("https://google.com")).isFalse()
            assertThat(HostListMatcher.shouldBlock("https://developer.android.com/reference")).isFalse()
            assertThat(HostListMatcher.shouldBlock("https://github.com/kotlin/kotlinx.coroutines")).isFalse()
            assertThat(HostListMatcher.shouldBlock("https://en.wikipedia.org/wiki/Main_Page")).isFalse()
            assertThat(HostListMatcher.shouldBlock("https://jestk.com")).isFalse()
            assertThat(HostListMatcher.shouldBlock("https://example.com/article?id=42")).isFalse()
        }

    @Test
    fun shouldBlock_handlesEdgeCasesAndSpecialSchemes() {
        assertThat(HostListMatcher.shouldBlock(null)).isFalse()
        assertThat(HostListMatcher.shouldBlock("")).isFalse()
        assertThat(HostListMatcher.shouldBlock("   ")).isFalse()
        assertThat(HostListMatcher.shouldBlock("about:blank")).isFalse()
        assertThat(HostListMatcher.shouldBlock("data:image/png;base64,iVBORw0KGgo=")).isFalse()
        assertThat(HostListMatcher.shouldBlock("javascript:void(0)")).isFalse()
        assertThat(HostListMatcher.shouldBlock("file:///android_asset/test.html")).isFalse()
    }

    // --- 6. Blocked WebResourceResponse Tests ---

    @Test
    fun shouldBlock_allowsLegitimateCdnDomains() {
        assertThat(HostListMatcher.shouldBlock("https://r34xyz.b-cdn.net/posts/1349/1349708/1349708.thumbnail.jpg")).isFalse()
        assertThat(HostListMatcher.shouldBlock("https://d111111abcdef8.cloudfront.net/images/hero.webp")).isFalse()
        assertThat(HostListMatcher.shouldBlock("https://my-app.workers.dev/api/data")).isFalse()
    }

    @Test
    fun isTracker_youtubeCorePlaybackTelemetry_returnsFalse() {
        assertThat(AdBlockerEngine.isTracker("https://www.youtube.com/api/stats/playback?docid=123")).isFalse()
        assertThat(AdBlockerEngine.isTracker("https://www.youtube.com/api/stats/watchtime?docid=123")).isFalse()
        assertThat(AdBlockerEngine.isTracker("https://www.youtube.com/api/stats/qoe?docid=123")).isFalse()
        assertThat(AdBlockerEngine.isTracker("https://m.youtube.com/api/stats/playback?docid=123")).isFalse()
    }

    // --- 7. Tracker Detection & Interception Counting Tests ---

    @Test
    fun isTracker_identifiesAnalyticsTrackersAndTelemetry() {
        // Analytics & telemetry domains
        assertThat(AdBlockerEngine.isTracker("https://google-analytics.com/analytics.js")).isTrue()
        assertThat(AdBlockerEngine.isTracker("https://www.google-analytics.com/collect?v=2")).isTrue()
        assertThat(AdBlockerEngine.isTracker("https://scorecardresearch.com/beacon.js")).isTrue()
        assertThat(AdBlockerEngine.isTracker("https://sb.scorecardresearch.com/p")).isTrue()
        assertThat(AdBlockerEngine.isTracker("https://quantserve.com/quant.js")).isTrue()
        assertThat(AdBlockerEngine.isTracker("https://pixel.quantserve.com/pixel/p-test.gif")).isTrue()

        // Yandex Metrika & adfstat
        assertThat(AdBlockerEngine.isTracker("https://mc.yandex.ru/metrika/tag.js")).isTrue()
        assertThat(AdBlockerEngine.isTracker("https://adfstat.yandex.ru/adfstat/banner")).isTrue()
        assertThat(AdBlockerEngine.isTracker("https://metrika.yandex.ru/watch/12345")).isTrue()

        // Mail.ru / Rambler counters & beacons
        assertThat(AdBlockerEngine.isTracker("https://top-fwz1.mail.ru/counter?id=123")).isTrue()
        assertThat(AdBlockerEngine.isTracker("https://counter.yadro.ru/hit?test")).isTrue()

        // Generic beacon/pixel/telemetry URLs
        assertThat(AdBlockerEngine.isTracker("https://example.com/beacon/send")).isTrue()
        assertThat(AdBlockerEngine.isTracker("https://example.com/tracking/pixel.gif")).isTrue()
        assertThat(AdBlockerEngine.isTracker("https://example.com/api/telemetry")).isTrue()
        assertThat(AdBlockerEngine.isTracker("https://example.com/stats/collect")).isTrue()
    }

    @Test
    fun isTracker_returnsFalseForNonTrackerAdsAndRegularUrls() {
        // Pure ads
        assertThat(AdBlockerEngine.isTracker("https://doubleclick.net/ad.js")).isFalse()
        assertThat(AdBlockerEngine.isTracker("https://googleadservices.com/pagead/conversion.js")).isFalse()
        assertThat(AdBlockerEngine.isTracker("https://adfox.ru/banner")).isFalse()

        // Regular URLs
        assertThat(AdBlockerEngine.isTracker("https://google.com/search?q=test")).isFalse()
        assertThat(AdBlockerEngine.isTracker("https://en.wikipedia.org/wiki/Main_Page")).isFalse()
        assertThat(AdBlockerEngine.isTracker("")).isFalse()
        assertThat(AdBlockerEngine.isTracker(null)).isFalse()
    }

    @Test
    fun shouldBlockAndIsTracker_allowlistAuthAndCaptchaEndpoints() {
        val authEndpoints = listOf(
            "https://arkoselabs.com/fc/api.js",
            "https://client-api.arkoselabs.com/fc/gt2/public_key/xxx",
            "https://arkose.com/challenge",
            "https://challenges.cloudflare.com/turnstile/v0/api.js",
            "https://cloudflare.com/cdn-cgi/challenge-platform/h/b/orchestrate",
            "https://ca1.cloudflarecaptcha.com/challenge",
            "https://hcaptcha.com/1/api.js",
            "https://recaptcha.net/recaptcha/api.js"
        )

        for (endpoint in authEndpoints) {
            assertThat(HostListMatcher.shouldBlock(endpoint)).isFalse()
            assertThat(AdBlockerEngine.isTracker(endpoint)).isFalse()
        }
    }

    @Test
    fun shouldBlock_matchesSubdomainsOfBlockedDomains() {
        // A blocked domain itself.
        assertThat(HostListMatcher.shouldBlock("https://doubleclick.net")).isTrue()

        // A subdomain of a blocked domain is blocked as well.
        assertThat(HostListMatcher.shouldBlock("https://ads.doubleclick.net")).isTrue()
        assertThat(HostListMatcher.shouldBlock("https://sub.deep.doubleclick.net")).isTrue()

        // A blocked domain with several labels is reached through its suffixes.
        assertThat(HostListMatcher.shouldBlock("https://ads.adfox.yandex.ru")).isTrue()
        assertThat(HostListMatcher.shouldBlock("https://deep.sub.my.target.com")).isTrue()

        // Regular domains and their subdomains stay reachable.
        assertThat(HostListMatcher.shouldBlock("https://example.com")).isFalse()
        assertThat(HostListMatcher.shouldBlock("https://ads.example.com")).isFalse()
        assertThat(HostListMatcher.shouldBlock("https://sub.deep.example.com")).isFalse()

        // A host without any dot has no suffix to match.
        assertThat(HostListMatcher.shouldBlock("https://localhost")).isFalse()

        // A blocked domain used as a prefix of another domain is not a match.
        assertThat(HostListMatcher.shouldBlock("https://doubleclick.net.example.com")).isFalse()
        assertThat(HostListMatcher.shouldBlock("https://notdoubleclick.net")).isFalse()
    }

    @Test
    fun shouldBlock_matchesTheReferenceSubdomainAlgorithm() {
        // The suffix walk is compared against the straightforward substring based implementation:
        // both must agree on every host, including the malformed ones.
        val hosts =
            listOf(
                "doubleclick.net",
                "ads.doubleclick.net",
                "sub.deep.doubleclick.net",
                "example.com",
                "ads.example.com",
                "sub.deep.example.com",
                "localhost",
                "doubleclick.net.example.com",
                "notdoubleclick.net",
                "adfox.yandex.ru",
                "ads.adfox.yandex.ru",
                "my.target.com",
                "sub.my.target.com",
                "target.my.com",
                "ads.com",
                "com",
                "a..b",
                ".doubleclick.net",
                "doubleclick.net.",
                "ad.doubleclick.net.",
                "192.168.1.1",
            )

        for (host in hosts) {
            val url = "https://$host"
            assertThat(HostListMatcher.shouldBlock(url)).isEqualTo(referenceShouldBlock(url))
        }
    }

    @Test
    fun shouldBlock_matchesTheReferenceSubdomainAlgorithmWithTheFullRuleset() =
        runBlocking {
            AdBlockerEngine.initialize(context).join()

            val hosts =
                listOf(
                    "doubleclick.net",
                    "ads.doubleclick.net",
                    "sub.deep.doubleclick.net",
                    "example.com",
                    "ads.example.com",
                    "localhost",
                    "a..b",
                    ".doubleclick.net",
                    "doubleclick.net.",
                    "acconsento.click",
                    "sdk.appconsent.io",
                    "axept.io",
                    "bigidcmp.cloud",
                )

            for (host in hosts) {
                val url = "https://$host"
                assertThat(HostListMatcher.shouldBlock(url)).isEqualTo(referenceShouldBlock(url))
            }
        }

    // --- 9. Engine Teardown ---

    private fun referenceShouldBlock(url: String): Boolean {
        val trimmedUrl = url.trim()
        val normalizedUrl =
            when {
                trimmedUrl.startsWith("//") -> "https:$trimmedUrl"
                !trimmedUrl.contains("://") -> "https://$trimmedUrl"
                else -> trimmedUrl
            }
        val uri = android.net.Uri.parse(normalizedUrl) ?: return false
        val host = uri.host?.lowercase() ?: return false
        if (host.isEmpty()) return false

        if (HostListMatcher.containsHost(host)) return true

        var currentHost = host
        while (currentHost.contains('.')) {
            val nextDotIndex = currentHost.indexOf('.')
            if (nextDotIndex == -1 || nextDotIndex == currentHost.lastIndex) break
            currentHost = currentHost.substring(nextDotIndex + 1)
            if (HostListMatcher.containsHost(currentHost)) return true
        }

        val path = uri.path?.lowercase().orEmpty()
        return HostListMatcher.isBlockedPath(host, path)
    }

    // --- First-party Context-Aware Request Filtering Tests ---

    @Test
    fun shouldBlock_firstPartyAnalytics_isAllowed() {
        // Yandex Metrika allowed from yandex.ru
        assertThat(HostListMatcher.shouldBlock("https://mc.yandex.ru/metrika/tag.js", "https://yandex.ru")).isFalse()
        assertThat(HostListMatcher.shouldBlock("https://mc.yandex.ru/watch/12345", "https://yandex.ru")).isFalse()
        // Yandex Metrika allowed from sibling subdomains (e.g. mail.yandex.ru, news.yandex.ru)
        assertThat(HostListMatcher.shouldBlock("https://mc.yandex.ru/metrika/tag.js", "https://mail.yandex.ru")).isFalse()
        // First-party internal analytics / stats endpoint
        assertThat(HostListMatcher.shouldBlock("https://yandex.ru/api/stats/session", "https://yandex.ru")).isFalse()
        assertThat(HostListMatcher.shouldBlock("https://vk.com/api/stats", "https://vk.com")).isFalse()
    }

    @Test
    fun shouldBlock_thirdPartyAnalytics_isBlocked() {
        // Yandex Metrika blocked from third-party sites
        assertThat(HostListMatcher.shouldBlock("https://mc.yandex.ru/metrika/tag.js", "https://example.com")).isTrue()
        assertThat(HostListMatcher.shouldBlock("https://mc.yandex.ru/watch/12345", "https://example.com")).isTrue()
        // Google Analytics blocked from third-party sites
        assertThat(HostListMatcher.shouldBlock("https://google-analytics.com/analytics.js", "https://example.com")).isTrue()
        assertThat(HostListMatcher.shouldBlock("https://scorecardresearch.com/beacon.js", "https://example.com")).isTrue()
    }

    @Test
    fun shouldBlock_firstPartyExplicitAdSubdomain_isBlocked() {
        // Explicit ad subdomains are blocked even when same-site
        assertThat(HostListMatcher.shouldBlock("https://an.yandex.ru/page", "https://yandex.ru")).isTrue()
        assertThat(HostListMatcher.shouldBlock("https://ads.vk.com/banner", "https://vk.com")).isTrue()
        assertThat(HostListMatcher.shouldBlock("https://ad.mail.ru/static/ads-async.js", "https://mail.ru")).isTrue()
        assertThat(HostListMatcher.shouldBlock("https://adfox.yandex.ru/banner", "https://yandex.ru")).isTrue()
    }

    @Test
    fun shouldBlock_firstPartyExplicitAdPath_isBlocked() {
        // Explicit ad paths are blocked even on first-party domains
        assertThat(HostListMatcher.shouldBlock("https://yandex.ru/ads/system/context.js", "https://yandex.ru")).isTrue()
        assertThat(HostListMatcher.shouldBlock("https://vk.com/ads/system/test.js", "https://vk.com")).isTrue()
        assertThat(HostListMatcher.shouldBlock("https://example.com/static/ads.js", "https://example.com")).isTrue()
    }

    @Test
    fun shouldBlock_contextFree_retainsStrictBlocking() {
        // When documentUrl is null, fallback to deterministic global evaluation
        assertThat(HostListMatcher.shouldBlock("https://mc.yandex.ru/metrika/tag.js")).isTrue()
        assertThat(HostListMatcher.shouldBlock("https://an.yandex.ru/page")).isTrue()
        assertThat(HostListMatcher.shouldBlock("https://doubleclick.net/ad.js")).isTrue()
        assertThat(HostListMatcher.shouldBlock("https://yandex.ru/search/?text=recipes")).isFalse()
    }

    @Test
    fun isTracker_withDocumentUrl_distinguishesContext() {
        // Same-site: first-party telemetry is not flagged as a cross-site tracker
        assertThat(AdBlockerEngine.isTracker("https://mc.yandex.ru/metrika/tag.js", "https://yandex.ru")).isFalse()
        // Third-party: telemetry is flagged as a tracker
        assertThat(AdBlockerEngine.isTracker("https://mc.yandex.ru/metrika/tag.js", "https://example.com")).isTrue()
        // Context-free: retains default behavior
        assertThat(AdBlockerEngine.isTracker("https://mc.yandex.ru/metrika/tag.js")).isTrue()
    }

    // --- Regression tests for the review findings AB-1 / AB-2 (real shipped asset) ---

    @Test
    fun shippedList_neverBlocksSharedInfrastructureOrLinkServices() =
        runBlocking {
            AdBlockerEngine.initialize(context).join()

            // Hosts that EasyList only blocks on specific sites (positive domain= option).
            assertThat(HostListMatcher.shouldBlock("https://my-bucket.s3.amazonaws.com/photo.jpg", "https://example.com/")).isFalse()
            assertThat(HostListMatcher.shouldBlock("https://i.imgur.com/abc.jpg", "https://reddit.com/")).isFalse()
            assertThat(HostListMatcher.shouldBlock("https://cdn.jwplayer.com/libraries/x.js", "https://news.example/")).isFalse()
            assertThat(HostListMatcher.shouldBlock("https://files.wordpress.com/2024/a.jpg", "https://blog.example/")).isFalse()
            assertThat(HostListMatcher.shouldBlock("https://players.brightcove.net/1/player.js", "https://news.example/")).isFalse()
            assertThat(HostListMatcher.shouldBlock("https://graph.facebook.com/v19.0/me", "https://app.example/")).isFalse()
            // Link shorteners opened by a tap.
            assertThat(HostListMatcher.shouldBlockNavigation("https://t.co/abc")).isFalse()
            assertThat(HostListMatcher.shouldBlockNavigation("https://bit.ly/abc")).isFalse()
        }

    @Test
    fun shouldBlock_neverBlocksASiteOnItself() =
        runBlocking {
            AdBlockerEngine.initialize(context).join()

            // Former label/path heuristics blocked these sites on their own pages.
            assertThat(HostListMatcher.shouldBlock("https://www.target.com/c/electronics", "https://www.target.com/")).isFalse()
            assertThat(HostListMatcher.shouldBlock("https://bs.to/serie/x", "https://bs.to/")).isFalse()
            assertThat(HostListMatcher.shouldBlock("https://avito.ru/ads/123", "https://avito.ru/")).isFalse()
            assertThat(HostListMatcher.shouldBlock("https://example.com/banners/logo.png", "https://example.com/")).isFalse()
            // Visiting an ad network's own site loads its own resources.
            assertThat(HostListMatcher.shouldBlock("https://cdn.taboola.com/site.css", "https://www.taboola.com/")).isFalse()
            // The same host is still blocked when embedded elsewhere.
            assertThat(HostListMatcher.shouldBlock("https://cdn.taboola.com/libtrc/x.js", "https://news.example/")).isTrue()
        }

    @Test
    fun shouldBlock_thirdPartyOnlyHosts_areAllowedFirstParty() {
        HostListMatcher.loadPrecompiledDomainsFromStream("widgets.example\$third-party\n".byteInputStream())

        assertThat(HostListMatcher.shouldBlock("https://widgets.example/w.js", "https://news.test/")).isTrue()
        assertThat(HostListMatcher.shouldBlock("https://cdn.widgets.example/w.js", "https://news.test/")).isTrue()
        assertThat(HostListMatcher.shouldBlock("https://widgets.example/w.js", "https://www.widgets.example/")).isFalse()
        // Navigations only honour hosts blocked everywhere.
        assertThat(HostListMatcher.shouldBlockNavigation("https://widgets.example/")).isFalse()
        assertThat(HostListMatcher.shouldBlockNavigation("https://doubleclick.net/click")).isTrue()
    }
}
