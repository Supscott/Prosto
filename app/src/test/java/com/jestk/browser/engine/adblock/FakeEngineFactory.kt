package com.jestk.browser.engine.adblock

/** Counts compiles and restores; the "blob" is a fixed marker so a corrupt file is detectable. */
internal class FakeEngineFactory(
    private val failAttempt: Int = -1,
) : EngineFactory {
    private var attempts = 0
    var compiles = 0
        private set
    var restores = 0
        private set
    val threads = mutableListOf<Thread>()
    val matchers = mutableListOf<FakeContentMatcher>()

    override fun compile(lists: List<String>, resourcesJson: String?): CompiledEngine? {
        threads += Thread.currentThread()
        if (++attempts == failAttempt) return null
        compiles++
        val matcher = FakeContentMatcher().also { matchers += it }
        return CompiledEngine(matcher, BLOB)
    }

    override fun restore(bytes: ByteArray, resourcesJson: String?): ContentMatcher? {
        threads += Thread.currentThread()
        if (!bytes.contentEquals(BLOB)) return null
        restores++
        return FakeContentMatcher().also { matchers += it }
    }

    companion object {
        val BLOB = "FAKE-BLOB".toByteArray()
    }
}
