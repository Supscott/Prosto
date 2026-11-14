package com.jestk.browser.engine.adblock

/** One published pair of compiled engines. [rulesCount] is the number of rules in both lists. */
internal class EngineSet(
    val ads: ContentMatcher,
    val privacy: ContentMatcher,
    val rulesCount: Int,
) {
    fun close() {
        ads.close()
        privacy.close()
    }
}
