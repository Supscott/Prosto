package com.jestk.browser.engine

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Hard-coded bar colors for sites whose theme color or sampled pixels give the wrong bar color.
 * Matches the page host (the site itself or a subdomain), never text elsewhere in the URL.
 */
internal object SiteThemeOverride {
    private const val APPLE_BAR = 0xFF161617.toInt()
    private const val YOUTUBE_DARK_BAR = 0xFF0F0F0F.toInt()

    /** The fixed bar color for [url], or null when the page's own colors apply. */
    fun barColor(url: String, isDark: Boolean): Int? {
        val host = url.toHttpUrlOrNull()?.host ?: return null
        return when {
            host.isOnSite("apple.com") -> APPLE_BAR
            isDark && host.isOnSite("youtube.com") -> YOUTUBE_DARK_BAR
            else -> null
        }
    }

    /** True when sampled page colors must not replace the fixed bar color on [url]. */
    fun ignoresSampledColors(url: String): Boolean =
        url.toHttpUrlOrNull()?.host?.isOnSite("apple.com") == true

    private fun String.isOnSite(site: String): Boolean = this == site || endsWith(".$site")
}
