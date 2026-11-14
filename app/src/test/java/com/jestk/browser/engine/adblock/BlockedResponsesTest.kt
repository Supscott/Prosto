package com.jestk.browser.engine.adblock

import android.webkit.WebResourceResponse
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
class BlockedResponsesTest {
    private fun dataUrl(mime: String, body: ByteArray) =
        "data:$mime;base64," + Base64.getEncoder().encodeToString(body)

    private fun bodyOf(response: WebResourceResponse) = response.data.readBytes()

    @Test
    fun fromDataUrl_decodesJavascript() {
        val response = BlockedResponses.fromDataUrl(dataUrl("application/javascript", "(function(){})();".toByteArray()))

        assertThat(response.mimeType).isEqualTo("application/javascript")
        assertThat(response.encoding).isEqualTo("UTF-8")
        assertThat(response.statusCode).isEqualTo(200)
        assertThat(String(bodyOf(response))).isEqualTo("(function(){})();")
    }

    @Test
    fun fromDataUrl_decodesGifBytesWithoutEncoding() {
        val gif = byteArrayOf(0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x01, 0x00)
        val response = BlockedResponses.fromDataUrl(dataUrl("image/gif", gif))

        assertThat(response.mimeType).isEqualTo("image/gif")
        assertThat(response.encoding).isNull()
        assertThat(bodyOf(response)).isEqualTo(gif)
    }

    @Test
    fun fromDataUrl_decodesHtmlAndText() {
        assertThat(BlockedResponses.fromDataUrl(dataUrl("text/html", "<p></p>".toByteArray())).mimeType)
            .isEqualTo("text/html")
        val text = BlockedResponses.fromDataUrl(dataUrl("text/plain", "ok".toByteArray()))
        assertThat(String(bodyOf(text))).isEqualTo("ok")
    }

    @Test
    fun fromDataUrl_usesTheSameCorsHeadersAsTheSmartResponse() {
        val headers = mapOf("Origin" to "https://news.example")
        val response = BlockedResponses.fromDataUrl(dataUrl("text/plain", "x".toByteArray()), headers)

        assertThat(response.responseHeaders["Access-Control-Allow-Origin"]).isEqualTo("https://news.example")
        assertThat(response.responseHeaders["Access-Control-Allow-Credentials"]).isEqualTo("true")
        assertThat(response.responseHeaders["Access-Control-Allow-Headers"]).isEqualTo("*")
    }

    @Test
    fun fromDataUrl_badDataUrlYieldsTheEmptyResponse() {
        val bad = listOf("", "https://example.com/a.js", "data:text/plain", "data:text/plain,hello", "data:text/plain;base64,!!!")
        for (url in bad) {
            val response = BlockedResponses.fromDataUrl(url)
            assertThat(response.mimeType).isEqualTo("text/plain")
            assertThat(bodyOf(response)).isEmpty()
        }
    }
}
