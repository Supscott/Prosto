package com.jestk.browser.domain.model

import java.net.URLEncoder

data class SearchEngine(
    val id: String,
    val displayName: String,
    val searchUrlTemplate: String,
    val iconUrl: String,
    val shortcut: String? = null,
    val orderPosition: Int = 0,
    val isBuiltIn: Boolean = false
) {
    val iconAssetUrl: String
        get() = iconUrl

    val name: String
        get() = id.uppercase()

    fun buildSearchUrl(query: String): String {
        val encoded = try {
            URLEncoder.encode(query, "UTF-8")
        } catch (e: Exception) {
            query
        }
        return if (searchUrlTemplate.contains("%s")) {
            String.format(searchUrlTemplate, encoded)
        } else {
            "$searchUrlTemplate$encoded"
        }
    }

    fun matchesUrl(url: String): Boolean {
        val lower = url.lowercase()
        return when (id.lowercase()) {
            "duckduckgo" -> lower.contains("duckduckgo.com")
            "google" -> lower.contains("google.com") || lower.contains(".google.") || lower.contains("://google.")
            "bing" -> lower.contains("bing.com")
            else -> {
                try {
                    val cleanUrl = searchUrlTemplate.replace("%s", "placeholder")
                    val uri = java.net.URI(cleanUrl)
                    val host = uri.host?.lowercase().orEmpty().removePrefix("www.")
                    host.isNotBlank() && lower.contains(host)
                } catch (_: Exception) {
                    val withoutScheme = searchUrlTemplate.substringAfter("://")
                    val host = withoutScheme.substringBefore("/").substringBefore("?").removePrefix("www.").lowercase()
                    host.isNotBlank() && lower.contains(host)
                }
            }
        }
    }

    fun extractQuery(url: String): String? {
        if (!matchesUrl(url)) return null
        val lower = url.lowercase()
        when (id.lowercase()) {
            "duckduckgo" -> {
                if (!(lower.contains("duckduckgo.com") && (lower.contains("?q=") || lower.contains("&q=")))) return null
            }
            "google" -> {
                if (!((lower.contains("google.") || lower.contains("google.com")) &&
                    lower.contains("/search") &&
                    (lower.contains("?q=") || lower.contains("&q=")))) return null
            }
            "bing" -> {
                if (!(lower.contains("bing.com") && lower.contains("/search") && (lower.contains("?q=") || lower.contains("&q=")))) return null
            }
            else -> {
                val param = extractQueryParameterName(searchUrlTemplate) ?: "q"
                val regex = Regex("""[?&]$param=([^&#]*)""", RegexOption.IGNORE_CASE)
                val match = regex.find(url) ?: return null
                val rawQuery = match.groupValues[1]
                if (rawQuery.isBlank()) return null
                return try {
                    java.net.URLDecoder.decode(rawQuery.replace("+", " "), "UTF-8").trim()
                } catch (_: Exception) {
                    rawQuery.replace("+", " ").trim()
                }.takeIf { it.isNotBlank() }
            }
        }

        val regex = Regex("""[?&]q=([^&#]*)""", RegexOption.IGNORE_CASE)
        val match = regex.find(url) ?: return null
        val rawQuery = match.groupValues[1]
        if (rawQuery.isBlank()) return null
        return try {
            java.net.URLDecoder.decode(rawQuery.replace("+", " "), "UTF-8").trim()
        } catch (_: Exception) {
            rawQuery.replace("+", " ").trim()
        }.takeIf { it.isNotBlank() }
    }

    companion object {
        @JvmField
        val DUCKDUCKGO = SearchEngine(
            id = "duckduckgo",
            displayName = "DuckDuckGo",
            searchUrlTemplate = "https://duckduckgo.com/?q=%s",
            iconUrl = "file:///android_asset/icons/duckduckgo.svg",
            shortcut = "d",
            orderPosition = 0,
            isBuiltIn = true
        )

        @JvmField
        val GOOGLE = SearchEngine(
            id = "google",
            displayName = "Google",
            searchUrlTemplate = "https://www.google.com/search?q=%s",
            iconUrl = "file:///android_asset/icons/google.svg",
            shortcut = "g",
            orderPosition = 1,
            isBuiltIn = true
        )

        @JvmField
        val BING = SearchEngine(
            id = "bing",
            displayName = "Bing",
            searchUrlTemplate = "https://www.bing.com/search?q=%s",
            iconUrl = "file:///android_asset/icons/bing.svg",
            shortcut = "b",
            orderPosition = 2,
            isBuiltIn = true
        )

        @JvmField
        val DEFAULT_ENGINES = listOf(DUCKDUCKGO, GOOGLE, BING)

        @JvmStatic
        val entries: List<SearchEngine>
            get() = DEFAULT_ENGINES

        @JvmStatic
        fun values(): Array<SearchEngine> = DEFAULT_ENGINES.toTypedArray()

        @JvmStatic
        fun valueOf(name: String): SearchEngine {
            val upper = name.uppercase()
            return when (upper) {
                "DUCKDUCKGO" -> DUCKDUCKGO
                "GOOGLE" -> GOOGLE
                "BING" -> BING
                else -> {
                    DEFAULT_ENGINES.firstOrNull { it.id.equals(name, ignoreCase = true) || it.displayName.equals(name, ignoreCase = true) }
                        ?: SearchEngine(
                            id = name.lowercase(),
                            displayName = name,
                            searchUrlTemplate = "",
                            iconUrl = "",
                            shortcut = null
                        )
                }
            }
        }

        @JvmStatic
        @JvmOverloads
        fun findMatchingEngineAndQuery(url: String?, engines: List<SearchEngine> = DEFAULT_ENGINES): Pair<SearchEngine, String>? {
            if (url.isNullOrBlank()) return null
            for (engine in engines) {
                val query = engine.extractQuery(url)
                if (query != null) {
                    return Pair(engine, query)
                }
            }
            return null
        }

        private fun extractQueryParameterName(template: String): String? {
            val idx = template.indexOf("%s")
            if (idx <= 0) return null
            val prefix = template.substring(0, idx)
            val eqIdx = prefix.lastIndexOf('=')
            if (eqIdx == -1) return null
            val paramStart = maxOf(prefix.lastIndexOf('?'), prefix.lastIndexOf('&'))
            if (paramStart == -1 || paramStart >= eqIdx) return null
            return prefix.substring(paramStart + 1, eqIdx).trim()
        }
    }
}
