package com.jestk.browser.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.channels.Channel
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

    // --- 2. Cold Start Instant Readiness Tests ---

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

        // DoubleClick / YouTube ad_status probe
        val adStatusJs = AdBlockerEngine.createSmartBlockedResponse("https://static.doubleclick.net/instream/ad_status.js")
        assertThat(adStatusJs.mimeType).isEqualTo("application/javascript")
        val adStatusBody = String(adStatusJs.data.readBytes())
        assertThat(adStatusBody).isEqualTo("window.google_ad_status = 1;")
    }

    @Test
    fun createSmartBlockedResponse_returns204NoContentForBeaconsAndAnalytics() {
        val response = AdBlockerEngine.createSmartBlockedResponse("https://google-analytics.com/collect?v=1")
        assertThat(response.statusCode).isEqualTo(204)
        assertThat(response.reasonPhrase).isEqualTo("No Content")
        assertThat(response.data.read()).isEqualTo(-1)
    }

    @Test
    fun createSmartBlockedResponse_returns204NoContentForMediaStreams() {
        val mp4Resp = AdBlockerEngine.createSmartBlockedResponse("https://example.com/ad_video.mp4")
        assertThat(mp4Resp.statusCode).isEqualTo(204)
        assertThat(mp4Resp.reasonPhrase).isEqualTo("No Content")
        assertThat(mp4Resp.mimeType).isEqualTo("video/mp4")
        assertThat(mp4Resp.data.read()).isEqualTo(-1)

        val webmResp = AdBlockerEngine.createSmartBlockedResponse(
            "https://example.com/stream",
            mimeTypeHint = "video/webm",
        )
        assertThat(webmResp.statusCode).isEqualTo(204)
        assertThat(webmResp.mimeType).isEqualTo("video/mp4")

        val m3u8Resp = AdBlockerEngine.createSmartBlockedResponse("https://example.com/ad_chunk.m3u8?token=123")
        assertThat(m3u8Resp.statusCode).isEqualTo(204)
    }

    @Test
    fun createSmartBlockedResponse_returnsEmptyHtmlForFrames() {
        val response = AdBlockerEngine.createSmartBlockedResponse("https://example.com/ad_frame.html")
        assertThat(response.mimeType).isEqualTo("text/html")
        assertThat(response.statusCode).isEqualTo(200)
        val body = String(response.data.readBytes())
        assertThat(body).contains("<!DOCTYPE html>")
    }

    @Test
    fun createSmartBlockedResponse_returnsEmptyJsonObjectForJsonRequests() {
        // Known ad JSON endpoints
        val pageadResp = AdBlockerEngine.createSmartBlockedResponse("https://googleads.g.doubleclick.net/pagead/id")
        assertThat(pageadResp.mimeType).isEqualTo("application/json")
        assertThat(pageadResp.statusCode).isEqualTo(200)
        assertThat(String(pageadResp.data.readBytes())).isEqualTo("{}")
        assertThat(pageadResp.responseHeaders["Access-Control-Allow-Origin"]).isEqualTo("https://m.youtube.com")
        assertThat(pageadResp.responseHeaders["Access-Control-Allow-Credentials"]).isEqualTo("true")

        val adBreakResp = AdBlockerEngine.createSmartBlockedResponse("https://youtube.com/youtubei/v1/player/ad_break")
        assertThat(adBreakResp.mimeType).isEqualTo("application/json")
        assertThat(adBreakResp.statusCode).isEqualTo(200)
        assertThat(String(adBreakResp.data.readBytes())).isEqualTo("{}")
        assertThat(adBreakResp.responseHeaders["Access-Control-Allow-Origin"]).isEqualTo("https://m.youtube.com")
        assertThat(adBreakResp.responseHeaders["Access-Control-Allow-Credentials"]).isEqualTo("true")

        // Accept: application/json header with custom Origin
        val acceptJsonResp = AdBlockerEngine.createSmartBlockedResponse(
            "https://example.com/ad-data",
            requestHeaders = mapOf(
                "Accept" to "application/json, text/plain, */*",
                "Origin" to "https://sub.example.com"
            )
        )
        assertThat(acceptJsonResp.mimeType).isEqualTo("application/json")
        assertThat(acceptJsonResp.statusCode).isEqualTo(200)
        assertThat(String(acceptJsonResp.data.readBytes())).isEqualTo("{}")
        assertThat(acceptJsonResp.responseHeaders["Access-Control-Allow-Origin"]).isEqualTo("https://sub.example.com")
        assertThat(acceptJsonResp.responseHeaders["Access-Control-Allow-Credentials"]).isEqualTo("true")
    }

    @Test
    fun recordBlockedRequest_reportsTheTrackerFlagThroughTheStatsContract() =
        runBlocking {
            val recorded = Channel<Boolean>(Channel.UNLIMITED)

            AdBlockerEngine.setBlockedRequestStats(
                object : com.jestk.browser.domain.repository.BlockedRequestStats {
                    override suspend fun recordBlockedRequest(isTracker: Boolean) {
                        recorded.send(isTracker)
                    }
                }
            )

            AdBlockerEngine.recordBlockedRequest("https://doubleclick.net/ad.js")
            AdBlockerEngine.recordBlockedRequest("https://google-analytics.com/analytics.js")

            val results = withTimeout(5_000) { listOf(recorded.receive(), recorded.receive()) }
            assertThat(results).containsExactly(false, true)
            Unit
        }

    @Test
    fun recordBlockedRequest_incrementsRepositoryCounts() =
        runBlocking {
            val adsIncremented = java.util.concurrent.atomic.AtomicLong()
            val trackersIncremented = java.util.concurrent.atomic.AtomicLong()
            val incremented = Channel<Unit>(Channel.UNLIMITED)

            val fakeRepo =
                object : com.jestk.browser.domain.repository.SettingsRepository {
                    override fun getSettings() = kotlinx.coroutines.flow.emptyFlow<com.jestk.browser.domain.model.BrowserSettings>()

                    override suspend fun getCurrentSettings() =
                        com.jestk.browser.domain.model
                            .BrowserSettings()

                    override suspend fun setThemeMode(mode: com.jestk.browser.domain.model.ThemeMode) {}

                    override suspend fun setSearchEngine(engine: com.jestk.browser.domain.model.SearchEngine) {}

                    override suspend fun setAdBlockerEnabled(enabled: Boolean) {}

                    override suspend fun setAdRedirectProtectionEnabled(enabled: Boolean) {}

                    override suspend fun setTrackingProtectionEnabled(enabled: Boolean) {}

                    override fun getBlockedAdsCount() = kotlinx.coroutines.flow.flowOf(adsIncremented.get())

                    override fun getBlockedTrackersCount() = kotlinx.coroutines.flow.flowOf(trackersIncremented.get())

                    override suspend fun incrementBlockedAds(count: Long) {
                        adsIncremented.addAndGet(count)
                        incremented.send(Unit)
                    }

                    override suspend fun incrementBlockedTrackers(count: Long) {
                        trackersIncremented.addAndGet(count)
                        incremented.send(Unit)
                    }

                    override suspend fun setHiddenSearchPageEngineIds(ids: Set<String>) {}

                    override suspend fun setOpenInAppsEnabled(enabled: Boolean) {}

                    override suspend fun setAutoUpdateEnabled(enabled: Boolean) {}

                    override suspend fun setVibrationEnabled(enabled: Boolean) {}

                    override suspend fun setGrayUnloadedTabsEnabled(enabled: Boolean) {}

                    override suspend fun setSkippedUpdateVersion(version: String?) {}
                }

            AdBlockerEngine.setBlockedRequestStats(
                com.jestk.browser.data.repository.SettingsBlockedRequestStats(fakeRepo)
            )

            AdBlockerEngine.recordBlockedRequest("https://doubleclick.net/ad.js")
            AdBlockerEngine.recordBlockedRequest("https://google-analytics.com/analytics.js")

            withTimeout(5_000) { repeat(2) { incremented.receive() } }

            assertThat(adsIncremented.get()).isEqualTo(1L)
            assertThat(trackersIncremented.get()).isEqualTo(1L)
        }

    // --- 8. Subdomain Matching Without Substring Allocations ---

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
    private class RecordingSettingsRepository : com.jestk.browser.domain.repository.SettingsRepository {
        var adsIncremented = 0L
        var trackersIncremented = 0L

        override fun getSettings() = kotlinx.coroutines.flow.emptyFlow<com.jestk.browser.domain.model.BrowserSettings>()

        override suspend fun getCurrentSettings() = com.jestk.browser.domain.model.BrowserSettings()

        override suspend fun setThemeMode(mode: com.jestk.browser.domain.model.ThemeMode) {}

        override suspend fun setSearchEngine(engine: com.jestk.browser.domain.model.SearchEngine) {}

        override suspend fun setAdBlockerEnabled(enabled: Boolean) {}

        override suspend fun setAdRedirectProtectionEnabled(enabled: Boolean) {}

        override suspend fun setTrackingProtectionEnabled(enabled: Boolean) {}

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

        override suspend fun setGrayUnloadedTabsEnabled(enabled: Boolean) {}

        override suspend fun setSkippedUpdateVersion(version: String?) {}
    }
}
