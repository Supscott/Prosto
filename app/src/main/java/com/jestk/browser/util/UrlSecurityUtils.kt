package com.jestk.browser.util

import android.net.Uri
import com.jestk.browser.engine.AdBlockerEngine

/**
 * Shared utility for detecting and formatting cleartext HTTP and insecure URLs.
 */
object UrlSecurityUtils {

    /**
     * Checks if the given URL uses the unencrypted HTTP scheme.
     */
    fun isHttpUrl(url: String): Boolean {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return false
        val scheme = try {
            Uri.parse(trimmed).scheme?.lowercase()
        } catch (_: Exception) {
            null
        }
        return scheme == "http" || trimmed.startsWith("http://", ignoreCase = true)
    }

    /**
     * Extracts the canonical host without port or protocol (e.g. "example.com" or "localhost")
     * for matching and whitelisting.
     */
    fun extractHost(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return ""
        return try {
            val uri = Uri.parse(trimmed)
            uri.host?.lowercase()?.trim()?.takeIf { it.isNotEmpty() }
                ?: trimmed.removePrefix("http://").removePrefix("https://").substringBefore('/').substringBefore(':').lowercase().trim()
        } catch (_: Exception) {
            trimmed.removePrefix("http://").removePrefix("https://").substringBefore('/').substringBefore(':').lowercase().trim()
        }
    }

    /**
     * Extracts host and custom port (e.g. "localhost:8080" or "example.com:8443")
     * for displaying clear, honest target host information in security modals.
     */
    fun extractDisplayHost(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return ""
        return try {
            val uri = Uri.parse(trimmed)
            val host = uri.host?.lowercase()?.trim()
            val port = uri.port
            if (!host.isNullOrEmpty()) {
                if (port != -1 && port != 80 && port != 443) {
                    "$host:$port"
                } else {
                    host
                }
            } else {
                trimmed.removePrefix("http://").removePrefix("https://").substringBefore('/')
            }
        } catch (_: Exception) {
            trimmed.removePrefix("http://").removePrefix("https://").substringBefore('/')
        }
    }

    private val SEARCH_ENGINE_DOMAINS = setOf(
        "google.com",
        "google.ru",
        "google.co.uk",
        "google.de",
        "google.fr",
        "google.it",
        "google.es",
        "google.ca",
        "google.com.br",
        "google.com.au",
        "google.co.jp",
        "google.co.in",
        "yandex.ru",
        "yandex.com",
        "yandex.by",
        "yandex.kz",
        "yandex.uz",
        "ya.ru",
        "bing.com",
        "duckduckgo.com",
        "ecosia.org",
        "baidu.com",
        "yahoo.com",
        "qwant.com",
        "brave.com",
        "startpage.com",
    )

    private val OAUTH_AUTH_HOSTS = setOf(
        "accounts.google.com",
        "oauth.vk.com",
        "passport.yandex.ru",
        "passport.yandex.com",
        "appleid.apple.com",
        "login.microsoftonline.com",
        "github.com",
        "id.rambler.ru",
        "auth.mail.ru",
        "connect.facebook.net",
        "www.facebook.com",
    )

    fun isSearchEngineHost(host: String): Boolean {
        if (host.isBlank()) return false
        val lower = host.lowercase().removePrefix("www.")
        return SEARCH_ENGINE_DOMAINS.any { lower == it || lower.endsWith(".$it") }
    }

    fun isAuthOrSecurityHost(host: String): Boolean {
        if (host.isBlank()) return false
        val lower = host.lowercase().removePrefix("www.")
        if (AdBlockerEngine.isAllowlistedAuthDomain(lower)) {
            return true
        }
        return OAUTH_AUTH_HOSTS.any { lower == it || lower.endsWith(".$it") }
    }

    private val TWO_PART_TLDS = setOf(
        "co.uk", "org.uk", "gov.uk", "ac.uk",
        "com.ru", "net.ru", "org.ru", "pp.ru", "spb.ru", "msk.ru",
        "com.ua", "net.ua", "org.ua",
        "co.jp", "ne.jp", "ac.jp",
        "com.br", "com.au", "net.au", "org.au", "co.nz", "co.za", "com.tr",
    )

    fun getBaseDomain(host: String): String {
        val cleanHost = host.lowercase().removePrefix("www.").trim()
        if (cleanHost.isEmpty()) return ""
        val parts = cleanHost.split('.')
        if (parts.size <= 2) return cleanHost
        if (parts.all { it.all { c -> c.isDigit() } }) return cleanHost

        val lastTwo = "${parts[parts.size - 2]}.${parts.last()}"
        val isTwoPartTld = lastTwo in TWO_PART_TLDS
        val takeParts = if (isTwoPartTld && parts.size >= 3) 3 else 2
        return parts.takeLast(takeParts).joinToString(".")
    }

    fun isSameSite(url1: String, url2: String): Boolean {
        val host1 = extractHost(url1).removePrefix("www.")
        val host2 = extractHost(url2).removePrefix("www.")
        if (host1.isEmpty() || host2.isEmpty()) return false
        if (host1 == host2) return true
        if (host1.endsWith(".$host2") || host2.endsWith(".$host1")) return true

        val base1 = getBaseDomain(host1)
        val base2 = getBaseDomain(host2)
        if (base1.isNotEmpty() && base1 == base2) return true

        // Treat Google properties & Google Analytics as same-site in first-party Google contexts
        if ((isGoogleDomain(base1) && host2 == "google-analytics.com") ||
            (isGoogleDomain(base2) && host1 == "google-analytics.com")
        ) {
            return true
        }

        // Treat Yandex national domains (ya.ru, yandex.ru, yandex.com, etc.) as same-site
        if (isYandexDomain(base1) && isYandexDomain(base2)) {
            return true
        }

        return false
    }

    private fun isGoogleDomain(domain: String): Boolean {
        return domain == "google.com" || domain.startsWith("google.") || domain == "google-analytics.com"
    }

    private fun isYandexDomain(domain: String): Boolean {
        return domain == "ya.ru" || domain == "yandex.ru" || domain == "yandex.com" ||
            domain == "yandex.by" || domain == "yandex.kz" || domain == "yandex.uz"
    }
}
