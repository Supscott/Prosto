package com.jestk.browser.ui.viewmodel

import com.jestk.browser.domain.model.SearchEngine

/** What the address bar input turns into: a URL to load and the shortcut engine it used, if any. */
internal data class ResolvedUrlInput(val url: String, val shortcutEngine: SearchEngine? = null)

private val SHORTCUT_QUERY = Regex("""^([a-zA-Z0-9_-]+)\s+(.+)$""")

/**
 * Turns typed input into a URL: blank and `about:blank` mean Home, web URLs load as they are,
 * host-like input gets `https://`, `<shortcut> <query>` searches with that engine and anything
 * else searches with [defaultEngine].
 */
internal fun resolveUrlInput(
    rawUrl: String,
    engines: List<SearchEngine>,
    defaultEngine: SearchEngine,
): ResolvedUrlInput {
    val trimmed = rawUrl.trim()
    return when {
        trimmed.isEmpty() || trimmed == "about:blank" -> ResolvedUrlInput("")
        trimmed.startsWith("http://") || trimmed.startsWith("https://") -> ResolvedUrlInput(trimmed)
        looksLikeUrlInput(trimmed) -> ResolvedUrlInput("https://$trimmed")
        else -> {
            val shortcutMatch = SHORTCUT_QUERY.find(trimmed)
            val shortcutEngine = shortcutMatch?.let { match ->
                val prefix = match.groupValues[1]
                engines.firstOrNull { it.shortcut?.equals(prefix, ignoreCase = true) == true }
            }
            if (shortcutMatch != null && shortcutEngine != null) {
                ResolvedUrlInput(shortcutEngine.buildSearchUrl(shortcutMatch.groupValues[2].trim()), shortcutEngine)
            } else {
                ResolvedUrlInput(defaultEngine.buildSearchUrl(trimmed))
            }
        }
    }
}
