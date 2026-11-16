package com.jestk.browser.engine.adblock

import com.google.common.truth.Truth.assertThat
import org.junit.runner.RunWith
import org.junit.Test
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BlockPageTest {
    private val text = BlockPageText(title = "Page blocked", body = "Reason", goBack = "Go back", proceed = "Proceed anyway")
    private val back = BlockPageActions.url(BlockPageAction.GO_BACK, token = "tok")
    private val proceed = BlockPageActions.url(BlockPageAction.PROCEED, token = "tok")

    @Test
    fun html_hasNoScriptAndNoExternalResources() {
        val html = BlockPage.html(text, "ads.example", back, proceed).lowercase()

        for (forbidden in listOf("<script", "src=", "url(", "@import", "<link", "<iframe", "<img", "onclick")) {
            assertThat(html).doesNotContain(forbidden)
        }
        // The only links are the two action buttons.
        assertThat(Regex("href=\"([^\"]*)\"").findAll(html).map { it.groupValues[1] }.toList())
            .containsExactly(back, proceed)
        assertThat(html).contains("<title>page blocked${BlockPage.TITLE_MARKER}</title>")
    }

    @Test
    fun html_hasBothButtonsWithTheirLabels() {
        val html = BlockPage.html(text, "ads.example", back, proceed)

        assertThat(html).contains(">Go back</a>")
        assertThat(html).contains(">Proceed anyway</a>")
    }

    @Test
    fun html_escapesTextSoTheHostCannotInjectMarkup() {
        val html = BlockPage.html(text, "<script>alert(1)</script>\"", back, proceed)

        assertThat(html).doesNotContain("<script>")
        assertThat(html).contains("&lt;script&gt;")
    }

    @Test
    fun response_isUncachedHtmlWithItsTokenLinks() {
        val response = BlockPage.response(text, "h.example", token = "tok")

        assertThat(response.mimeType).isEqualTo("text/html")
        assertThat(response.responseHeaders["Cache-Control"]).isEqualTo("no-store")
        val html = response.data.readBytes().toString(Charsets.UTF_8)
        assertThat(html).contains("h.example")
        assertThat(html).contains(proceed)
        assertThat(html).contains(back)
    }

    @Test
    fun titleMarker_isRecognisedAndStrippedForDisplay() {
        val title = "Page blocked${BlockPage.TITLE_MARKER}"

        assertThat(BlockPage.isBlockPageTitle(title)).isTrue()
        assertThat(BlockPage.displayTitle(title)).isEqualTo("Page blocked")
        assertThat(BlockPage.isBlockPageTitle("Page blocked")).isFalse()
        assertThat(BlockPage.isBlockPageTitle(null)).isFalse()
    }
}
