package com.jestk.browser.engine

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
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
    private val client = OkHttpClient()

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

        assertThat(SiteIconDownloader.fetch(origin(), client)).isNull()
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

        assertThat(SiteIconDownloader.fetch(origin(), client) { now }).isNull()
        val after = server.requestCount
        assertThat(SiteIconDownloader.fetch(origin(), client) { now }).isNull()
        assertThat(server.requestCount).isEqualTo(after)

        now += 6 * 60 * 1000L
        assertThat(SiteIconDownloader.fetch(origin(), client) { now }).isNull()
        assertThat(server.requestCount).isGreaterThan(after)
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
