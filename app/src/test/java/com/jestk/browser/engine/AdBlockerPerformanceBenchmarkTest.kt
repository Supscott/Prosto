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
class AdBlockerPerformanceBenchmarkTest {

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

    @Test
    fun benchmarkAdBlockerColdStartAndThroughput() = runBlocking {
        // 1. Measure Cold Start Rule Loading Latency
        val initStart = System.nanoTime()
        AdBlockerEngine.initialize(context)
        AdBlockerEngine.awaitInitialization()
        val initDurationMs = (System.nanoTime() - initStart) / 1_000_000.0
        val rulesCount = AdBlockerEngine.getRulesCount()

        println("==================================================")
        println("[BENCHMARK-RESULT] Cold Start Rule Loading:")
        println("  Duration: ${"%.2f".format(initDurationMs)} ms")
        println("  Rules Ingested: $rulesCount")
        println("==================================================")

        assertThat(AdBlockerEngine.isReady()).isTrue()
        assertThat(rulesCount).isGreaterThan(500)

        // 2. Prepare 10,000 realistic URLs
        val seedUrls = listOf(
            "https://googleads.g.doubleclick.net/pagead/ads?client=ca-pub-1234567890&format=300x250",
            "https://adservice.google.com/adsid/integrator.js?domain=example.com",
            "https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js",
            "https://www.google-analytics.com/analytics.js",
            "https://stats.g.doubleclick.net/dc.js",
            "https://mc.yandex.ru/metrika/tag.js",
            "https://an.yandex.ru/count/W_example_banner_click",
            "https://adfox.yandex.ru/banner/123/code",
            "https://yabs.yandex.ru/resource/ad_block.js",
            "https://criteo.com/delivery/ajs.php?zoneid=123",
            "https://static.criteo.net/js/ld/ld.js",
            "https://cdn.taboola.com/libtrc/unip/1234/tfa.js",
            "https://trc.taboola.com/sg/pub/1/log/3/recommendation",
            "https://outbrain.com/widget/detect",
            "https://widgets.outbrain.com/nanoWidget/3rd/dist/ob-widget.js",
            "https://ib.adnxs.com/seg?add=123&t=2",
            "https://secure.quantserve.com/quant.js",
            "https://sb.scorecardresearch.com/beacon.js",
            "https://counter.yadro.ru/hit?t12.6;r",
            "https://top-fwz1.mail.ru/counter?id=12345;js=13",
            "https://ad.mail.ru/static/ads-async.js",
            "https://ads.vk.com/api/v2/stats/view",
            "https://en.wikipedia.org/wiki/Android_(operating_system)",
            "https://en.wikipedia.org/static/images/icons/wikipedia.png",
            "https://github.com/torvalds/linux/blob/master/README",
            "https://raw.githubusercontent.com/torvalds/linux/master/Makefile",
            "https://developer.android.com/reference/android/webkit/WebView",
            "https://developer.android.com/static/css/devsite-cyan.css",
            "https://news.ycombinator.com/item?id=38192834",
            "https://news.ycombinator.com/hn.js",
            "https://kotlinlang.org/docs/coroutines-overview.html",
            "https://assets.stickpng.com/thumbs/5848309bcef1014c0b5e4a9a.png",
            "https://images.unsplash.com/photo-1579783900882-c0d3dad7b119?w=800",
            "https://fonts.googleapis.com/css2?family=Inter:wght@400;600&display=swap",
            "https://fonts.gstatic.com/s/inter/v13/UcCO3FwrK3iLTeHuS_fvQtMwCp50KnMw2boKoduKmMEVuLyfAZ9hjp-Ek-_EeA.woff2",
            "https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css",
            "https://cdnjs.cloudflare.com/ajax/libs/react/18.2.0/umd/react.production.min.js",
            "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIxMCIgaGVpZ2h0PSIxMCI+PC9zdmc+",
            "javascript:void(0)",
            "about:blank",
            "https://sub.domain.deep.path.example.org/static/js/bundle.chunk.js?v=99",
            "https://hybrid.ai/static/pixel.gif",
            "https://videonow.ru/tag/vn_tag.js",
            "https://viqeo.tv/js/player.js",
            "https://rubiconproject.com/fastlane.js",
            "https://pubmatic.com/tag.js",
            "https://bidvol.com/ads.js"
        )

        val totalUrls = 10_000
        val testUrls = ArrayList<String>(totalUrls)
        for (i in 0 until totalUrls) {
            testUrls.add(seedUrls[i % seedUrls.size])
        }

        // Warm up JIT
        for (i in 0 until 500) {
            AdBlockerEngine.shouldBlock(testUrls[i])
            AdBlockerEngine.isTracker(testUrls[i])
        }

        // 3. Measure 10,000 URL Matching Throughput
        val matchStart = System.nanoTime()
        var blockedCount = 0
        var trackerCount = 0

        for (url in testUrls) {
            if (AdBlockerEngine.shouldBlock(url)) {
                blockedCount++
            }
            if (AdBlockerEngine.isTracker(url)) {
                trackerCount++
            }
        }
        val matchDurationMs = (System.nanoTime() - matchStart) / 1_000_000.0
        val lookupsPerSec = ((totalUrls * 2).toDouble() / (matchDurationMs / 1000.0)).toLong()

        println("==================================================")
        println("[BENCHMARK-RESULT] 10,000 URL Lookup Throughput:")
        println("  Total Evaluations: ${totalUrls * 2} (shouldBlock + isTracker)")
        println("  Duration: ${"%.2f".format(matchDurationMs)} ms")
        println("  Throughput: $lookupsPerSec lookups/sec")
        println("  Blocked Count: $blockedCount / $totalUrls")
        println("  Tracker Count: $trackerCount / $totalUrls")
        println("==================================================")

        assertThat(blockedCount).isGreaterThan(4000)
        assertThat(trackerCount).isGreaterThan(1000)
    }
}
