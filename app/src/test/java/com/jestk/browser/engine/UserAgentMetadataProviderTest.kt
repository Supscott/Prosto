package com.jestk.browser.engine

import androidx.webkit.UserAgentMetadata
import androidx.webkit.UserAgentMetadata.BrandVersion
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
        assertThat(UserAgentMetadataProvider.extractPlatformVersion(ua14)).isEqualTo("14.0.0")

        val ua10 = "Mozilla/5.0 (Linux; U; Android 10; SM-G960F) AppleWebKit/537.36 Chrome/106.0.5249.126 Mobile Safari/537.36"
        assertThat(UserAgentMetadataProvider.extractPlatformVersion(ua10)).isEqualTo("10.0.0")

        val uaPointRelease = "Mozilla/5.0 (Linux; Android 13.1; Tablet) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36"
        assertThat(UserAgentMetadataProvider.extractPlatformVersion(uaPointRelease)).isEqualTo("13.1.0")
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
    fun buildOrUpdateMetadata_whenCurrentMetadataPresent_replacesAndroidWebViewWithGoogleChromeAndPreservesChromiumGrease() {
        val inputBrands = listOf(
            BrandVersion.Builder().setBrand("Not?A_Brand").setMajorVersion("24").setFullVersion("24.0.0.0").build(),
            BrandVersion.Builder().setBrand("Chromium").setMajorVersion("130").setFullVersion("130.0.6723.102").build(),
            BrandVersion.Builder().setBrand("Android WebView").setMajorVersion("130").setFullVersion("130.0.6723.102").build()
        )
        val currentMetadata = UserAgentMetadata.Builder()
            .setBrandVersionList(inputBrands)
            .setFullVersion("130.0.6723.102")
            .setPlatform("Android")
            .setPlatformVersion("14.0.0")
            .setArchitecture("arm")
            .setBitness(64)
            .setModel("Pixel 8")
            .setMobile(true)
            .build()

        val updated = UserAgentMetadataProvider.buildOrUpdateMetadata(
            currentMetadata = currentMetadata,
            userAgent = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
        )

        assertThat(updated.isMobile).isTrue()
        assertThat(updated.platform).isEqualTo("Android")
        assertThat(updated.platformVersion).isEqualTo("14.0.0")
        assertThat(updated.model).isEqualTo("Pixel 8")
        assertThat(updated.architecture).isEqualTo("arm")
        assertThat(updated.bitness).isEqualTo(64)
        assertThat(updated.fullVersion).isEqualTo("130.0.6723.102")

        val brandMap = updated.brandVersionList.associate { it.brand to it.majorVersion }
        assertThat(brandMap).containsEntry("Not?A_Brand", "24")
        assertThat(brandMap).containsEntry("Chromium", "130")
        assertThat(brandMap).containsEntry("Google Chrome", "130")
        assertThat(brandMap).doesNotContainKey("Android WebView")

        val chromeBrand = updated.brandVersionList.first { it.brand == "Google Chrome" }
        assertThat(chromeBrand.fullVersion).isEqualTo("130.0.6723.102")
    }

    @Test
    fun buildOrUpdateMetadata_whenCurrentMetadataNull_fallsBackToFallbackMetadata() {
        val metadata = UserAgentMetadataProvider.buildOrUpdateMetadata(
            currentMetadata = null,
            userAgent = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36"
        )

        assertThat(metadata.isMobile).isTrue()
        assertThat(metadata.platform).isEqualTo("Android")
        assertThat(metadata.platformVersion).isEqualTo("10.0.0")
        assertThat(metadata.model).isEmpty()
        assertThat(metadata.architecture).isEmpty()
        assertThat(metadata.bitness).isAtLeast(32)

        val brandMap = metadata.brandVersionList.associate { it.brand to it.majorVersion }
        assertThat(brandMap).containsEntry("Chromium", "121")
        assertThat(brandMap).containsEntry("Google Chrome", "121")
        assertThat(brandMap).containsEntry("Not(A:Brand)", "99")
    }

    @Test
    fun buildFallbackMetadata_createsAlignedMetadata_forStandardMobileChromeUA() {
        val ua = "Mozilla/5.0 (Linux; Android 14; Pixel 7 Build/UQ1A.240205.002) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.6167.101 Mobile Safari/537.36"
        val metadata = UserAgentMetadataProvider.buildFallbackMetadata(
            userAgent = ua,
            deviceModel = "Pixel 7",
            osRelease = "14"
        )

        assertThat(metadata.isMobile).isTrue()
        assertThat(metadata.platform).isEqualTo("Android")
        assertThat(metadata.platformVersion).isEqualTo("14.0.0")
        assertThat(metadata.model).isEqualTo("Pixel 7")
        assertThat(metadata.fullVersion).isEqualTo("121.0.6167.101")
        assertThat(metadata.bitness).isAtLeast(32)

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
    }

    @Test
    fun buildFallbackMetadata_handlesFallback_whenUserAgentIsMissingOrEmpty() {
        val metadata = UserAgentMetadataProvider.buildFallbackMetadata(
            userAgent = null,
            deviceModel = "",
            osRelease = ""
        )

        assertThat(metadata.isMobile).isTrue()
        assertThat(metadata.platform).isEqualTo("Android")
        assertThat(metadata.platformVersion).isEqualTo("10.0.0")
        assertThat(metadata.model).isEmpty()
        assertThat(metadata.fullVersion).isEqualTo("${UserAgentMetadataProvider.DEFAULT_CHROME_MAJOR_VERSION}.0.0.0")
        assertThat(metadata.bitness).isAtLeast(32)

        val brandMap = metadata.brandVersionList.associate { it.brand to it.majorVersion }
        assertThat(brandMap).containsEntry("Chromium", UserAgentMetadataProvider.DEFAULT_CHROME_MAJOR_VERSION)
        assertThat(brandMap).containsEntry("Google Chrome", UserAgentMetadataProvider.DEFAULT_CHROME_MAJOR_VERSION)
        assertThat(brandMap).containsEntry("Not(A:Brand)", "99")
    }

    @Test
    fun buildFallbackMetadata_extractsFullVersion_fromRawUserAgent_whenSanitizedIsReduced() {
        val rawUa = "Mozilla/5.0 (Linux; Android 14; Pixel 7 Build/UQ1A.240205.002) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.6167.101 Mobile Safari/537.36"
        val reducedUa = UserAgentSanitizer.sanitize(rawUa)

        val metadata = UserAgentMetadataProvider.buildFallbackMetadata(
            userAgent = reducedUa,
            rawUserAgent = rawUa,
            deviceModel = "Pixel 7",
            osRelease = "14"
        )

        assertThat(metadata.fullVersion).isEqualTo("121.0.6167.101")
        val chromeBrand = metadata.brandVersionList.first { it.brand == "Google Chrome" }
        assertThat(chromeBrand.majorVersion).isEqualTo("121")
        assertThat(chromeBrand.fullVersion).isEqualTo("121.0.6167.101")
    }
}
