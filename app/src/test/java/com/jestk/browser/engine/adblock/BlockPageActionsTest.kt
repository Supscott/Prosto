package com.jestk.browser.engine.adblock

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BlockPageActionsTest {
    private val token = "0123456789abcdef"

    @Test
    fun parse_acceptsEachActionWithTheRightToken() {
        for (action in BlockPageAction.entries) {
            assertThat(BlockPageActions.parse(BlockPageActions.url(action, token), token)).isEqualTo(action)
        }
        assertThat(BlockPageActions.parse(BlockPageActions.url(BlockPageAction.PROCEED, token) + "/", token))
            .isEqualTo(BlockPageAction.PROCEED)
    }

    @Test
    fun parse_rejectsAForgedOrMissingToken() {
        assertThat(BlockPageActions.parse("prosto-block://proceed/wrong", token)).isNull()
        assertThat(BlockPageActions.parse("prosto-block://proceed/", token)).isNull()
        assertThat(BlockPageActions.parse("prosto-block://proceed", token)).isNull()
        assertThat(BlockPageActions.parse("prosto-block://proceed/${token}x", token)).isNull()
        assertThat(BlockPageActions.parse("prosto-block://proceed/${token.dropLast(1)}", token)).isNull()
        assertThat(BlockPageActions.parse("prosto-block://proceed/$token/extra", token)).isNull()
        assertThat(BlockPageActions.parse("prosto-block://unknown/$token", token)).isNull()
        assertThat(BlockPageActions.parse("https://prosto-block://proceed/$token", token)).isNull()
    }

    @Test
    fun newToken_isRandomPerBlockPage() {
        val first = BlockPageActions.newToken()

        assertThat(first).matches("[0-9a-f]{32}")
        assertThat(BlockPageActions.newToken()).isNotEqualTo(first)
        assertThat(BlockPageActions.parse(BlockPageActions.url(BlockPageAction.PROCEED, first), BlockPageActions.newToken())).isNull()
        assertThat(BlockPageActions.parse(BlockPageActions.url(BlockPageAction.PROCEED, first), null)).isNull()
    }

    @Test
    fun isActionUrl_coversForgedUrlsToo() {
        assertThat(BlockPageActions.isActionUrl("prosto-block://proceed/anything")).isTrue()
        assertThat(BlockPageActions.isActionUrl("PROSTO-BLOCK://x")).isTrue()
        assertThat(BlockPageActions.isActionUrl("https://example.com/prosto-block://")).isFalse()
    }

    private fun request(url: String, mainFrame: Boolean = true) = object : WebResourceRequest {
        override fun getUrl(): Uri = Uri.parse(url)
        override fun isForMainFrame() = mainFrame
        override fun isRedirect() = false
        override fun hasGesture() = true
        override fun getMethod() = "GET"
        override fun getRequestHeaders(): Map<String, String> = emptyMap()
    }

    private fun webView(title: String?, url: String?): WebView {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        return object : WebView(context) {
            override fun getTitle() = title
            override fun getUrl() = url
        }
    }

    private val blockTitle = "Page blocked${BlockPage.TITLE_MARKER}"
    private val validProceed get() = BlockPageActions.url(BlockPageAction.PROCEED, token)

    @Test
    fun resolve_returnsTheActionAndTheBlockedUrlOnABlockPage() {
        val view = webView(blockTitle, "https://ads.example/x")

        assertThat(BlockPageActions.resolve(view, request(validProceed), token))
            .isEqualTo(BlockPageAction.PROCEED to "https://ads.example/x")
    }

    @Test
    fun resolve_ignoresActionsOutsideABlockPageOrAMainFrameOrWithAForgedToken() {
        // A normal page (not showing the block page) cannot trigger an action even with the token.
        assertThat(BlockPageActions.resolve(webView("News", "https://news.example"), request(validProceed), token)).isNull()
        assertThat(BlockPageActions.resolve(null, request(validProceed), token)).isNull()
        assertThat(BlockPageActions.resolve(webView(blockTitle, "https://ads.example/x"), request(validProceed, mainFrame = false), token)).isNull()
        assertThat(
            BlockPageActions.resolve(webView(blockTitle, "https://ads.example/x"), request("prosto-block://proceed/forged"), token),
        ).isNull()
    }
}
