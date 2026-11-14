package com.jestk.browser.engine.adblock

/** A freshly compiled engine plus its serialized form (null when serialization failed). */
internal class CompiledEngine(val matcher: ContentMatcher, val serialized: ByteArray?)

/** Builds engines from filter lists or from a cached blob; the native one is swapped for a fake in tests. */
internal interface EngineFactory {
    fun compile(lists: List<String>, resourcesJson: String?): CompiledEngine?
    fun restore(bytes: ByteArray, resourcesJson: String?): ContentMatcher?
}

internal object NativeEngineFactory : EngineFactory {
    override fun compile(lists: List<String>, resourcesJson: String?): CompiledEngine? =
        NativeEngineHandle.fromLists(lists, resourcesJson)?.let { CompiledEngine(NativeContentMatcher(it), it.serialize()) }

    override fun restore(bytes: ByteArray, resourcesJson: String?): ContentMatcher? =
        NativeEngineHandle.fromSerialized(bytes, resourcesJson)?.let { NativeContentMatcher(it) }
}

/** What the facade needs from the native side; tests inject a fake. */
internal class NativeBackend(
    val load: () -> Boolean,
    val version: () -> String,
    val factory: EngineFactory,
) {
    companion object {
        val DEFAULT = NativeBackend(AdblockNative::load, AdblockNative::nativeVersion, NativeEngineFactory)
    }
}
