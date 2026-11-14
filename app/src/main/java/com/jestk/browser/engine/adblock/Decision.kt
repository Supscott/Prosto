package com.jestk.browser.engine.adblock

/**
 * Outcome of `AdBlockerEngine.decide`. [redirectDataUrl] is a `data:<mime>;base64,<payload>` URL
 * from a uBO redirect resource, set only when the matching rule redirects instead of just blocking.
 */
data class Decision(
    val block: Boolean,
    val isTracker: Boolean,
    val redirectDataUrl: String?,
) {
    companion object {
        val ALLOW = Decision(block = false, isTracker = false, redirectDataUrl = null)
    }
}

/** Runs the privacy engine first (a block there is a tracker), then the ads engine. */
internal object EngineDecider {
    fun decide(
        set: EngineSet,
        url: String,
        sourceUrl: String,
        type: RequestType,
        adsEnabled: Boolean,
        trackersEnabled: Boolean,
    ): Decision {
        if (trackersEnabled) {
            blockDecision(set.privacy, url, sourceUrl, type, isTracker = true)?.let { return it }
        }
        if (adsEnabled) {
            blockDecision(set.ads, url, sourceUrl, type, isTracker = false)?.let { return it }
        }
        return Decision.ALLOW
    }

    /** True when [result] blocks: an exception cancels a block unless the filter is `important`. */
    fun blocks(result: MatchResult): Boolean = result.shouldBlock && (!result.hasException || result.isImportant)

    private fun blockDecision(
        engine: ContentMatcher,
        url: String,
        sourceUrl: String,
        type: RequestType,
        isTracker: Boolean,
    ): Decision? {
        val result = engine.check(url, sourceUrl, type)
        if (!blocks(result)) return null
        val redirect = if (result.hasRedirect) engine.redirectFor(url, sourceUrl, type) else null
        return Decision(block = true, isTracker = isTracker, redirectDataUrl = redirect)
    }
}
