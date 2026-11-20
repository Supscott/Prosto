package com.jestk.browser.engine.update

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.AppUpdate
import com.jestk.browser.util.UpdateCheckStore
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppUpdateManagerRequestTest {
    private lateinit var server: MockWebServer
    private lateinit var manager: AppUpdateManager

    private val now = 1_000_000_000L
    private val body = """{"tag_name":"v0.5.0","html_url":"https://example.com/r","body":"n"}"""

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        manager =
            AppUpdateManager(
                ApplicationProvider.getApplicationContext(),
                store = NoStore,
                baseUrl = server.url("/").toString(),
                webBaseUrl = server.url("/").toString(),
                clock = { now },
            )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun ok_returnsReleaseAndEtag_withoutConditionalHeader() = runBlocking {
        server.enqueue(MockResponse().setBody(body).setHeader("ETag", "\"abc\""))

        val result = manager.fetchLatestRelease()

        assertThat(result).isEqualTo(UpdateFetchResult.Fresh(AppUpdate("0.5.0", "https://example.com/r"), "\"abc\""))
        assertThat(server.takeRequest().getHeader("If-None-Match")).isNull()
    }

    @Test
    fun storedEtag_isSentAndNotModifiedIsReported() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(304))

        val result = manager.fetchLatestRelease(etag = "\"abc\"")

        assertThat(result).isEqualTo(UpdateFetchResult.NotModified)
        assertThat(server.takeRequest().getHeader("If-None-Match")).isEqualTo("\"abc\"")
    }

    @Test
    fun retryAfter_isConvertedToAbsoluteTime() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "120"))

        val result = manager.fetchLatestRelease()

        assertThat(result).isEqualTo(UpdateFetchResult.Failed(429, now + 120_000L))
    }

    @Test
    fun exhaustedRateLimit_usesResetTime() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setHeader("X-RateLimit-Remaining", "0")
                .setHeader("X-RateLimit-Reset", "1000003600"),
        )

        val result = manager.fetchLatestRelease()

        assertThat(result).isEqualTo(UpdateFetchResult.Failed(403, 1_000_003_600_000L))
    }

    @Test
    fun plainForbidden_hasNoRetryTime() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setHeader("X-RateLimit-Remaining", "5"))

        assertThat(manager.fetchLatestRelease()).isEqualTo(UpdateFetchResult.Failed(403, null))
    }

    private object NoStore : UpdateCheckStore {
        override var lastSuccessAt = 0L
        override var nextAllowedAt = 0L
        override var failureCount = 0
        override var etag: String? = null
        override var cachedRelease: AppUpdate? = null
    }

    @Test
    fun tagProbe_readsTheRedirectWithoutFollowingIt() = kotlinx.coroutines.test.runTest {
        server.enqueue(
            okhttp3.mockwebserver.MockResponse()
                .setResponseCode(302)
                .setHeader("Location", "https://github.com/Supscott/Prosto/releases/tag/v0.13.0"),
        )

        assertThat(manager.fetchLatestTagVersion()).isEqualTo("0.13.0")
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("HEAD")
        assertThat(request.path).isEqualTo("/Supscott/Prosto/releases/latest")
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun tagProbe_withoutARedirect_returnsNull() = kotlinx.coroutines.test.runTest {
        server.enqueue(okhttp3.mockwebserver.MockResponse().setResponseCode(200))
        assertThat(manager.fetchLatestTagVersion()).isNull()
    }

    @Test
    fun versionFromReleaseLocation_parsesTheTag() {
        assertThat(versionFromReleaseLocation("https://github.com/o/r/releases/tag/v1.2.3")).isEqualTo("1.2.3")
        assertThat(versionFromReleaseLocation("https://github.com/o/r/releases/tag/v1.2.3?x=1")).isEqualTo("1.2.3")
        assertThat(versionFromReleaseLocation("https://github.com/o/r/releases")).isNull()
        assertThat(versionFromReleaseLocation(null)).isNull()
    }
}
