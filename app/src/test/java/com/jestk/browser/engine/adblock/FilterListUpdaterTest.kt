package com.jestk.browser.engine.adblock

import com.google.common.truth.Truth.assertThat
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FilterListUpdaterTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val server = MockWebServer()
    private lateinit var dir: File
    private var now = 1_000L

    private val list = "[Adblock Plus 2.0]\n! Title: test\n||ads.example^\n##.banner\n"

    @Before
    fun setUp() {
        server.start()
        dir = File(tmp.root, "lists")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun updater(maxBytes: Int = FilterListUpdater.MAX_BYTES, requireHttps: Boolean = false) =
        FilterListUpdater(OkHttpClient(), dir, { now }, maxBytes, requireHttps)

    private fun entry(id: String = "easylist", path: String = "/$id.txt") =
        ListEntry(id, listOf("ads"), ProtectionPreset.STANDARD, server.url(path).toString())

    @Test
    fun ok_storesTheListAndReportsItChanged() {
        server.enqueue(MockResponse().setBody(list).setHeader("ETag", "\"v1\"").setHeader("Last-Modified", "Mon, 01 Jan 2024 00:00:00 GMT"))

        val result = updater().update(listOf(entry()))

        assertThat(result.changed).containsExactly("easylist")
        assertThat(result.failures).isEmpty()
        val stored = updater().read("easylist")!!
        assertThat(stored.text).isEqualTo(list)
        assertThat(stored.meta).isEqualTo(ListMeta("\"v1\"", "Mon, 01 Jan 2024 00:00:00 GMT", 1_000L))
    }

    @Test
    fun notModified_sendsValidatorsAndReportsUnchanged() {
        server.enqueue(MockResponse().setBody(list).setHeader("ETag", "\"v1\"").setHeader("Last-Modified", "Mon, 01 Jan 2024 00:00:00 GMT"))
        updater().update(listOf(entry()))
        now = 2_000L
        server.enqueue(MockResponse().setResponseCode(304))

        val result = updater().update(listOf(entry()))

        server.takeRequest()
        val conditional = server.takeRequest()
        assertThat(conditional.getHeader("If-None-Match")).isEqualTo("\"v1\"")
        assertThat(conditional.getHeader("If-Modified-Since")).isEqualTo("Mon, 01 Jan 2024 00:00:00 GMT")
        assertThat(result.changed).isEmpty()
        assertThat(result.unchanged).containsExactly("easylist")
        assertThat(updater().read("easylist")!!.text).isEqualTo(list)
        assertThat(updater().lastFetchedAt()).isEqualTo(2_000L)
    }

    @Test
    fun sameBodyWithoutValidators_isUnchanged() {
        server.enqueue(MockResponse().setBody(list))
        server.enqueue(MockResponse().setBody(list))
        updater().update(listOf(entry()))

        val result = updater().update(listOf(entry()))

        assertThat(result.changed).isEmpty()
        assertThat(result.unchanged).containsExactly("easylist")
    }

    @Test
    fun htmlBody_isRejectedAndNothingIsStored() {
        server.enqueue(MockResponse().setBody("<!DOCTYPE html><html><body>Captive portal</body></html>"))

        val result = updater().update(listOf(entry()))

        assertThat(result.changed).isEmpty()
        assertThat(result.failures.keys).containsExactly("easylist")
        assertThat(updater().has("easylist")).isFalse()
    }

    @Test
    fun oversizeBody_isRejected() {
        server.enqueue(MockResponse().setBody(list + "||x.example^\n".repeat(200)))

        val result = updater(maxBytes = 512).update(listOf(entry()))

        assertThat(result.failures["easylist"]).contains("too large")
        assertThat(updater().has("easylist")).isFalse()
    }

    @Test
    fun httpUrl_isRejectedWhenHttpsIsRequired() {
        val result = updater(requireHttps = true).update(listOf(entry()))

        assertThat(result.failures["easylist"]).contains("not HTTPS")
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun httpError_isAFailureAndKeepsTheOldFile() {
        server.enqueue(MockResponse().setBody(list))
        updater().update(listOf(entry()))
        server.enqueue(MockResponse().setResponseCode(500))

        val result = updater().update(listOf(entry()), force = true)

        assertThat(result.failures["easylist"]).contains("500")
        assertThat(updater().read("easylist")!!.text).isEqualTo(list)
    }

    @Test
    fun partialFailure_keepsTheOldFilesAndStoresTheGoodOnes() {
        val newer = "$list||more.example^\n"
        server.enqueue(MockResponse().setBody(list))
        server.enqueue(MockResponse().setBody(list))
        updater().update(listOf(entry("a"), entry("b")))
        server.enqueue(MockResponse().setBody(newer))
        server.enqueue(MockResponse().setBody("<html>nope</html>"))

        val result = updater().update(listOf(entry("a"), entry("b")), force = true)

        assertThat(result.changed).containsExactly("a")
        assertThat(result.failures.keys).containsExactly("b")
        assertThat(updater().read("a")!!.text).isEqualTo(newer)
        assertThat(updater().read("b")!!.text).isEqualTo(list)
    }

    @Test
    fun rollback_restoresThePreviousCopyOrDeletesAFirstDownload() {
        server.enqueue(MockResponse().setBody(list))
        updater().update(listOf(entry("a")))
        updater().commit()
        server.enqueue(MockResponse().setBody("$list||more.example^\n"))
        server.enqueue(MockResponse().setBody(list))
        val changed = updater().update(listOf(entry("a"), entry("fresh")), force = true).changed

        updater().rollback(changed)

        assertThat(updater().read("a")!!.text).isEqualTo(list)
        assertThat(updater().has("fresh")).isFalse()
    }

    @Test
    fun commit_dropsThePreviousCopies() {
        server.enqueue(MockResponse().setBody(list))
        server.enqueue(MockResponse().setBody("$list||more.example^\n"))
        updater().update(listOf(entry()))
        updater().update(listOf(entry()), force = true)
        assertThat(dir.listFiles().orEmpty().map { it.name }).contains("easylist.txt.prev")

        updater().commit()

        assertThat(dir.listFiles().orEmpty().map { it.name }.filter { it.endsWith(".prev") }).isEmpty()
    }

    @Test
    fun includesAreResolvedWithinTheSameOrigin() {
        server.enqueue(MockResponse().setBody("! Title: main\n!#include part.txt\n!#if env_firefox\n||ff.example^\n!#endif\n"))
        server.enqueue(MockResponse().setBody("||part.example^\n"))

        updater().update(listOf(entry()))

        assertThat(updater().read("easylist")!!.text).isEqualTo("! Title: main\n||part.example^\n")
        assertThat(server.takeRequest().path).isEqualTo("/easylist.txt")
        assertThat(server.takeRequest().path).isEqualTo("/part.txt")
    }

    @Test
    fun remove_deletesFilesAndMetadata() {
        server.enqueue(MockResponse().setBody(list))
        updater().update(listOf(entry()))

        updater().remove(listOf("easylist"))

        assertThat(dir.listFiles().orEmpty()).isEmpty()
    }
}
