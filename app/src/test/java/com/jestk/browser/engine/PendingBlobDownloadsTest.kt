package com.jestk.browser.engine

import android.os.Looper
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class PendingBlobDownloadsTest {
    private val webView = WebView(ApplicationProvider.getApplicationContext())
    private val blobUrl = "blob:https://viewer.example/0b1c"

    @Test
    fun noFrameAnswers_dropsTheRequestAndReportsATimeout() {
        val pending = PendingBlobDownloads(timeoutMs = 1_000)
        var timeouts = 0
        pending.request(webView, blobUrl, null, "text/plain") { timeouts++ }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(999))
        assertThat(timeouts).isEqualTo(0)

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2))

        assertThat(timeouts).isEqualTo(1)
        assertThat(pending.isPending(blobUrl)).isFalse()
    }

    @Test
    fun deliveredDownload_doesNotTimeOut() {
        val pending = PendingBlobDownloads(timeoutMs = 1_000)
        var delivered: String? = null
        var timeouts = 0
        val bridge = pending.createBridge { dataUrl, _, _ -> delivered = dataUrl }
        pending.request(webView, blobUrl, null, "text/plain") { timeouts++ }

        bridge.onBlobDownloaded(blobUrl, "data:text/plain;base64,aGk=", "", "text/plain")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))

        assertThat(delivered).isEqualTo("data:text/plain;base64,aGk=")
        assertThat(timeouts).isEqualTo(0)
    }

    @Test
    fun bridge_ignoresBlobUrlsTheDownloadListenerDidNotReport() {
        val pending = PendingBlobDownloads()
        var delivered = false
        val bridge = pending.createBridge { _, _, _ -> delivered = true }

        bridge.onBlobDownloaded(blobUrl, "data:text/plain;base64,aGk=", "", "text/plain")

        assertThat(delivered).isFalse()
    }
}
