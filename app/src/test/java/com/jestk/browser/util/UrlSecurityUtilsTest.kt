package com.jestk.browser.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UrlSecurityUtilsTest {

    @Test
    fun getBaseDomain_returnsRegistrableDomain() {
        assertThat(UrlSecurityUtils.getBaseDomain("yandex.ru")).isEqualTo("yandex.ru")
        assertThat(UrlSecurityUtils.getBaseDomain("mc.yandex.ru")).isEqualTo("yandex.ru")
        assertThat(UrlSecurityUtils.getBaseDomain("mail.yandex.ru")).isEqualTo("yandex.ru")
        assertThat(UrlSecurityUtils.getBaseDomain("news.mail.yandex.ru")).isEqualTo("yandex.ru")
        assertThat(UrlSecurityUtils.getBaseDomain("example.com")).isEqualTo("example.com")
        assertThat(UrlSecurityUtils.getBaseDomain("sub.deep.example.com")).isEqualTo("example.com")
        assertThat(UrlSecurityUtils.getBaseDomain("bbc.co.uk")).isEqualTo("bbc.co.uk")
        assertThat(UrlSecurityUtils.getBaseDomain("news.bbc.co.uk")).isEqualTo("bbc.co.uk")
        assertThat(UrlSecurityUtils.getBaseDomain("localhost")).isEqualTo("localhost")
        assertThat(UrlSecurityUtils.getBaseDomain("127.0.0.1")).isEqualTo("127.0.0.1")
    }

    @Test
    fun isSameSite_handlesExactAndSubdomainMatches() {
        assertThat(UrlSecurityUtils.isSameSite("https://yandex.ru", "https://yandex.ru")).isTrue()
        assertThat(UrlSecurityUtils.isSameSite("https://yandex.ru", "https://mc.yandex.ru/metrika/tag.js")).isTrue()
        assertThat(UrlSecurityUtils.isSameSite("https://mail.yandex.ru", "https://mc.yandex.ru/watch")).isTrue()
        assertThat(UrlSecurityUtils.isSameSite("https://ya.ru", "https://mc.yandex.ru")).isTrue()
        assertThat(UrlSecurityUtils.isSameSite("https://vk.com", "https://ads.vk.com/banner")).isTrue()
        assertThat(UrlSecurityUtils.isSameSite("https://google.com", "https://google-analytics.com/analytics.js")).isTrue()
        assertThat(UrlSecurityUtils.isSameSite("https://bbc.co.uk", "https://news.bbc.co.uk")).isTrue()
    }

    @Test
    fun isSameSite_handlesThirdPartyUrls() {
        assertThat(UrlSecurityUtils.isSameSite("https://example.com", "https://mc.yandex.ru")).isFalse()
        assertThat(UrlSecurityUtils.isSameSite("https://example.com", "https://google-analytics.com")).isFalse()
        assertThat(UrlSecurityUtils.isSameSite("https://example.com", "https://vk.com")).isFalse()
        assertThat(UrlSecurityUtils.isSameSite("", "https://example.com")).isFalse()
        assertThat(UrlSecurityUtils.isSameSite("about:blank", "https://example.com")).isFalse()
    }
}
