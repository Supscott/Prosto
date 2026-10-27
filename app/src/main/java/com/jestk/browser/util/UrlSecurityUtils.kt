package com.jestk.browser.util

import android.net.Uri

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
        } catch (_: Throwable) {
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
        } catch (_: Throwable) {
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
        } catch (_: Throwable) {
            trimmed.removePrefix("http://").removePrefix("https://").substringBefore('/')
        }
    }
}
