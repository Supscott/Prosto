package com.jestk.browser.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AdBlockerEngineTest {
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

    // --- 1. ABP Rule Extraction Tests ---

    @Test
    fun extractDomainFromAbpRule_extractsCleanDomain() {
        assertThat(AdBlockerEngine.extractDomainFromAbpRule("||doubleclick.net^"))
            .isEqualTo("doubleclick.net")
        assertThat(AdBlockerEngine.extractDomainFromAbpRule("||googleadservices.com^"))
            .isEqualTo("googleadservices.com")
        assertThat(AdBlockerEngine.extractDomainFromAbpRule("||analytics-consent-manager.azureedge.net^"))
            .isEqualTo("analytics-consent-manager.azureedge.net")
        assertThat(AdBlockerEngine.extractDomainFromAbpRule("||ccpa.sp-prod.net^"))
            .isEqualTo("ccpa.sp-prod.net")
    }

    @Test
    fun extractDomainFromAbpRule_extractsDomainWithOptions() {
        assertThat(AdBlockerEngine.extractDomainFromAbpRule("||demand.supply^\$script"))
            .isEqualTo("demand.supply")
        assertThat(AdBlockerEngine.extractDomainFromAbpRule("||0i0i0i0.com^\$document"))
            .isEqualTo("0i0i0i0.com")
        assertThat(AdBlockerEngine.extractDomainFromAbpRule("||acconsento.click^\$third-party"))
            .isEqualTo("acconsento.click")
        assertThat(AdBlockerEngine.extractDomainFromAbpRule("||app.livemarketshoppers.com^\$script,third-party"))
            .isEqualTo("app.livemarketshoppers.com")
        assertThat(AdBlockerEngine.extractDomainFromAbpRule("||axept.io^\$third-party,domain=~axeptio.eu"))
            .isEqualTo("axept.io")
    }

    @Test
    fun extractDomainFromAbpRule_caseInsensitive() {
        assertThat(AdBlockerEngine.extractDomainFromAbpRule("||DOUBLECLICK.NET^"))
            .isEqualTo("doubleclick.net")
        assertThat(AdBlockerEngine.extractDomainFromAbpRule("||AdServer.Example.COM^\$script"))
            .isEqualTo("adserver.example.com")
    }

    @Test
    fun extractDomainFromAbpRule_ignoresCommentsAndHeaders() {
        assertThat(AdBlockerEngine.extractDomainFromAbpRule("! Title: EasyList")).isNull()
        assertThat(AdBlockerEngine.extractDomainFromAbpRule("! *** easylist_general_block.txt ***")).isNull()
        assertThat(AdBlockerEngine.extractDomainFromAbpRule("[Adblock Plus 2.0]")).isNull()
    }

    @Test
    fun extractDomainFromAbpRule_ignoresWhitelistAndElementHidingRules() {
        assertThat(AdBlockerEngine.extractDomainFromAbpRule("@@||doubleclick.net^")).isNull()
        assertThat(AdBlockerEngine.extractDomainFromAbpRule("@@||example.com^\$document")).isNull()
        assertThat(AdBlockerEngine.extractDomainFromAbpRule("example.com##.ad-banner")).isNull()
        assertThat(AdBlockerEngine.extractDomainFromAbpRule("example.com#?#.ad-banner")).isNull()
        assertThat(AdBlockerEngine.extractDomainFromAbpRule("##.cookie-banner")).isNull()
        assertThat(AdBlockerEngine.extractDomainFromAbpRule("#?#.cookie-modal")).isNull()
    }

    @Test
    fun extractDomainFromAbpRule_ignoresPathRulesAndWildcards() {
        // Path after caret or slash before caret
        assertThat(AdBlockerEngine.extractDomainFromAbpRule("||dev.to/bb/sidebar_left^")).isNull()
        assertThat(AdBlockerEngine.extractDomainFromAbpRule("||cacheserve.*/promodisplay/")).isNull()
        assertThat(AdBlockerEngine.extractDomainFromAbpRule("||media.max.com/*/dash.mpd^\$domain=cnn.com")).isNull()
        assertThat(AdBlockerEngine.extractDomainFromAbpRule("||domain.com^something/path")).isNull()
        assertThat(AdBlockerEngine.extractDomainFromAbpRule("||util-*.simply-hentai.com^")).isNull()
    }

    @Test
    fun extractDomainFromAbpRule_ignoresInvalidOrMalformedLines() {
        assertThat(AdBlockerEngine.extractDomainFromAbpRule("")).isNull()
        assertThat(AdBlockerEngine.extractDomainFromAbpRule("   ")).isNull()
        assertThat(AdBlockerEngine.extractDomainFromAbpRule("||")).isNull()
        assertThat(AdBlockerEngine.extractDomainFromAbpRule("||^")).isNull()
        assertThat(AdBlockerEngine.extractDomainFromAbpRule("||nodot^")).isNull()
        assertThat(AdBlockerEngine.extractDomainFromAbpRule("||.starts-with-dot.com^")).isNull()
        assertThat(AdBlockerEngine.extractDomainFromAbpRule("||ends-with-dot.com.^")).isNull()
    }

    @Test
    fun extractDomainFromHostsLine_parsesStandardHostsFormats() {
        assertThat(AdBlockerEngine.extractDomainFromHostsLine("127.0.0.1 doubleclick.net"))
            .isEqualTo("doubleclick.net")
        assertThat(AdBlockerEngine.extractDomainFromHostsLine("0.0.0.0 tracker.com"))
            .isEqualTo("tracker.com")
        assertThat(AdBlockerEngine.extractDomainFromHostsLine("adservice.google.com"))
            .isEqualTo("adservice.google.com")
        assertThat(AdBlockerEngine.extractDomainFromHostsLine("# Comment line")).isNull()
        assertThat(AdBlockerEngine.extractDomainFromHostsLine("")).isNull()
    }

    // --- 2. Cold Start Instant Readiness Tests ---

    @Test
    fun coldStart_defaultHostsImmediatelyBlockedWithoutWaitingForAssets() {
        // Engine is freshly reset, assets are NOT initialized yet
        assertThat(AdBlockerEngine.isReady()).isFalse()

        // Default hosts must be blocked immediately
        assertThat(AdBlockerEngine.shouldBlock("https://doubleclick.net")).isTrue()
        assertThat(AdBlockerEngine.shouldBlock("https://googleadservices.com/pagead")).isTrue()
        assertThat(AdBlockerEngine.shouldBlock("https://googlesyndication.com")).isTrue()
        assertThat(AdBlockerEngine.shouldBlock("https://google-analytics.com/analytics.js")).isTrue()
        assertThat(AdBlockerEngine.shouldBlock("https://criteo.com")).isTrue()
        assertThat(AdBlockerEngine.shouldBlock("https://taboola.com")).isTrue()
        assertThat(AdBlockerEngine.shouldBlock("https://outbrain.com")).isTrue()

        // Russian ad networks immediately blocked on cold start
        assertThat(AdBlockerEngine.shouldBlock("https://adfox.ru/banner")).isTrue()
        assertThat(AdBlockerEngine.shouldBlock("https://ad.mail.ru/static/ads-async.js")).isTrue()
        assertThat(AdBlockerEngine.shouldBlock("https://ads.x5media.ru/sdk/sdk.standalone.js")).isTrue()
        assertThat(AdBlockerEngine.shouldBlock("https://yabs.yandex.ru/count")).isTrue()
        assertThat(AdBlockerEngine.shouldBlock("https://mc.yandex.ru/metrika/tag.js")).isTrue()

        // Path-based ad blocking on portal domains
        assertThat(AdBlockerEngine.shouldBlock("https://yandex.ru/ads/system/context.js")).isTrue()
        assertThat(AdBlockerEngine.shouldBlock("https://yandex.ru/ads/system/header-bidding.js")).isTrue()
        // Normal Yandex search not blocked
        assertThat(AdBlockerEngine.shouldBlock("https://yandex.ru/search/?text=recipes")).isFalse()
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
            assertThat(AdBlockerEngine.shouldBlock("https://000491b06a.com/ad.js")).isTrue()
            assertThat(AdBlockerEngine.shouldBlock("https://0019x.com")).isTrue()
            assertThat(AdBlockerEngine.shouldBlock("https://a-ads.com")).isTrue()
            assertThat(AdBlockerEngine.shouldBlock("https://demand.supply/script.js")).isTrue()
            assertThat(AdBlockerEngine.shouldBlock("https://0i0i0i0.com")).isTrue()

            // Verify known EasyListCookie domains
            assertThat(AdBlockerEngine.shouldBlock("https://acconsento.click")).isTrue()
            assertThat(AdBlockerEngine.shouldBlock("https://ccpa.sp-prod.net")).isTrue()
            assertThat(AdBlockerEngine.shouldBlock("https://appconsent.io")).isTrue()
            assertThat(AdBlockerEngine.shouldBlock("https://axept.io")).isTrue()
            assertThat(AdBlockerEngine.shouldBlock("https://bigidcmp.cloud")).isTrue()
        }

    // --- 4. Subdomain Blocking Tests ---

    @Test
    fun shouldBlock_subdomainBlockingWalksHierarchy() =
        runBlocking {
            AdBlockerEngine.initialize(context).join()

            // Direct host match
            assertThat(AdBlockerEngine.shouldBlock("https://doubleclick.net")).isTrue()
            // Single level subdomain
            assertThat(AdBlockerEngine.shouldBlock("https://ad.doubleclick.net")).isTrue()
            // Multi level subdomain
            assertThat(AdBlockerEngine.shouldBlock("https://deeply.nested.sub.doubleclick.net/test.js")).isTrue()
            // Scheme-relative URL
            assertThat(AdBlockerEngine.shouldBlock("//stats.doubleclick.net/pixel")).isTrue()
            // Raw host string without scheme
            assertThat(AdBlockerEngine.shouldBlock("ad.doubleclick.net")).isTrue()
            // Cookie subdomain
            assertThat(AdBlockerEngine.shouldBlock("https://sdk.appconsent.io/embed.js")).isTrue()
        }

    @Test
    fun shouldBlock_subdomainOfLegitimateDomainIsNotBlocked() =
        runBlocking {
            AdBlockerEngine.initialize(context).join()

            // Legitimate domain containing blocked keyword as subdomain or vice versa
            // doubleclick.net is blocked, but doubleclick.net.example.com has parent example.com
            assertThat(AdBlockerEngine.shouldBlock("https://doubleclick.net.example.com")).isFalse()
            assertThat(AdBlockerEngine.shouldBlock("https://notdoubleclick.net")).isFalse()
        }

    // --- 5. Benign URLs Allowed Tests ---

    @Test
    fun shouldBlock_allowsLegitimateSites() =
        runBlocking {
            AdBlockerEngine.initialize(context).join()

            assertThat(AdBlockerEngine.shouldBlock("https://google.com")).isFalse()
            assertThat(AdBlockerEngine.shouldBlock("https://developer.android.com/reference")).isFalse()
            assertThat(AdBlockerEngine.shouldBlock("https://github.com/kotlin/kotlinx.coroutines")).isFalse()
            assertThat(AdBlockerEngine.shouldBlock("https://en.wikipedia.org/wiki/Main_Page")).isFalse()
            assertThat(AdBlockerEngine.shouldBlock("https://jestk.com")).isFalse()
            assertThat(AdBlockerEngine.shouldBlock("https://example.com/article?id=42")).isFalse()
        }

    @Test
    fun shouldBlock_handlesEdgeCasesAndSpecialSchemes() {
        assertThat(AdBlockerEngine.shouldBlock(null)).isFalse()
        assertThat(AdBlockerEngine.shouldBlock("")).isFalse()
        assertThat(AdBlockerEngine.shouldBlock("   ")).isFalse()
        assertThat(AdBlockerEngine.shouldBlock("about:blank")).isFalse()
        assertThat(AdBlockerEngine.shouldBlock("data:image/png;base64,iVBORw0KGgo=")).isFalse()
        assertThat(AdBlockerEngine.shouldBlock("javascript:void(0)")).isFalse()
        assertThat(AdBlockerEngine.shouldBlock("file:///android_asset/test.html")).isFalse()
    }

    // --- 6. Blocked WebResourceResponse Tests ---

    @Test
    fun createBlockedResponse_returnsEmptyTextResponse() {
        val response = AdBlockerEngine.createBlockedResponse()
        assertThat(response.mimeType).isEqualTo("text/plain")
        assertThat(response.encoding).isEqualTo("UTF-8")
        assertThat(response.data.read()).isEqualTo(-1) // Empty stream
    }

    @Test
    fun transparent1x1Png_isValidPngSignature() {
        assertThat(AdBlockerEngine.TRANSPARENT_1X1_PNG.isNotEmpty()).isTrue()
        // PNG magic bytes: 0x89, 'P', 'N', 'G'
        assertThat(AdBlockerEngine.TRANSPARENT_1X1_PNG[0]).isEqualTo(0x89.toByte())
        assertThat(AdBlockerEngine.TRANSPARENT_1X1_PNG[1]).isEqualTo('P'.code.toByte())
        assertThat(AdBlockerEngine.TRANSPARENT_1X1_PNG[2]).isEqualTo('N'.code.toByte())
        assertThat(AdBlockerEngine.TRANSPARENT_1X1_PNG[3]).isEqualTo('G'.code.toByte())
    }

    @Test
    fun createSmartBlockedResponse_returnsTransparentPngForImages() {
        val responseUrl = AdBlockerEngine.createSmartBlockedResponse("https://example.com/banner.png")
        assertThat(responseUrl.mimeType).isEqualTo("image/png")
        assertThat(responseUrl.statusCode).isEqualTo(200)
        val bytes = responseUrl.data.readBytes()
        assertThat(bytes).isEqualTo(AdBlockerEngine.TRANSPARENT_1X1_PNG)

        val responseAccept =
            AdBlockerEngine.createSmartBlockedResponse(
                "https://example.com/ad-image",
                requestHeaders = mapOf("Accept" to "image/avif,image/webp,image/*"),
            )
        assertThat(responseAccept.mimeType).isEqualTo("image/png")
    }

    @Test
    fun createSmartBlockedResponse_returnsJavascriptWithSurrogatesForScripts() {
        // Generic JS
        val genericJs = AdBlockerEngine.createSmartBlockedResponse("https://example.com/ad.js")
        assertThat(genericJs.mimeType).isEqualTo("application/javascript")
        assertThat(genericJs.statusCode).isEqualTo(200)
        val genericBody = String(genericJs.data.readBytes())
        assertThat(genericBody).contains("blocked by Prosto")

        // AdSense
        val adsenseJs = AdBlockerEngine.createSmartBlockedResponse("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js")
        assertThat(adsenseJs.mimeType).isEqualTo("application/javascript")
        val adsenseBody = String(adsenseJs.data.readBytes())
        assertThat(adsenseBody).contains("window.adsbygoogle")

        // GPT
        val gptJs = AdBlockerEngine.createSmartBlockedResponse("https://securepubads.g.doubleclick.net/tag/js/gpt.js")
        assertThat(gptJs.mimeType).isEqualTo("application/javascript")
        val gptBody = String(gptJs.data.readBytes())
        assertThat(gptBody).contains("window.googletag")

        // Yandex context.js / ADFOX
        val yandexJs = AdBlockerEngine.createSmartBlockedResponse("https://yandex.ru/ads/system/context.js")
        assertThat(yandexJs.mimeType).isEqualTo("application/javascript")
        val yandexBody = String(yandexJs.data.readBytes())
        assertThat(yandexBody).contains("window.Ya.adfoxCode")

        // X5 Media
        val x5Js = AdBlockerEngine.createSmartBlockedResponse("https://ads.x5media.ru/sdk/sdk.standalone.js")
        assertThat(x5Js.mimeType).isEqualTo("application/javascript")
        val x5Body = String(x5Js.data.readBytes())
        assertThat(x5Body).contains("window.x5media")
    }

    @Test
    fun createSmartBlockedResponse_returns204NoContentForBeaconsAndAnalytics() {
        val response = AdBlockerEngine.createSmartBlockedResponse("https://google-analytics.com/collect?v=1")
        assertThat(response.statusCode).isEqualTo(204)
        assertThat(response.reasonPhrase).isEqualTo("No Content")
        assertThat(response.data.read()).isEqualTo(-1)
    }

    @Test
    fun createSmartBlockedResponse_returnsEmptyHtmlForFrames() {
        val response = AdBlockerEngine.createSmartBlockedResponse("https://example.com/ad_frame.html")
        assertThat(response.mimeType).isEqualTo("text/html")
        assertThat(response.statusCode).isEqualTo(200)
        val body = String(response.data.readBytes())
        assertThat(body).contains("<!DOCTYPE html>")
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
    fun recordBlockedRequest_reportsTheTrackerFlagThroughTheStatsContract() =
        runBlocking {
            val recorded = mutableListOf<Boolean>()

            AdBlockerEngine.setBlockedRequestStats(
                object : com.jestk.browser.domain.repository.BlockedRequestStats {
                    override suspend fun recordBlockedRequest(isTracker: Boolean) {
                        recorded += isTracker
                    }
                }
            )

            AdBlockerEngine.recordBlockedRequest("https://doubleclick.net/ad.js")
            AdBlockerEngine.recordBlockedRequest("https://google-analytics.com/analytics.js")

            kotlinx.coroutines.delay(100)

            assertThat(recorded).containsExactly(false, true).inOrder()
        }

    @Test
    fun recordBlockedRequest_incrementsRepositoryCounts() =
        runBlocking {
            var adsIncremented = 0L
            var trackersIncremented = 0L

            val fakeRepo =
                object : com.jestk.browser.domain.repository.SettingsRepository {
                    override fun getSettings() = kotlinx.coroutines.flow.emptyFlow<com.jestk.browser.domain.model.BrowserSettings>()

                    override suspend fun getCurrentSettings() =
                        com.jestk.browser.domain.model
                            .BrowserSettings()

                    override suspend fun setThemeMode(mode: com.jestk.browser.domain.model.ThemeMode) {}

                    override suspend fun setSearchEngine(engine: com.jestk.browser.domain.model.SearchEngine) {}

                    override suspend fun setAdBlockerEnabled(enabled: Boolean) {}

                    override fun getBlockedAdsCount() = kotlinx.coroutines.flow.flowOf(adsIncremented)

                    override fun getBlockedTrackersCount() = kotlinx.coroutines.flow.flowOf(trackersIncremented)

                    override suspend fun incrementBlockedAds(count: Long) {
                        adsIncremented += count
                    }

                    override suspend fun incrementBlockedTrackers(count: Long) {
                        trackersIncremented += count
                    }

                    override suspend fun setHiddenSearchPageEngineIds(ids: Set<String>) {}

                    override suspend fun setOpenInAppsEnabled(enabled: Boolean) {}

                    override suspend fun setAutoUpdateEnabled(enabled: Boolean) {}

                    override suspend fun setVibrationEnabled(enabled: Boolean) {}

                    override suspend fun setSkippedUpdateVersion(version: String?) {}
                }

            AdBlockerEngine.setBlockedRequestStats(
                com.jestk.browser.data.repository.SettingsBlockedRequestStats(fakeRepo)
            )

            AdBlockerEngine.recordBlockedRequest("https://doubleclick.net/ad.js")
            AdBlockerEngine.recordBlockedRequest("https://google-analytics.com/analytics.js")

            // Wait brief delay for coroutine launch
            kotlinx.coroutines.delay(100)

            assertThat(adsIncremented).isEqualTo(1L)
            assertThat(trackersIncremented).isEqualTo(1L)
        }

    // --- 8. Subdomain Matching Without Substring Allocations ---

    @Test
    fun shouldBlock_matchesSubdomainsOfBlockedDomains() {
        // A blocked domain itself.
        assertThat(AdBlockerEngine.shouldBlock("https://doubleclick.net")).isTrue()

        // A subdomain of a blocked domain is blocked as well.
        assertThat(AdBlockerEngine.shouldBlock("https://ads.doubleclick.net")).isTrue()
        assertThat(AdBlockerEngine.shouldBlock("https://sub.deep.doubleclick.net")).isTrue()

        // A blocked domain with several labels is reached through its suffixes.
        assertThat(AdBlockerEngine.shouldBlock("https://ads.adfox.yandex.ru")).isTrue()
        assertThat(AdBlockerEngine.shouldBlock("https://deep.sub.my.target.com")).isTrue()

        // Regular domains and their subdomains stay reachable.
        assertThat(AdBlockerEngine.shouldBlock("https://example.com")).isFalse()
        assertThat(AdBlockerEngine.shouldBlock("https://ads.example.com")).isFalse()
        assertThat(AdBlockerEngine.shouldBlock("https://sub.deep.example.com")).isFalse()

        // A host without any dot has no suffix to match.
        assertThat(AdBlockerEngine.shouldBlock("https://localhost")).isFalse()

        // A blocked domain used as a prefix of another domain is not a match.
        assertThat(AdBlockerEngine.shouldBlock("https://doubleclick.net.example.com")).isFalse()
        assertThat(AdBlockerEngine.shouldBlock("https://notdoubleclick.net")).isFalse()
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
            assertThat(AdBlockerEngine.shouldBlock(url)).isEqualTo(referenceShouldBlock(url))
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
                assertThat(AdBlockerEngine.shouldBlock(url)).isEqualTo(referenceShouldBlock(url))
            }
        }

    // --- 9. Engine Teardown ---

    @Test
    fun shutdown_cancelsTheBackgroundScope() {
        // Make sure the engine owns a running scope, whatever a previous teardown did.
        assertThat(AdBlockerEngine.ensureScope()).isTrue()
        assertThat(AdBlockerEngine.isScopeActive()).isTrue()

        AdBlockerEngine.shutdown()
        assertThat(AdBlockerEngine.isScopeActive()).isFalse()

        // The call is idempotent.
        AdBlockerEngine.shutdown()
        assertThat(AdBlockerEngine.isScopeActive()).isFalse()
    }

    @Test
    fun recordBlockedRequest_keepsWorkingAfterShutdown() =
        runBlocking {
            val repo = RecordingSettingsRepository()
            AdBlockerEngine.setBlockedRequestStats(
                com.jestk.browser.data.repository.SettingsBlockedRequestStats(repo)
            )

            AdBlockerEngine.shutdown()
            assertThat(AdBlockerEngine.isScopeActive()).isFalse()

            AdBlockerEngine.recordBlockedRequest("https://doubleclick.net/ad.js")
            kotlinx.coroutines.delay(200)

            // The scope is recreated lazily, so the engine keeps counting after a shutdown.
            assertThat(repo.adsIncremented).isEqualTo(1L)
            assertThat(AdBlockerEngine.isScopeActive()).isTrue()
        }

    /**
     * Reference implementation of the host matching, using the straightforward substring based
     * subdomain walk. It mirrors the behaviour the engine had before the suffix walk was rewritten
     * to work on indices, and is used to prove that both implementations agree.
     */
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

        if (AdBlockerEngine.containsHost(host)) return true

        var currentHost = host
        while (currentHost.contains('.')) {
            val nextDotIndex = currentHost.indexOf('.')
            if (nextDotIndex == -1 || nextDotIndex == currentHost.lastIndex) break
            currentHost = currentHost.substring(nextDotIndex + 1)
            if (AdBlockerEngine.containsHost(currentHost)) return true
        }

        val path = uri.path?.lowercase().orEmpty()
        return AdBlockerEngine.isBlockedPath(host, path)
    }

    private class RecordingSettingsRepository : com.jestk.browser.domain.repository.SettingsRepository {
        var adsIncremented = 0L
        var trackersIncremented = 0L

        override fun getSettings() = kotlinx.coroutines.flow.emptyFlow<com.jestk.browser.domain.model.BrowserSettings>()

        override suspend fun getCurrentSettings() = com.jestk.browser.domain.model.BrowserSettings()

        override suspend fun setThemeMode(mode: com.jestk.browser.domain.model.ThemeMode) {}

        override suspend fun setSearchEngine(engine: com.jestk.browser.domain.model.SearchEngine) {}

        override suspend fun setAdBlockerEnabled(enabled: Boolean) {}

        override fun getBlockedAdsCount() = kotlinx.coroutines.flow.flowOf(adsIncremented)

        override fun getBlockedTrackersCount() = kotlinx.coroutines.flow.flowOf(trackersIncremented)

        override suspend fun incrementBlockedAds(count: Long) {
            adsIncremented += count
        }

        override suspend fun incrementBlockedTrackers(count: Long) {
            trackersIncremented += count
        }

        override suspend fun setHiddenSearchPageEngineIds(ids: Set<String>) {}

        override suspend fun setOpenInAppsEnabled(enabled: Boolean) {}

        override suspend fun setAutoUpdateEnabled(enabled: Boolean) {}

        override suspend fun setVibrationEnabled(enabled: Boolean) {}

        override suspend fun setSkippedUpdateVersion(version: String?) {}
    }
}
