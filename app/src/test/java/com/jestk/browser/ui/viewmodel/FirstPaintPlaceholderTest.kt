package com.jestk.browser.ui.viewmodel

import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.Tab
import org.junit.Test

class FirstPaintPlaceholderTest {
    private val pageTab = Tab(id = "t1", url = "https://example.com/", title = "Example", snapshotPath = "/snap/t1.webp")

    private fun state(tab: Tab, awaiting: Set<String>, homeHold: String? = null) =
        BrowserUiState(
            tabs = listOf(tab),
            activeTabId = tab.id,
            currentUrl = tab.url,
            awaitingFirstPaintTabIds = awaiting,
            homeHoldTabId = homeHold,
        )

    @Test
    fun recreatedWebViewWithSnapshot_showsSnapshot() {
        assertThat(state(pageTab, awaiting = setOf("t1")).firstPaintPlaceholder)
            .isEqualTo(FirstPaintPlaceholder.SNAPSHOT)
    }

    @Test
    fun paintedWebView_showsNoPlaceholder() {
        assertThat(state(pageTab, awaiting = emptySet()).firstPaintPlaceholder).isNull()
    }

    @Test
    fun recreatedWebViewWithoutSnapshot_showsNoPlaceholder() {
        val tab = pageTab.copy(snapshotPath = null)
        assertThat(state(tab, awaiting = setOf("t1")).firstPaintPlaceholder).isNull()
    }

    @Test
    fun homeHold_winsOverStaleSnapshot() {
        val held = state(pageTab, awaiting = setOf("t1"), homeHold = "t1")
        assertThat(held.firstPaintPlaceholder).isEqualTo(FirstPaintPlaceholder.HOME)
        assertThat(held.isHomeContentVisible).isTrue()
    }

    @Test
    fun firstFrame_endsHomeHold() {
        val painted = state(pageTab, awaiting = setOf("t1"), homeHold = "t1").withAwaitingFirstPaint(emptySet())
        assertThat(painted.homeHoldTabId).isNull()
        assertThat(painted.isHomeContentVisible).isFalse()
    }

    @Test
    fun holdOfBackgroundTab_doesNotAffectActiveTab() {
        assertThat(state(pageTab, awaiting = setOf("other"), homeHold = "other").firstPaintPlaceholder).isNull()
    }
}
