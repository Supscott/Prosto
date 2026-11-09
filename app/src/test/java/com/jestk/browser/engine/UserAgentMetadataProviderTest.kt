package com.jestk.browser.engine

import androidx.webkit.UserAgentMetadata
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserAgentMetadataProviderTest {

    @Test
    fun extractChromeMajorVersion_extractsFromStandardAndroidUA() {
        val ua = "Mozilla/5.0 (Linux; Android 14; Pixel 7 Build/UQ1A.240205.002) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.6167.101 Mobile Safari/537.36"
        assertThat(UserAgentMetadataProvider.extractChromeMajorVersion(ua)).isEqualTo("121")
    }

    @Test
    fun extractChromeMajorVersion_extractsFromDifferentVersions() {
        val ua1 = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
        assertThat(UserAgentMetadataProvider.extractChromeMajorVersion(ua1)).isEqualTo("130")

        val ua2 = "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/99.0.4844.88 Mobile Safari/537.36"
        assertThat(UserAgentMetadataProvider.extractChromeMajorVersion(ua2)).isEqualTo("99")

        val ua3 = "Mozilla/5.0 (Linux; Android 15; Pixel 8) AppleWebKit/537.36 Chrome/133 Mobile Safari/537.36"
        assertThat(UserAgentMetadataProvider.extractChromeMajorVersion(ua3)).isEqualTo("133")
    }

    @Test
    fun extractChromeMajorVersion_fallsBackToDefaultWhenMissingOrInvalid() {
        assertThat(UserAgentMetadataProvider.extractChromeMajorVersion(null))
            .isEqualTo(UserAgentMetadataProvider.DEFAULT_CHROME_MAJOR_VERSION)
        assertThat(UserAgentMetadataProvider.extractChromeMajorVersion(""))
            .isEqualTo(UserAgentMetadataProvider.DEFAULT_CHROME_MAJOR_VERSION)
        assertThat(UserAgentMetadataProvider.extractChromeMajorVersion("   "))
            .isEqualTo(UserAgentMetadataProvider.DEFAULT_CHROME_MAJOR_VERSION)
        assertThat(UserAgentMetadataProvider.extractChromeMajorVersion("Mozilla/5.0 (Mobile; rv:109.0) Gecko/109.0 Firefox/119.0"))
            .isEqualTo(UserAgentMetadataProvider.DEFAULT_CHROME_MAJOR_VERSION)
    }

    @Test
    fun extractChromeFullVersion_extractsFullVersionString() {
        val ua = "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 Chrome/121.0.6167.101 Mobile Safari/537.36"
        assertThat(UserAgentMetadataProvider.extractChromeFullVersion(ua)).isEqualTo("121.0.6167.101")

        val uaMajorOnly = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/130 Mobile Safari/537.36"
        assertThat(UserAgentMetadataProvider.extractChromeFullVersion(uaMajorOnly)).isEqualTo("130.0.0.0")

        assertThat(UserAgentMetadataProvider.extractChromeFullVersion(null))
            .isEqualTo("${UserAgentMetadataProvider.DEFAULT_CHROME_MAJOR_VERSION}.0.0.0")
        assertThat(UserAgentMetadataProvider.extractChromeFullVersion("invalid"))
            .isEqualTo("${UserAgentMetadataProvider.DEFAULT_CHROME_MAJOR_VERSION}.0.0.0")
    }

    @Test
    fun extractPlatformVersion_extractsAndroidMajorVersion() {
        val ua14 = "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 Chrome/121.0.6167.101 Mobile Safari/537.36"
        assertThat(UserAgentMetadataProvider.extractPlatformVersion(ua14)).isEqualTo("14")

        val ua10 = "Mozilla/5.0 (Linux; U; Android 10; SM-G960F) AppleWebKit/537.36 Chrome/106.0.5249.126 Mobile Safari/537.36"
        assertThat(UserAgentMetadataProvider.extractPlatformVersion(ua10)).isEqualTo("10")

        val uaPointRelease = "Mozilla/5.0 (Linux; Android 13.1; Tablet) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36"
        assertThat(UserAgentMetadataProvider.extractPlatformVersion(uaPointRelease)).isEqualTo("13.1")
    }

    @Test
    fun extractPlatformVersion_fallsBackToDefaultWhenMissingOrInvalid() {
        assertThat(UserAgentMetadataProvider.extractPlatformVersion(null))
            .isEqualTo(UserAgentMetadataProvider.DEFAULT_PLATFORM_VERSION)
        assertThat(UserAgentMetadataProvider.extractPlatformVersion(""))
            .isEqualTo(UserAgentMetadataProvider.DEFAULT_PLATFORM_VERSION)
        assertThat(UserAgentMetadataProvider.extractPlatformVersion("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36"))
            .isEqualTo(UserAgentMetadataProvider.DEFAULT_PLATFORM_VERSION)
    }

    @Test
    fun buildMetadata_createsAlignedMetadata_forStandardMobileChromeUA() {
        val ua = "Mozilla/5.0 (Linux; Android 14; Pixel 7 Build/UQ1A.240205.002) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.6167.101 Mobile Safari/537.36"
        val metadata: UserAgentMetadata = UserAgentMetadataProvider.buildMetadata(ua)

        assertThat(metadata.isMobile).isTrue()
        assertThat(metadata.platform).isEqualTo("Android")
        assertThat(metadata.platformVersion).isEqualTo("14")
        assertThat(metadata.architecture).isEmpty()
        assertThat(metadata.model).isEmpty()
        assertThat(metadata.fullVersion).isEqualTo("121.0.6167.101")

        val brands = metadata.brandVersionList
        assertThat(brands).hasSize(3)

        val brandMap = brands.associate { it.brand to it.majorVersion }
        assertThat(brandMap).containsEntry("Chromium", "121")
        assertThat(brandMap).containsEntry("Google Chrome", "121")
        assertThat(brandMap).containsEntry("Not(A:Brand)", "99")

        val chromiumBrand = brands.first { it.brand == "Chromium" }
        assertThat(chromiumBrand.majorVersion).isEqualTo("121")
        assertThat(chromiumBrand.fullVersion).isEqualTo("121.0.6167.101")

        val googleChromeBrand = brands.first { it.brand == "Google Chrome" }
        assertThat(googleChromeBrand.majorVersion).isEqualTo("121")
        assertThat(googleChromeBrand.fullVersion).isEqualTo("121.0.6167.101")

        val greaseBrand = brands.first { it.brand == "Not(A:Brand)" }
        assertThat(greaseBrand.majorVersion).isEqualTo("99")
        assertThat(greaseBrand.fullVersion).isEqualTo("99.0.0.0")
    }

    @Test
    fun buildMetadata_handlesFallback_whenUserAgentIsMissingOrEmpty() {
        val metadata = UserAgentMetadataProvider.buildMetadata(null)

        assertThat(metadata.isMobile).isTrue()
        assertThat(metadata.platform).isEqualTo("Android")
        assertThat(metadata.platformVersion).isEqualTo("10")
        assertThat(metadata.architecture).isEmpty()
        assertThat(metadata.model).isEmpty()
        assertThat(metadata.fullVersion).isEqualTo("${UserAgentMetadataProvider.DEFAULT_CHROME_MAJOR_VERSION}.0.0.0")

        val brandMap = metadata.brandVersionList.associate { it.brand to it.majorVersion }
        assertThat(brandMap).containsEntry("Chromium", UserAgentMetadataProvider.DEFAULT_CHROME_MAJOR_VERSION)
        assertThat(brandMap).containsEntry("Google Chrome", UserAgentMetadataProvider.DEFAULT_CHROME_MAJOR_VERSION)
        assertThat(brandMap).containsEntry("Not(A:Brand)", "99")
    }
}
