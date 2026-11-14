package com.jestk.browser.engine.adblock

import com.google.common.truth.Truth.assertThat
import org.junit.runner.RunWith
import org.junit.Test
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BlockPageTest {
    @Test
    fun html_hasNoScriptAndNoExternalResources() {
        val html = BlockPage.html("Page blocked", "Reason", "ads.example").lowercase()

        for (forbidden in listOf("<script", "src=", "href=", "url(", "@import", "<link", "<iframe", "<img", "onclick")) {
            assertThat(html).doesNotContain(forbidden)
        }
        assertThat(html).contains("<title>page blocked</title>")
    }

    @Test
    fun html_escapesTextSoTheHostCannotInjectMarkup() {
        val html = BlockPage.html("T", "B", "<script>alert(1)</script>\"")

        assertThat(html).doesNotContain("<script>")
        assertThat(html).contains("&lt;script&gt;")
    }

    @Test
    fun response_isUncachedHtml() {
        val response = BlockPage.response("T", "B", "h.example")

        assertThat(response.mimeType).isEqualTo("text/html")
        assertThat(response.responseHeaders["Cache-Control"]).isEqualTo("no-store")
        assertThat(response.data.readBytes().toString(Charsets.UTF_8)).contains("h.example")
    }
}
