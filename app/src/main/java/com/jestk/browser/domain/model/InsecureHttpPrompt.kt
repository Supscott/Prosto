package com.jestk.browser.domain.model

/**
 * Target destination where the insecure HTTP navigation is intended to take place.
 */
enum class InsecureNavigationTarget {
    ACTIVE_TAB,
    NEW_TAB,
    BACKGROUND_TAB
}

/**
 * Domain representation of a warning prompt before navigating to an insecure cleartext HTTP site.
 */
data class InsecureHttpPrompt(
    val url: String,
    val tabId: String? = null,
    val target: InsecureNavigationTarget = InsecureNavigationTarget.ACTIVE_TAB,
)
