package com.jestk.browser.engine

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceResponse
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap

object AdBlockerEngine {

    private val blockedHosts = ConcurrentHashMap.newKeySet<String>()
    @Volatile
    private var isInitialized = false

    fun initialize(context: Context) {
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return
            try {
                context.assets.open("adblock_hosts.txt").use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).useLines { lines ->
                        lines.forEach { line ->
                            val trimmed = line.trim().lowercase()
                            if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                                blockedHosts.add(trimmed)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Graceful fallback to built-in essential hosts
                val defaultHosts = listOf(
                    "doubleclick.net", "googleadservices.com", "googlesyndication.com",
                    "google-analytics.com", "criteo.com", "taboola.com", "outbrain.com"
                )
                blockedHosts.addAll(defaultHosts)
            }
            isInitialized = true
        }
    }

    fun shouldBlock(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val host = try {
            Uri.parse(url).host?.lowercase()
        } catch (_: Exception) {
            null
        } ?: return false

        if (blockedHosts.contains(host)) return true

        // Check subdomain chains: e.g. "ads.sub.example.com" -> "sub.example.com" -> "example.com"
        var currentHost = host
        while (currentHost.contains('.')) {
            val nextDotIndex = currentHost.indexOf('.')
            if (nextDotIndex == -1 || nextDotIndex == currentHost.lastIndex) break
            currentHost = currentHost.substring(nextDotIndex + 1)
            if (blockedHosts.contains(currentHost)) {
                return true
            }
        }

        return false
    }

    fun createBlockedResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "UTF-8",
            ByteArrayInputStream(ByteArray(0))
        )
    }

    fun isReady(): Boolean = isInitialized

    fun getRulesCount(): Int = blockedHosts.size
}
