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
 */
object UserAgentSanitizer {

    private val WV_TRAILING_PATTERN = Regex(";\\s*wv\\b")
    private val WV_LEADING_PATTERN = Regex("\\bwv;\\s*")
    private val VERSION_TOKEN_PATTERN = Regex("Version/\\d+(\\.\\d+)+\\s*")

    fun sanitize(userAgent: String?): String {
        if (userAgent.isNullOrEmpty()) return userAgent.orEmpty()
        if (userAgent.isBlank()) return userAgent

        var sanitized = userAgent
        sanitized = WV_TRAILING_PATTERN.replace(sanitized, "")
        sanitized = WV_LEADING_PATTERN.replace(sanitized, "")
        sanitized = VERSION_TOKEN_PATTERN.replace(sanitized, "")
        return sanitized
    }
}
