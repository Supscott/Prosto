package com.jestk.browser.engine.adblock

internal class NativeContentMatcher(private val engine: NativeEngineHandle) : ContentMatcher {
    override fun check(url: String, sourceUrl: String, type: RequestType) = engine.check(url, sourceUrl, type)

    override fun redirectFor(url: String, sourceUrl: String, type: RequestType) =
        engine.redirectFor(url, sourceUrl, type)

    override fun cosmeticResources(url: String) = engine.cosmeticResources(url)

    override fun hiddenClassIdSelectors(classes: List<String>, ids: List<String>, exceptions: Set<String>) =
        engine.hiddenClassIdSelectors(classes, ids, exceptions)

    override fun close() = engine.close()
}
