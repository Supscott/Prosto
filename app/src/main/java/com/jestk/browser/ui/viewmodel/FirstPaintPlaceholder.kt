package com.jestk.browser.ui.viewmodel

/**
 * What the viewport keeps showing while the active tab's WebView has not painted its page yet
 * (Chrome-style paint holding), instead of an empty background.
 */
enum class FirstPaintPlaceholder {
    /** The navigation started from Home: Home stays until the page appears. */
    HOME,

    /** The tab's WebView was recreated (eviction, restore, crash): show its last snapshot. */
    SNAPSHOT,
}
