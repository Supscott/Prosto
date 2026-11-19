package com.jestk.browser.ui.app

import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.DomainGroupSuggestion
import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.domain.model.Workspace
import com.jestk.browser.ui.viewmodel.BrowserUiState
import org.junit.Test

class DomainGroupChipVisibilityTest {
    private val suggestion = DomainGroupSuggestion(
        workspaceId = Workspace.DEFAULT_PERSONAL_ID,
        domain = "youtube.com",
        folderTitle = "Youtube",
        tabIds = listOf("1", "2", "3"),
    )

    private fun state(activeTabId: String?) =
        BrowserUiState(domainGroupSuggestion = suggestion, activeTabId = activeTabId)

    @Test
    fun shownWhenTheOpenTabBelongsToTheGroup() {
        assertThat(state(activeTabId = "2").shouldShowDomainGroupChip(isBottomBarVisible = true)).isTrue()
    }

    @Test
    fun hiddenWhenTheOpenTabIsAnotherSite() {
        assertThat(state(activeTabId = "other").shouldShowDomainGroupChip(isBottomBarVisible = true)).isFalse()
        assertThat(state(activeTabId = null).shouldShowDomainGroupChip(isBottomBarVisible = true)).isFalse()
    }

    @Test
    fun hiddenWithoutTheDockOrUnderAnOverlay() {
        assertThat(state(activeTabId = "1").shouldShowDomainGroupChip(isBottomBarVisible = false)).isFalse()
        assertThat(
            state(activeTabId = "1").copy(activeOverlay = OverlayType.TAB_GRID).shouldShowDomainGroupChip(isBottomBarVisible = true),
        ).isFalse()
    }

    @Test
    fun hiddenWhileTheMenuIsOpen() {
        assertThat(state(activeTabId = "1").copy(isMenuExpanded = true).shouldShowDomainGroupChip(isBottomBarVisible = true)).isFalse()
    }

    @Test
    fun hiddenWithoutASuggestion() {
        assertThat(BrowserUiState(activeTabId = "1").shouldShowDomainGroupChip(isBottomBarVisible = true)).isFalse()
    }
}
