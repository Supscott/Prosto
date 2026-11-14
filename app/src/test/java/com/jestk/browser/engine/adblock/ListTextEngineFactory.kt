package com.jestk.browser.engine.adblock

import java.net.URI
import java.util.concurrent.atomic.AtomicInteger

/**
 * Fake engines that "compile" the list text: a URL is blocked when some list has a `||host^` line
 * for its host. The serialized blob is the joined text, so restore can rebuild the same matcher.
 */
internal class ListTextEngineFactory : EngineFactory {
    val compiles = AtomicInteger()
    val matchers = mutableListOf<TextMatcher>()

    override fun compile(lists: List<String>, resourcesJson: String?): CompiledEngine? {
        compiles.incrementAndGet()
        val text = lists.joinToString("\n")
        return CompiledEngine(TextMatcher(text).also { synchronized(matchers) { matchers += it } }, text.toByteArray())
    }

    override fun restore(bytes: ByteArray, resourcesJson: String?): ContentMatcher =
        TextMatcher(String(bytes)).also { synchronized(matchers) { matchers += it } }

    class TextMatcher(private val text: String) : ContentMatcher {
        @Volatile
        var closed = false
            private set

        override fun check(url: String, sourceUrl: String, type: RequestType): MatchResult {
            if (closed) return MatchResult.ALLOW
            val host = runCatching { URI(url).host }.getOrNull() ?: return MatchResult.ALLOW
            return if (text.contains("||$host^")) MatchResult(MatchResult.BLOCK) else MatchResult.ALLOW
        }

        override fun redirectFor(url: String, sourceUrl: String, type: RequestType): String? = null

        override fun cosmeticResources(url: String): CosmeticResources = CosmeticResources.EMPTY

        override fun hiddenClassIdSelectors(classes: List<String>, ids: List<String>, exceptions: Set<String>): List<String> =
            emptyList()

        override fun close() {
            closed = true
        }
    }
}
