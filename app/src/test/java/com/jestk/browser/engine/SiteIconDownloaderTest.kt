package com.jestk.browser.engine

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Before
import org.junit.Test

class SiteIconDownloaderTest {
    private val server = MockWebServer()
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)
    private val ico = byteArrayOf(0, 0, 1, 0, 1, 0, 16, 16)
    private val client = SiteIconDownloader.defaultClient
    private val anyHostIsPublic: (String) -> Boolean = { false }

    @Before
    fun setUp() {
        SiteIconDownloader.clearFailures()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun serve(routes: Map<String, MockResponse>) {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    routes[request.path] ?: MockResponse().setResponseCode(404).setBody("<html>not found</html>")
            }
        server.start()
    }

    private fun origin() = "http://${server.hostName}:${server.port}"

    private fun body(bytes: ByteArray, type: String) =
        MockResponse().setHeader("Content-Type", type).setBody(Buffer().write(bytes))

    @Test
    fun touchIconWins_andTheFaviconIsNotRequested() = runTest {
        serve(mapOf("/apple-touch-icon.png" to body(png, "image/png"), "/favicon.ico" to body(ico, "image/x-icon")))

        val icon = SiteIconDownloader.fetch(origin(), client)

        assertThat(icon?.mimeType).isEqualTo("image/png")
        assertThat(server.requestCount).isEqualTo(1)
        assertThat(server.takeRequest().path).isEqualTo("/apple-touch-icon.png")
    }

    @Test
    fun missingTouchIcon_fallsBackToFavicon_evenWhenServedAsOctetStream() = runTest {
        serve(mapOf("/favicon.ico" to body(ico, "application/octet-stream")))

        val icon = SiteIconDownloader.fetch(origin(), client)

        assertThat(icon?.mimeType).isEqualTo("image/x-icon")
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test
    fun faviconRedirectedToAnHtmlPage_isRejected() = runTest {
        serve(
            mapOf(
                "/favicon.ico" to MockResponse().setResponseCode(302).setHeader("Location", "/login"),
                "/login" to body("<html>login</html>".toByteArray(), "text/html"),
            ),
        )

        assertThat(SiteIconDownloader.fetch(origin(), client, isPrivateHost = anyHostIsPublic)).isNull()
    }

    @Test
    fun redirectToAnotherPublicHost_isFollowed() = runTest {
        // The server answers on "localhost" for the final hop; the origin uses the numeric address.
        serve(emptyMap())
        val target = "http://localhost:${server.port}/real.png"
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    when (request.path) {
                        "/apple-touch-icon.png" -> MockResponse().setResponseCode(301).setHeader("Location", target)
                        "/real.png" -> body(png, "image/png")
                        else -> MockResponse().setResponseCode(404)
                    }
            }

        val icon = SiteIconDownloader.fetch("http://127.0.0.1:${server.port}", client) { false }

        assertThat(icon?.mimeType).isEqualTo("image/png")
    }

    @Test
    fun redirectToAPrivateHost_isRefusedWithoutAnyRequestToIt() = runTest {
        serve(emptyMap())
        val target = "http://127.0.0.1:${server.port}/secret.png"
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    if (request.path == "/apple-touch-icon.png") {
                        MockResponse().setResponseCode(302).setHeader("Location", target)
                    } else {
                        MockResponse().setResponseCode(404)
                    }
            }

        // "localhost" is the public site here; 127.0.0.1 is the private target.
        val icon = SiteIconDownloader.fetch("http://localhost:${server.port}", client) { it == "127.0.0.1" }

        assertThat(icon).isNull()
        val paths = generateSequence { server.takeRequest(10, java.util.concurrent.TimeUnit.MILLISECONDS) }.map { it.path }.toList()
        assertThat(paths).doesNotContain("/secret.png")
    }

    @Test
    fun moreThanThreeRedirects_areRefused() = runTest {
        serve(emptyMap())
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    when (request.path) {
                        "/apple-touch-icon.png" -> MockResponse().setResponseCode(302).setHeader("Location", "/r1")
                        "/r1" -> MockResponse().setResponseCode(302).setHeader("Location", "/r2")
                        "/r2" -> MockResponse().setResponseCode(302).setHeader("Location", "/r3")
                        "/r3" -> MockResponse().setResponseCode(302).setHeader("Location", "/r4")
                        "/r4" -> body(png, "image/png")
                        else -> MockResponse().setResponseCode(404)
                    }
            }

        assertThat(SiteIconDownloader.fetch(origin(), client, isPrivateHost = anyHostIsPublic)).isNull()
    }

    @Test
    fun threeRedirects_areStillFollowed() = runTest {
        serve(emptyMap())
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    when (request.path) {
                        "/apple-touch-icon.png" -> MockResponse().setResponseCode(302).setHeader("Location", "/r1")
                        "/r1" -> MockResponse().setResponseCode(302).setHeader("Location", "/r2")
                        "/r2" -> MockResponse().setResponseCode(302).setHeader("Location", "/r3")
                        "/r3" -> body(png, "image/png")
                        else -> MockResponse().setResponseCode(404)
                    }
            }

        assertThat(SiteIconDownloader.fetch(origin(), client, isPrivateHost = anyHostIsPublic)).isNotNull()
    }

    @Test
    fun redirectAllowed_refusesHttpsToHttpDowngradeAndPrivateTargets() {
        val https = "https://example.com/a".toHttpUrl()
        val http = "http://example.com/a".toHttpUrl()
        val isPrivate = SiteIcon::isPrivateHost

        assertThat(SiteIconDownloader.redirectAllowed(https, "https://cdn.example.net/i.png".toHttpUrl(), isPrivate)).isTrue()
        assertThat(SiteIconDownloader.redirectAllowed(http, "https://example.com/b".toHttpUrl(), isPrivate)).isTrue()
        assertThat(SiteIconDownloader.redirectAllowed(http, "http://example.net/b".toHttpUrl(), isPrivate)).isTrue()
        assertThat(SiteIconDownloader.redirectAllowed(https, "http://example.com/b".toHttpUrl(), isPrivate)).isFalse()
        assertThat(SiteIconDownloader.redirectAllowed(https, "https://192.168.1.1/b".toHttpUrl(), isPrivate)).isFalse()
        assertThat(SiteIconDownloader.redirectAllowed(https, "https://localhost/b".toHttpUrl(), isPrivate)).isFalse()
        assertThat(SiteIconDownloader.redirectAllowed(https, "https://[::1]/b".toHttpUrl(), isPrivate)).isFalse()
    }

    @Test
    fun theDefaultClient_followsNoRedirectsItselfAndHasACallTimeout() {
        assertThat(client.followRedirects).isFalse()
        assertThat(client.followSslRedirects).isFalse()
        assertThat(client.callTimeoutMillis).isEqualTo(10_000)
    }

    @Test
    fun concurrentLoadsOfOneOrigin_shareOneCall() = runTest {
        serve(mapOf("/apple-touch-icon.png" to body(png, "image/png").setBodyDelay(300, java.util.concurrent.TimeUnit.MILLISECONDS)))

        val results =
            listOf(
                async(Dispatchers.Default) { SiteIconDownloader.fetch(origin(), client) },
                async(Dispatchers.Default) { SiteIconDownloader.fetch(origin(), client) },
                async(Dispatchers.Default) { SiteIconDownloader.fetch(origin(), client) },
            ).awaitAll()

        assertThat(results.all { it != null }).isTrue()
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun diskCache_servesTheIconWithoutARequest_untilTheHitTtlPasses() = runTest {
        val cache = SiteIconDiskCache(java.nio.file.Files.createTempDirectory("siteicons").toFile())
        serve(mapOf("/apple-touch-icon.png" to body(png, "image/png")))
        var now = 1_000_000L

        assertThat(SiteIconDownloader.fetch(origin(), client, { now }, cache)).isNotNull()
        assertThat(server.requestCount).isEqualTo(1)

        now += SiteIconDiskCache.HIT_TTL_MS - 60_000
        assertThat(SiteIconDownloader.fetch(origin(), client, { now }, cache)?.mimeType).isEqualTo("image/png")
        assertThat(server.requestCount).isEqualTo(1)

        now += 120_000
        assertThat(SiteIconDownloader.fetch(origin(), client, { now }, cache)).isNotNull()
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test
    fun diskCache_remembersAMissAcrossProcesses_untilTheMissTtlPasses() = runTest {
        val dir = java.nio.file.Files.createTempDirectory("siteicons").toFile()
        serve(emptyMap())
        var now = 1_000_000L

        assertThat(SiteIconDownloader.fetch(origin(), client, { now }, SiteIconDiskCache(dir))).isNull()
        val afterFirst = server.requestCount
        assertThat(afterFirst).isEqualTo(2)

        // A cold start: the in-memory failure map is empty, a new cache object reads the same directory.
        SiteIconDownloader.clearFailures()
        now += SiteIconDiskCache.MISS_TTL_MS - 60_000
        assertThat(SiteIconDownloader.fetch(origin(), client, { now }, SiteIconDiskCache(dir))).isNull()
        assertThat(server.requestCount).isEqualTo(afterFirst)

        SiteIconDownloader.clearFailures()
        now += 120_000
        assertThat(SiteIconDownloader.fetch(origin(), client, { now }, SiteIconDiskCache(dir))).isNull()
        assertThat(server.requestCount).isGreaterThan(afterFirst)
    }

    @Test
    fun anHtmlBodyServedAsImagePng_isRejectedBySniffing() = runTest {
        serve(mapOf("/apple-touch-icon.png" to body("<html></html>".toByteArray(), "image/png")))

        assertThat(SiteIconDownloader.fetch(origin(), client)).isNull()
    }

    @Test
    fun noContentResponse_isRejected() = runTest {
        serve(mapOf("/apple-touch-icon.png" to MockResponse().setResponseCode(204)))

        assertThat(SiteIconDownloader.fetch(origin(), client)).isNull()
    }

    @Test
    fun requestsCarryNoCookiesOrReferer() = runTest {
        serve(mapOf("/apple-touch-icon.png" to body(png, "image/png")))

        SiteIconDownloader.fetch(origin(), client)

        val request = server.takeRequest()
        assertThat(request.getHeader("Cookie")).isNull()
        assertThat(request.getHeader("Referer")).isNull()
    }

    @Test
    fun aFailedOrigin_isNotAskedAgainUntilTheTtlPasses() = runTest {
        serve(emptyMap())
        var now = 1_000L

        assertThat(SiteIconDownloader.fetch(origin(), client, now = { now })).isNull()
        val after = server.requestCount
        assertThat(SiteIconDownloader.fetch(origin(), client, now = { now })).isNull()
        assertThat(server.requestCount).isEqualTo(after)

        now += SiteIconDiskCache.MISS_TTL_MS + 60_000
        assertThat(SiteIconDownloader.fetch(origin(), client, now = { now })).isNull()
        assertThat(server.requestCount).isGreaterThan(after)
    }

    @Test
    fun theInMemoryFailureMap_isBounded() = runTest {
        serve(emptyMap())
        // 300 distinct origins fail; the oldest are forgotten, so the first one is asked again.
        val first = origin()
        SiteIconDownloader.fetch(first, client, now = { 1_000L })
        val afterFirst = server.requestCount
        val offline = client.newBuilder()
            .dns(
                object : okhttp3.Dns {
                    override fun lookup(hostname: String): List<java.net.InetAddress> =
                        throw java.net.UnknownHostException("offline")
                },
            )
            .build()
        for (i in 1..300) {
            SiteIconDownloader.fetch("http://site$i.example", offline, now = { 1_000L })
        }

        SiteIconDownloader.fetch(first, client, now = { 1_000L })
        assertThat(server.requestCount).isGreaterThan(afterFirst)
    }

    @Test
    fun sniffImageMime_knowsTheRasterFormats() {
        assertThat(SiteIconDownloader.sniffImageMime(png)).isEqualTo("image/png")
        assertThat(SiteIconDownloader.sniffImageMime(ico)).isEqualTo("image/x-icon")
        assertThat(SiteIconDownloader.sniffImageMime(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))).isEqualTo("image/jpeg")
        assertThat(SiteIconDownloader.sniffImageMime("GIF89a".toByteArray())).isEqualTo("image/gif")
        assertThat(SiteIconDownloader.sniffImageMime("RIFF....WEBPVP8 ".toByteArray())).isEqualTo("image/webp")
        assertThat(SiteIconDownloader.sniffImageMime("<svg/>".toByteArray())).isNull()
        assertThat(SiteIconDownloader.sniffImageMime(ByteArray(0))).isNull()
    }
}
