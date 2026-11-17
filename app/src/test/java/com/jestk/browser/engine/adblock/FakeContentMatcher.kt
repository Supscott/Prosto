package com.jestk.browser.engine.adblock

/** Rule-map fake: a URL containing a key gets the mapped flags. Records every call. */
class FakeContentMatcher(
    private val blocked: Map<String, Int> = emptyMap(),
    private val redirects: Map<String, String> = emptyMap(),
    private val cosmeticByHost: Map<String, CosmeticResources> = emptyMap(),
) : ContentMatcher {
    data class Call(val url: String, val sourceUrl: String, val type: RequestType)

    /** Synchronized: EngineSwapConcurrencyTest calls check() from several threads. */
    val calls: MutableList<Call> = java.util.Collections.synchronizedList(mutableListOf())
    var closed = false
        private set

    override fun check(url: String, sourceUrl: String, type: RequestType): MatchResult {
        calls += Call(url, sourceUrl, type)
        if (closed) return MatchResult.ALLOW
        return MatchResult(blocked.entries.firstOrNull { url.contains(it.key) }?.value ?: 0)
    }

    override fun redirectFor(url: String, sourceUrl: String, type: RequestType): String? =
        if (closed) null else redirects.entries.firstOrNull { url.contains(it.key) }?.value

    override fun cosmeticResources(url: String): CosmeticResources {
        if (closed) return CosmeticResources.EMPTY
        return cosmeticByHost.entries.firstOrNull { url.contains(it.key) }?.value ?: CosmeticResources.EMPTY
    }

    override fun hiddenClassIdSelectors(
        classes: List<String>,
        ids: List<String>,
        exceptions: Set<String>,
    ): List<String> = if (closed) {
        emptyList()
    } else {
        classes.filter { it !in exceptions }.map { ".$it" } + ids.filter { it !in exceptions }.map { "#$it" }
    }

    override fun close() {
        closed = true
    }
}
