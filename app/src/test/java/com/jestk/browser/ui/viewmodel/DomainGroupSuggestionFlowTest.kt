package com.jestk.browser.ui.viewmodel

import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.DomainGroupKey
import com.jestk.browser.domain.model.Tab
import com.jestk.browser.domain.model.Workspace
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DomainGroupSuggestionFlowTest {
    private fun tab(id: String, url: String) = Tab(id = id, url = url, lastActiveTimestamp = 1L)

    private val threeYoutubeTabs = listOf(
        tab("1", "https://youtube.com/a"),
        tab("2", "https://music.youtube.com/b"),
        tab("3", "https://www.youtube.com/c"),
    )

    @Test
    fun suggestionFollowsTheTabsAndTheDismissals() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val state = MutableStateFlow(BrowserUiState(tabs = threeYoutubeTabs))
        val job = backgroundScope.launchDomainGroupSuggestions(state, dispatcher)
        runCurrent()
        assertThat(state.value.domainGroupSuggestion?.domain).isEqualTo("youtube.com")
        assertThat(state.value.domainGroupSuggestion?.tabIds).containsExactly("1", "2", "3").inOrder()

        state.update { it.copy(domainGroupDismissals = mapOf(DomainGroupKey(Workspace.DEFAULT_PERSONAL_ID, "youtube.com") to 3)) }
        runCurrent()
        assertThat(state.value.domainGroupSuggestion).isNull()
        job.cancel()
    }

    @Test
    fun titleThemeAndNavigationChangesDoNotRecompute() = runTest {
        val state = MutableStateFlow(BrowserUiState(tabs = threeYoutubeTabs))
        val emitted = mutableListOf<Any?>()
        val job = backgroundScope.launch { state.domainGroupSuggestions().collect { emitted += it } }
        runCurrent()

        state.update { s -> s.copy(tabs = s.tabs.map { it.copy(title = "Video", themeColor = 1, canGoBack = true, snapshotPath = "x") }) }
        runCurrent()
        assertThat(emitted).hasSize(1)

        state.update { s -> s.copy(tabs = s.tabs.map { if (it.id == "3") it.copy(url = "https://other.org/") else it }) }
        runCurrent()
        assertThat(emitted).hasSize(2)
        assertThat(emitted.last()).isNull()
        job.cancel()
    }
}
