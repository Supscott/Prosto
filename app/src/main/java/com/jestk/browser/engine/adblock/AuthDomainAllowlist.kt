package com.jestk.browser.engine.adblock


internal object AuthDomainAllowlist {
    internal val AUTH_SECURITY_ALLOWLIST: Set<String> =
        setOf(
            "arkoselabs.com",
            "client-api.arkoselabs.com",
            "arkose.com",
            "challenges.cloudflare.com",
            "cloudflare.com",
            "cloudflarecaptcha.com",
            "hcaptcha.com",
            "recaptcha.net",
        )

    internal fun isAllowlistedAuthDomain(host: String): Boolean {
        if (host.isEmpty()) return false
        val lowerHost = host.lowercase()
        for (allowed in AUTH_SECURITY_ALLOWLIST) {
            if (lowerHost == allowed || lowerHost.endsWith(".$allowed")) {
                return true
            }
        }
        return false
    }
}
