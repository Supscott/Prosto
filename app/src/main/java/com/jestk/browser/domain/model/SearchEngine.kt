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
            "google" -> lower.contains("google.com") || lower.contains(".google.") || lower.contains("://google.")
            "duckduckgo" -> lower.contains("duckduckgo.com")
            "brave" -> lower.contains("search.brave.com") || lower.contains("brave.com")
            "kagi" -> lower.contains("kagi.com")
            "ecosia" -> lower.contains("ecosia.org")
            "startpage" -> lower.contains("startpage.com")
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
            "google" -> {
                if (!((lower.contains("google.") || lower.contains("google.com")) &&
                    lower.contains("/search") &&
                    (lower.contains("?q=") || lower.contains("&q=")))) return null
            }
            "duckduckgo" -> {
                if (!(lower.contains("duckduckgo.com") && (lower.contains("?q=") || lower.contains("&q=")))) return null
            }
            "brave" -> {
                if (!(lower.contains("search.brave.com") && (lower.contains("?q=") || lower.contains("&q=")))) return null
            }
            "kagi" -> {
                if (!(lower.contains("kagi.com") && lower.contains("/search") && (lower.contains("?q=") || lower.contains("&q=")))) return null
            }
            "ecosia" -> {
                if (!(lower.contains("ecosia.org") && (lower.contains("?q=") || lower.contains("&q=")))) return null
            }
            "startpage" -> {
                if (!lower.contains("startpage.com")) return null
                val regex = Regex("""[?&](?:query|q)=([^&#]*)""", RegexOption.IGNORE_CASE)
                val match = regex.find(url) ?: return null
                val rawQuery = match.groupValues[1]
                if (rawQuery.isBlank()) return null
                return try {
                    java.net.URLDecoder.decode(rawQuery.replace("+", " "), "UTF-8").trim()
                } catch (_: Exception) {
                    rawQuery.replace("+", " ").trim()
                }.takeIf { it.isNotBlank() }
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
        val GOOGLE = SearchEngine(
            id = "google",
            displayName = "Google",
            searchUrlTemplate = "https://www.google.com/search?q=%s",
            iconUrl = "https://www.google.com/s2/favicons?sz=128&domain=google.com",
            shortcut = "g",
            orderPosition = 0,
            isBuiltIn = true
        )

        val DUCKDUCKGO = SearchEngine(
            id = "duckduckgo",
            displayName = "DuckDuckGo",
            searchUrlTemplate = "https://duckduckgo.com/?q=%s",
            iconUrl = "https://www.google.com/s2/favicons?sz=128&domain=duckduckgo.com",
            shortcut = "d",
            orderPosition = 1,
            isBuiltIn = true
        )

        val BRAVE = SearchEngine(
            id = "brave",
            displayName = "Brave",
            searchUrlTemplate = "https://search.brave.com/search?q=%s",
            iconUrl = "https://www.google.com/s2/favicons?sz=128&domain=brave.com",
            shortcut = "b",
            orderPosition = 2,
            isBuiltIn = true
        )

        val KAGI = SearchEngine(
            id = "kagi",
            displayName = "Kagi",
            searchUrlTemplate = "https://kagi.com/search?q=%s",
            iconUrl = "https://www.google.com/s2/favicons?sz=128&domain=kagi.com",
            shortcut = "k",
            orderPosition = 3,
            isBuiltIn = true
        )

        val ECOSIA = SearchEngine(
            id = "ecosia",
            displayName = "Ecosia",
            searchUrlTemplate = "https://www.ecosia.org/search?q=%s",
            iconUrl = "https://www.google.com/s2/favicons?sz=128&domain=ecosia.org",
            shortcut = "e",
            orderPosition = 4,
            isBuiltIn = true
        )

        val STARTPAGE = SearchEngine(
            id = "startpage",
            displayName = "Startpage",
            searchUrlTemplate = "https://www.startpage.com/sp/search?query=%s",
            iconUrl = "https://www.google.com/s2/favicons?sz=128&domain=startpage.com",
            shortcut = "s",
            orderPosition = 5,
            isBuiltIn = true
        )

        val DEFAULT_ENGINES = listOf(GOOGLE, DUCKDUCKGO, BRAVE, KAGI, ECOSIA, STARTPAGE)

        val entries: List<SearchEngine>
            get() = DEFAULT_ENGINES

        fun valueOf(name: String): SearchEngine {
            val upper = name.uppercase()
            return when (upper) {
                "GOOGLE" -> GOOGLE
                "DUCKDUCKGO" -> DUCKDUCKGO
                "BRAVE" -> BRAVE
                "KAGI" -> KAGI
                "ECOSIA" -> ECOSIA
                "STARTPAGE" -> STARTPAGE
                "BING" -> DUCKDUCKGO
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
