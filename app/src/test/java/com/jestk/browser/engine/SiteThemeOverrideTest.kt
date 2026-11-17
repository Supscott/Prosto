package com.jestk.browser.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SiteThemeOverrideTest {
    @Test
    fun appleSiteAndSubdomainsGetTheFixedBar() {
        assertThat(SiteThemeOverride.barColor("https://www.apple.com/iphone/", isDark = false)).isEqualTo(0xFF161617.toInt())
        assertThat(SiteThemeOverride.barColor("https://apple.com", isDark = true)).isEqualTo(0xFF161617.toInt())
        assertThat(SiteThemeOverride.ignoresSampledColors("https://support.apple.com/kb")).isTrue()
    }

    @Test
    fun youtubeGetsTheFixedBarOnlyInDarkMode() {
        assertThat(SiteThemeOverride.barColor("https://m.youtube.com/watch?v=x", isDark = true)).isEqualTo(0xFF0F0F0F.toInt())
        assertThat(SiteThemeOverride.barColor("https://m.youtube.com/watch?v=x", isDark = false)).isNull()
    }

    @Test
    fun textElsewhereInTheUrlDoesNotMatch() {
        val urls = listOf(
            "https://www.google.com/search?q=apple.com",
            "https://pineapple.com/",
            "https://notyoutube.com/",
            "https://example.com/?next=https://www.youtube.com/",
            "https://apple.com.evil.example/",
        )
        for (url in urls) {
            assertThat(SiteThemeOverride.barColor(url, isDark = true)).isNull()
            assertThat(SiteThemeOverride.ignoresSampledColors(url)).isFalse()
        }
    }
}
