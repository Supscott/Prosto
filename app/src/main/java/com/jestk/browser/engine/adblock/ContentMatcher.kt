package com.jestk.browser.engine.adblock

/** Policy-free view of the native engine; JVM tests use a fake. */
interface ContentMatcher {
    fun check(url: String, sourceUrl: String, type: RequestType): MatchResult
    fun redirectFor(url: String, sourceUrl: String, type: RequestType): String?
    fun cosmeticResources(url: String): CosmeticResources
    fun hiddenClassIdSelectors(classes: List<String>, ids: List<String>, exceptions: Set<String>): List<String>
    fun close()
}
