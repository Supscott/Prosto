package com.jestk.browser.engine

import androidx.webkit.UserAgentMetadata

/**
 * Builds aligned User-Agent Client Hints metadata ([UserAgentMetadata]) for Android WebView.
 *
 * Android WebView by default exposes "Android WebView" brands and outdated versioning
 * via `navigator.userAgentData`, even when `WebSettings.userAgentString` is sanitized.
 *
 * This provider extracts the Chrome major version and platform version from the sanitized
 * User-Agent string and produces a [UserAgentMetadata] instance with:
 * - mobile = true
 * - platform = "Android"
 * - platformVersion = matched Android version (or "10" default)
 * - architecture = "" (privacy / anti-fingerprinting)
 * - model = "" (privacy / anti-fingerprinting)
 * - brands = Chromium, Google Chrome, and standard Chromium GREASE brand Not(A:Brand)
 */
object UserAgentMetadataProvider {

    const val DEFAULT_CHROME_MAJOR_VERSION = "130"
    const val DEFAULT_PLATFORM_VERSION = "10"
    const val GREASE_BRAND = "Not(A:Brand)"
    const val GREASE_MAJOR_VERSION = "99"
    const val GREASE_FULL_VERSION = "99.0.0.0"

    private const val BRAND_CHROMIUM = "Chromium"
    private const val BRAND_GOOGLE_CHROME = "Google Chrome"

    private val CHROME_VERSION_PATTERN = Regex("""\bChrome/([0-9]+)(?:\.([0-9]+(?:\.[0-9]+)*))?""", RegexOption.IGNORE_CASE)
    private val ANDROID_VERSION_PATTERN = Regex("""\bAndroid\s+([0-9]+(?:\.[0-9]+)*)""", RegexOption.IGNORE_CASE)

    fun extractChromeMajorVersion(userAgent: String?): String {
        if (userAgent.isNullOrBlank()) return DEFAULT_CHROME_MAJOR_VERSION
        val match = CHROME_VERSION_PATTERN.find(userAgent) ?: return DEFAULT_CHROME_MAJOR_VERSION
        return match.groupValues[1]
    }

    fun extractChromeFullVersion(userAgent: String?): String {
        if (userAgent.isNullOrBlank()) return "$DEFAULT_CHROME_MAJOR_VERSION.0.0.0"
        val match = CHROME_VERSION_PATTERN.find(userAgent) ?: return "$DEFAULT_CHROME_MAJOR_VERSION.0.0.0"
        val full = match.value.substringAfter('/')
        return if (full.contains('.')) full else "$full.0.0.0"
    }

    fun extractPlatformVersion(userAgent: String?): String {
        if (userAgent.isNullOrBlank()) return DEFAULT_PLATFORM_VERSION
        val match = ANDROID_VERSION_PATTERN.find(userAgent) ?: return DEFAULT_PLATFORM_VERSION
        return match.groupValues[1]
    }

    fun buildMetadata(userAgent: String?): UserAgentMetadata {
        val majorVersion = extractChromeMajorVersion(userAgent)
        val fullVersion = extractChromeFullVersion(userAgent)
        val platformVersion = extractPlatformVersion(userAgent)

        val brandList = listOf(
            UserAgentMetadata.BrandVersion.Builder()
                .setBrand(BRAND_CHROMIUM)
                .setMajorVersion(majorVersion)
                .setFullVersion(fullVersion)
                .build(),
            UserAgentMetadata.BrandVersion.Builder()
                .setBrand(BRAND_GOOGLE_CHROME)
                .setMajorVersion(majorVersion)
                .setFullVersion(fullVersion)
                .build(),
            UserAgentMetadata.BrandVersion.Builder()
                .setBrand(GREASE_BRAND)
                .setMajorVersion(GREASE_MAJOR_VERSION)
                .setFullVersion(GREASE_FULL_VERSION)
                .build()
        )

        return UserAgentMetadata.Builder()
            .setBrandVersionList(brandList)
            .setFullVersion(fullVersion)
            .setPlatform("Android")
            .setPlatformVersion(platformVersion)
            .setArchitecture("")
            .setModel("")
            .setMobile(true)
            .build()
    }
}
