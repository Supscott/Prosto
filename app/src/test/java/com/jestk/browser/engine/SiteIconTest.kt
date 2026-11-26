package com.jestk.browser.engine

import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.SearchEngine
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SiteIconTest {
    @Test
    fun originOf_keepsSchemeHostAndPort_andDropsPathAndQuery() {
        assertThat(SiteIcon.originOf("https://example.com/a/b?q=1#x")).isEqualTo("https://example.com")
        assertThat(SiteIcon.originOf("http://Example.com:8080/")).isEqualTo("http://example.com:8080")
        assertThat(SiteIcon.originOf("https://www.example.com")).isEqualTo("https://www.example.com")
    }

    @Test
    fun originOf_skipsNonWebAndPrivateHosts() {
        assertThat(SiteIcon.originOf(null)).isNull()
        assertThat(SiteIcon.originOf("  ")).isNull()
        assertThat(SiteIcon.originOf("about:blank")).isNull()
        assertThat(SiteIcon.originOf("file:///android_asset/page.html")).isNull()
        assertThat(SiteIcon.originOf("data:image/png;base64,1234")).isNull()
        assertThat(SiteIcon.originOf("http://localhost:8080")).isNull()
        assertThat(SiteIcon.originOf("http://127.0.0.1:3000")).isNull()
        assertThat(SiteIcon.originOf("http://192.168.1.1")).isNull()
        assertThat(SiteIcon.originOf("http://10.0.0.1/admin")).isNull()
        assertThat(SiteIcon.originOf("http://172.16.0.1")).isNull()
        assertThat(SiteIcon.originOf("http://172.31.255.1")).isNull()
        assertThat(SiteIcon.originOf("http://169.254.1.1")).isNull()
        assertThat(SiteIcon.originOf("http://mydev.local")).isNull()
        assertThat(SiteIcon.originOf("http://router")).isNull()
        assertThat(SiteIcon.originOf("http://[::1]/")).isNull()
        // Outside the private ranges
        assertThat(SiteIcon.originOf("http://172.32.0.1")).isEqualTo("http://172.32.0.1")
        assertThat(SiteIcon.originOf("http://8.8.8.8")).isEqualTo("http://8.8.8.8")
    }

    @Test
    fun isPrivateHost_coversReservedIpv4Ranges() {
        listOf("100.64.0.1", "100.127.255.254", "192.0.0.8", "169.254.169.254", "0.0.0.0", "224.0.0.1", "255.255.255.255")
            .forEach { assertThat(SiteIcon.isPrivateHost(it)).isTrue() }
        listOf("100.63.0.1", "100.128.0.1", "192.0.2.1", "172.15.0.1", "172.32.0.1", "11.0.0.1", "93.184.216.34")
            .forEach { assertThat(SiteIcon.isPrivateHost(it)).isFalse() }
    }

    @Test
    fun isPrivateHost_treatsEveryIpv6LiteralAsUnsafe() {
        listOf("::1", "[::1]", "fe80::1", "[fe80::abcd]", "fc00::1", "fd12:3456::1", "[2001:db8::1]")
            .forEach { assertThat(SiteIcon.isPrivateHost(it)).isTrue() }
    }

    @Test
    fun isPrivateHost_rejectsNumericIpv4FormsThatAreNotPlainDottedDecimal() {
        // 127.0.0.1 as a single integer, in hex, in octal, and with a leading zero or an extra part
        listOf("2130706433", "0x7f000001", "0x7f.0.0.1", "0177.0.0.1", "127.1", "010.0.0.1", "1.2.3", "1.2.3.4.5", "999.1.1.1")
            .forEach { assertThat(SiteIcon.isPrivateHost(it)).isTrue() }
    }

    @Test
    fun isPrivateHost_coversSuffixesAndSingleLabelNames() {
        listOf("printer.local", "nas.lan", "svc.internal", "router.home.arpa", "app.test", "a.localhost", "localhost", "localhost.", "intranet")
            .forEach { assertThat(SiteIcon.isPrivateHost(it)).isTrue() }
        listOf("example.com", "www.example.co.uk", "test.example.org", "local.example.com", "lan.io")
            .forEach { assertThat(SiteIcon.isPrivateHost(it)).isFalse() }
    }

    @Test
    fun candidateUrls_tryTheTouchIconBeforeFavicon() {
        assertThat(SiteIcon.candidateUrls("http://example.com:8080")).containsExactly(
            "http://example.com:8080/apple-touch-icon.png",
            "http://example.com:8080/favicon.ico",
        ).inOrder()
    }

    @Test
    fun letterFor_usesFirstLetterOfHostWithoutWww() {
        assertThat(SiteIcon.letterFor("https://www.example.com/x")).isEqualTo("E")
        assertThat(SiteIcon.letterFor("siteicon:https://www.wikipedia.org")).isEqualTo("W")
        assertThat(SiteIcon.letterFor("kagi.com")).isEqualTo("K")
        assertThat(SiteIcon.letterFor("http://192.168.1.1")).isEqualTo("1")
        assertThat(SiteIcon.letterFor(null)).isEqualTo("?")
        assertThat(SiteIcon.letterFor("")).isEqualTo("?")
    }

    @Test
    fun colorIndexFor_isStableAndIgnoresWwwSchemeAndPath() {
        val a = SiteIcon.colorIndexFor("https://www.example.com/page", 8)
        assertThat(SiteIcon.colorIndexFor("http://example.com", 8)).isEqualTo(a)
        assertThat(SiteIcon.colorIndexFor("siteicon:https://example.com", 8)).isEqualTo(a)
        assertThat(a).isIn(0..7)
    }

    @Test
    fun legacyGoogleS2Url_mapsToTheSiteChain_withoutKeepingTheGoogleUrl() {
        val legacy = "https://www.google.com/s2/favicons?sz=128&domain=example.com"
        assertThat(SiteIcon.isRef(legacy)).isTrue()
        assertThat(SiteIcon.originOfRef(legacy)).isEqualTo("https://example.com")
        assertThat(FaviconManager.toFaviconImageModel(legacy))
            .isEqualTo(FaviconImageModel.Site("https://example.com"))
        assertThat(FaviconManager.toImageModel(legacy)).isEqualTo(SiteIconModel("https://example.com"))
    }

    @Test
    fun legacyGoogleS2Url_ofAPrivateHostOrWithoutDomain_hasNoModel() {
        assertThat(FaviconManager.toFaviconImageModel("https://www.google.com/s2/favicons?domain=192.168.0.2")).isNull()
        assertThat(FaviconManager.toFaviconImageModel("https://www.google.com/s2/favicons?sz=128")).isNull()
    }

    @Test
    fun otherGoogleUrlsAreNotTreatedAsLegacyIcons() {
        assertThat(SiteIcon.isRef("https://www.google.com/search?q=x")).isFalse()
        assertThat(SiteIcon.isRef("https://example.com/s2/favicons?domain=a.com")).isFalse()
    }

    @Test
    fun siteReference_roundTripsThroughTheImageModel() {
        val ref = FaviconManager.extractFallbackFaviconUrl("http://example.com:8080/page")
        assertThat(FaviconManager.toFaviconImageModel(ref)).isEqualTo(FaviconImageModel.Site("http://example.com:8080"))
        assertThat(FaviconManager.isLocalFaviconPath(ref)).isFalse()
    }

    @Test
    fun everyDefaultSearchEngineHasABundledIcon() {
        for (engine in SearchEngine.DEFAULT_ENGINES) {
            assertThat(BundledSearchIcons.isBundledRef(engine.iconUrl)).isTrue()
            assertThat(BundledSearchIcons.resIdOf(engine.iconUrl)).isNotNull()
            assertThat(FaviconManager.toImageModel(engine.iconUrl)).isInstanceOf(Int::class.javaObjectType)
        }
    }

    @Test
    fun unknownBundledReference_hasNoModel() {
        assertThat(FaviconManager.toFaviconImageModel("bundled:engine/nope")).isNull()
    }
}
