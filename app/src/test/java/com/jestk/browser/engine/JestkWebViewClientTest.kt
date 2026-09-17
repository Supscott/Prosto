package com.jestk.browser.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class JestkWebViewClientTest {

    @Test
    fun onPageStarted_ignoresEmptyAndAboutBlank() {
        var startedUrl: String? = null
        val client = JestkWebViewClient(
            tabId = "tab_1",
            isAdBlockerEnabled = { false },
            onPageStarted = { _, url -> startedUrl = url },
            onPageFinished = { _, _, _ -> },
            onNavigationStateChanged = { _, _, _ -> },
            onSnapshotReady = { _, _ -> }
        )

        client.onPageStarted(null, "", null)
        assertThat(startedUrl).isNull()

        client.onPageStarted(null, "about:blank", null)
        assertThat(startedUrl).isNull()

        client.onPageStarted(null, "https://example.com", null)
        assertThat(startedUrl).isEqualTo("https://example.com")
    }

    @Test
    fun onPageFinished_ignoresEmptyAndAboutBlank() {
        var finishedUrl: String? = null
        val client = JestkWebViewClient(
            tabId = "tab_1",
            isAdBlockerEnabled = { false },
            onPageStarted = { _, _ -> },
            onPageFinished = { _, url, _ -> finishedUrl = url },
            onNavigationStateChanged = { _, _, _ -> },
            onSnapshotReady = { _, _ -> }
        )

        client.onPageFinished(null, "")
        assertThat(finishedUrl).isNull()

        client.onPageFinished(null, "about:blank")
        assertThat(finishedUrl).isNull()

        client.onPageFinished(null, "https://example.com")
        assertThat(finishedUrl).isEqualTo("https://example.com")
    }
}
