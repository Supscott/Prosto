package com.jestk.browser.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UserAgentSanitizerTest {

    @Test
    fun sanitize_stripsWebViewMarkers_fromTypicalAndroidWebViewUA() {
        val rawUa = "Mozilla/5.0 (Linux; Android 14; Pixel 7 Build/UQ1A.240205.002; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/121.0.6167.101 Mobile Safari/537.36"
        val expected = "Mozilla/5.0 (Linux; Android 14; Pixel 7 Build/UQ1A.240205.002) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.6167.101 Mobile Safari/537.36"

        assertThat(UserAgentSanitizer.sanitize(rawUa)).isEqualTo(expected)
    }

    @Test
    fun sanitize_leavesStandardMobileChromeUA_unchanged() {
        val chromeUa = "Mozilla/5.0 (Linux; Android 14; Pixel 7 Build/UQ1A.240205.002) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.6167.101 Mobile Safari/537.36"

        assertThat(UserAgentSanitizer.sanitize(chromeUa)).isEqualTo(chromeUa)
    }

    @Test
    fun sanitize_leavesDesktopChromeUA_unchanged() {
        val desktopUa = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"

        assertThat(UserAgentSanitizer.sanitize(desktopUa)).isEqualTo(desktopUa)
    }

    @Test
    fun sanitize_handlesDifferentAndroidVersionsAndSpacing() {
        val rawUa = "Mozilla/5.0 (Linux; U; Android 10; SM-G960F Build/QP1A.190711.020;  wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/106.0.5249.126 Mobile Safari/537.36"
        val expected = "Mozilla/5.0 (Linux; U; Android 10; SM-G960F Build/QP1A.190711.020) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/106.0.5249.126 Mobile Safari/537.36"

        assertThat(UserAgentSanitizer.sanitize(rawUa)).isEqualTo(expected)
    }

    @Test
    fun sanitize_handlesMiddleWvToken() {
        val rawUa = "Mozilla/5.0 (Linux; Android 13; wv; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/115.0.0.0 Mobile Safari/537.36"
        val expected = "Mozilla/5.0 (Linux; Android 13; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Mobile Safari/537.36"

        assertThat(UserAgentSanitizer.sanitize(rawUa)).isEqualTo(expected)
    }

    @Test
    fun sanitize_handlesNullAndEmptyStrings() {
        assertThat(UserAgentSanitizer.sanitize(null)).isEmpty()
        assertThat(UserAgentSanitizer.sanitize("")).isEmpty()
        assertThat(UserAgentSanitizer.sanitize("   ")).isEqualTo("   ")
    }

    @Test
    fun sanitize_handlesMultiDigitVersionTokens() {
        val rawUa = "Mozilla/5.0 (Linux; Android 15; Device; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0.0.0 Chrome/130.0.0.0 Mobile Safari/537.36"
        val expected = "Mozilla/5.0 (Linux; Android 15; Device) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

        assertThat(UserAgentSanitizer.sanitize(rawUa)).isEqualTo(expected)
    }
}
