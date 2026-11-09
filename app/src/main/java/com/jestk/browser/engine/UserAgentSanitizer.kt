package com.jestk.browser.engine

/**
 * Sanitizes Android WebView User-Agent strings to match standalone Google Chrome on Android.
 *
 * By default, Android WebView injects embedded container indicators:
 * 1. The `; wv` (or `wv;`) token inside the platform paren, e.g. `(Linux; Android 14; ...; wv)`
 * 2. The `Version/4.0` prefix before `Chrome/...`
 *
 * Major web applications (such as X/Twitter, Google accounts, Cloudflare Turnstile, and Arkose Labs)
 * inspect these tokens to flag and block embedded web containers. Stripping them aligns the User-Agent
 * with standard mobile Chrome.
 *
 * Furthermore, Chrome 107+ enabled User-Agent Reduction on Android:
 * - The platform token is frozen to `(Linux; Android 10; K)` (eliminating device model and OS build).
 * - The Chrome version is frozen to `Chrome/<major>.0.0.0` (zeroing minor and build numbers).
 */
object UserAgentSanitizer {

    private val WV_TRAILING_PATTERN = Regex(";\\s*wv\\b")
    private val WV_LEADING_PATTERN = Regex("\\bwv;\\s*")
    private val VERSION_TOKEN_PATTERN = Regex("Version/\\d+(\\.\\d+)+\\s*")
    private val REDUCED_PLATFORM_PATTERN = Regex("""\((Linux;\s*(?:U;\s*)?)Android\s+[^)]*\)""")
    private val CHROME_VERSION_REDUCTION_PATTERN = Regex("""\bChrome/([0-9]+)\.[0-9]+(?:\.[0-9]+)*\b""")

    fun sanitize(userAgent: String?): String {
        if (userAgent.isNullOrEmpty()) return userAgent.orEmpty()
        if (userAgent.isBlank()) return userAgent

        var sanitized = userAgent
        sanitized = WV_TRAILING_PATTERN.replace(sanitized, "")
        sanitized = WV_LEADING_PATTERN.replace(sanitized, "")
        sanitized = VERSION_TOKEN_PATTERN.replace(sanitized, "")
        sanitized = REDUCED_PLATFORM_PATTERN.replace(sanitized, "(Linux; Android 10; K)")
        sanitized = CHROME_VERSION_REDUCTION_PATTERN.replace(sanitized, "Chrome/$1.0.0.0")
        return sanitized
    }
}
