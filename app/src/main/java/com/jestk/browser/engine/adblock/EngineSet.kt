package com.jestk.browser.engine.adblock

/**
 * One published set of compiled engines. [rulesCount] is the number of rules in the ads and privacy
 * lists. [popup] holds the lists' `$popup` rules rewritten for `DOCUMENT` checks ([PopupRules]); it
 * is optional, a set without it still blocks everything else.
 */
internal class EngineSet(
    val ads: ContentMatcher,
    val privacy: ContentMatcher,
    val rulesCount: Int,
    val popup: ContentMatcher? = null,
) {
    fun close() {
        ads.close()
        privacy.close()
        popup?.close()
    }
}
