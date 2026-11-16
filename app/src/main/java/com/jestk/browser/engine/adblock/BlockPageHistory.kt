package com.jestk.browser.engine.adblock

import android.webkit.WebView

/**
 * A block page is a real WebView history entry, and WebView cannot delete entries. Instead Back
 * steps over them: it goes to the nearest earlier entry that is not a block page, and Back is only
 * available when such an entry exists.
 */
internal object BlockPageHistory {
    /** Steps back (negative) from [currentIndex] to the nearest non-block-page entry of [titles], or null. */
    fun backSteps(titles: List<String?>, currentIndex: Int): Int? {
        for (index in minOf(currentIndex, titles.size) - 1 downTo 0) {
            if (!BlockPage.isBlockPageTitle(titles[index])) return index - currentIndex
        }
        return null
    }
}

private fun WebView.backSteps(): Int? {
    if (!canGoBack()) return null
    val list = copyBackForwardList()
    return BlockPageHistory.backSteps(List(list.size) { list.getItemAtIndex(it)?.title }, list.currentIndex)
}

/** Like [WebView.canGoBack], but ignoring block page entries. Main thread only. */
internal fun WebView.canGoBackPastBlockPages(): Boolean = backSteps() != null

/** Like [WebView.goBack], but stepping over block page entries. Main thread only. */
internal fun WebView.goBackPastBlockPages() {
    backSteps()?.let { goBackOrForward(it) }
}
