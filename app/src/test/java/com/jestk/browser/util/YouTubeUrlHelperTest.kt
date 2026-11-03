package com.jestk.browser.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class YouTubeUrlHelperTest {

    @Test
    fun extractVideoId_standardWatchUrls() {
        assertThat(YouTubeUrlHelper.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
            .isEqualTo("dQw4w9WgXcQ")
        assertThat(YouTubeUrlHelper.extractVideoId("https://m.youtube.com/watch?v=dQw4w9WgXcQ&t=42s"))
            .isEqualTo("dQw4w9WgXcQ")
        assertThat(YouTubeUrlHelper.extractVideoId("https://youtube.com/watch?v=dQw4w9WgXcQ"))
            .isEqualTo("dQw4w9WgXcQ")
    }

    @Test
    fun extractVideoId_shortAndShortsUrls() {
        assertThat(YouTubeUrlHelper.extractVideoId("https://youtu.be/dQw4w9WgXcQ"))
            .isEqualTo("dQw4w9WgXcQ")
        assertThat(YouTubeUrlHelper.extractVideoId("https://youtu.be/dQw4w9WgXcQ?t=15"))
            .isEqualTo("dQw4w9WgXcQ")
        assertThat(YouTubeUrlHelper.extractVideoId("https://www.youtube.com/shorts/dQw4w9WgXcQ"))
            .isEqualTo("dQw4w9WgXcQ")
        assertThat(YouTubeUrlHelper.extractVideoId("https://m.youtube.com/shorts/dQw4w9WgXcQ?feature=share"))
            .isEqualTo("dQw4w9WgXcQ")
    }

    @Test
    fun extractVideoId_embedUrls() {
        assertThat(YouTubeUrlHelper.extractVideoId("https://www.youtube.com/embed/dQw4w9WgXcQ"))
            .isEqualTo("dQw4w9WgXcQ")
        assertThat(YouTubeUrlHelper.extractVideoId("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ"))
            .isEqualTo("dQw4w9WgXcQ")
    }

    @Test
    fun extractVideoId_nonVideoUrls_returnsNull() {
        assertThat(YouTubeUrlHelper.extractVideoId(null)).isNull()
        assertThat(YouTubeUrlHelper.extractVideoId("")).isNull()
        assertThat(YouTubeUrlHelper.extractVideoId("https://m.youtube.com/")).isNull()
        assertThat(YouTubeUrlHelper.extractVideoId("https://m.youtube.com/channel/UC12345678901")).isNull()
        assertThat(YouTubeUrlHelper.extractVideoId("https://m.youtube.com/@SomeCreator")).isNull()
        assertThat(YouTubeUrlHelper.extractVideoId("https://m.youtube.com/feed/subscriptions")).isNull()
        assertThat(YouTubeUrlHelper.extractVideoId("https://google.com/search?q=youtube")).isNull()
        assertThat(YouTubeUrlHelper.extractVideoId("https://example.com/watch?v=dQw4w9WgXcQ")).isNull()
    }

    @Test
    fun extractStartTimeSeconds_parsesVariousFormats() {
        assertThat(YouTubeUrlHelper.extractStartTimeSeconds("https://m.youtube.com/watch?v=dQw4w9WgXcQ&t=90"))
            .isEqualTo(90)
        assertThat(YouTubeUrlHelper.extractStartTimeSeconds("https://m.youtube.com/watch?v=dQw4w9WgXcQ&t=90s"))
            .isEqualTo(90)
        assertThat(YouTubeUrlHelper.extractStartTimeSeconds("https://youtu.be/dQw4w9WgXcQ?t=1m30s"))
            .isEqualTo(90)
        assertThat(YouTubeUrlHelper.extractStartTimeSeconds("https://youtu.be/dQw4w9WgXcQ?t=1h2m3s"))
            .isEqualTo(3723)
        assertThat(YouTubeUrlHelper.extractStartTimeSeconds("https://youtu.be/dQw4w9WgXcQ?start=45"))
            .isEqualTo(45)
        assertThat(YouTubeUrlHelper.extractStartTimeSeconds("https://youtu.be/dQw4w9WgXcQ"))
            .isEqualTo(0)
    }

    @Test
    fun buildCleanEmbedUrl_generatesPrivacyEnhancedUrl() {
        assertThat(YouTubeUrlHelper.buildCleanEmbedUrl("dQw4w9WgXcQ"))
            .isEqualTo("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ?autoplay=1&playsinline=1&rel=0&modestbranding=1")

        assertThat(YouTubeUrlHelper.buildCleanEmbedUrl("dQw4w9WgXcQ", 90))
            .isEqualTo("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ?autoplay=1&playsinline=1&rel=0&modestbranding=1&start=90")
    }

    @Test
    fun buildEmbedHtml_containsStrictOriginReferrerAndIframe() {
        val html = YouTubeUrlHelper.buildEmbedHtml("dQw4w9WgXcQ", 45)
        assertThat(html).contains("referrerpolicy=\"strict-origin-when-cross-origin\"")
        assertThat(html).contains("name=\"referrer\" content=\"strict-origin-when-cross-origin\"")
        assertThat(html).contains("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ")
        assertThat(html).contains("&start=45")
        assertThat(html).contains("CleanPlayerBridge")
    }

    @Test
    fun buildOriginalWatchUrl_generatesMobileWatchUrl() {
        assertThat(YouTubeUrlHelper.buildOriginalWatchUrl("dQw4w9WgXcQ"))
            .isEqualTo("https://m.youtube.com/watch?v=dQw4w9WgXcQ")

        assertThat(YouTubeUrlHelper.buildOriginalWatchUrl("dQw4w9WgXcQ", 90))
            .isEqualTo("https://m.youtube.com/watch?v=dQw4w9WgXcQ&t=90")
    }
}
