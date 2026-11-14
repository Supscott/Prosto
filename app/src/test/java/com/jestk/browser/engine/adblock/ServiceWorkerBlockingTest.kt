package com.jestk.browser.engine.adblock

import android.net.Uri
import android.webkit.WebResourceRequest
import androidx.webkit.ServiceWorkerClientCompat
import com.google.common.truth.Truth.assertThat
import com.jestk.browser.engine.AdBlockerEngine
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ServiceWorkerBlockingTest {
    private val installed = mutableListOf<ServiceWorkerClientCompat>()
    private val matcher = FakeContentMatcher(blocked = mapOf("ads.example" to (MatchResult.BLOCK or MatchResult.MATCHED)))

    @Before
    fun setUp() {
        ServiceWorkerBlocking.resetForTest()
        ServiceWorkerBlocking.isSupported = { true }
        ServiceWorkerBlocking.install = { installed += it }
        AdBlockerEngine.engineStore.swap(EngineSet(matcher, FakeContentMatcher(), rulesCount = 1))
    }

    @After
    fun tearDown() {
        ServiceWorkerBlocking.resetForTest()
        AdBlockerEngine.engineStore.swap(null)
    }

    private fun request(url: String, referer: String? = null) = object : WebResourceRequest {
        override fun getUrl(): Uri = Uri.parse(url)
        override fun isForMainFrame() = false
        override fun isRedirect() = false
        override fun hasGesture() = false
        override fun getMethod() = "GET"
        override fun getRequestHeaders(): Map<String, String> = referer?.let { mapOf("Referer" to it) } ?: emptyMap()
    }

    @Test
    fun registersOnceWhenTheFeatureIsSupported() {
        assertThat(ServiceWorkerBlocking.register({ true }, { true })).isTrue()
        assertThat(ServiceWorkerBlocking.register({ true }, { true })).isFalse()

        assertThat(installed).hasSize(1)
    }

    @Test
    fun doesNothingWhenTheFeatureIsMissing() {
        ServiceWorkerBlocking.isSupported = { false }

        assertThat(ServiceWorkerBlocking.register({ true }, { true })).isFalse()
        assertThat(installed).isEmpty()
    }

    @Test
    fun clientRunsThePolicyWithTheRefererAsSource() {
        ServiceWorkerBlocking.register({ true }, { true })
        val client = installed.single()

        val blocked = client.shouldInterceptRequest(request("https://ads.example/x.js", referer = "https://pwa.example/sw.js"))
        val allowed = client.shouldInterceptRequest(request("https://pwa.example/app.js"))

        assertThat(blocked).isNotNull()
        assertThat(allowed).isNull()
        assertThat(matcher.calls.first().sourceUrl).isEqualTo("https://pwa.example/sw.js")
    }

    @Test
    fun clientReadsTheTogglesWhenARequestArrives() {
        var ads = true
        ServiceWorkerBlocking.register({ ads }, { true })
        val client = installed.single()

        ads = false

        assertThat(client.shouldInterceptRequest(request("https://ads.example/x.js"))).isNull()
    }
}
