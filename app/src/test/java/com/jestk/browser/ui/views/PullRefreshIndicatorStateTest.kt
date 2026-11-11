package com.jestk.browser.ui.views

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PullRefreshIndicatorStateTest {
    private val state = PullRefreshIndicatorState()

    @Test
    fun ordinaryPageLoad_neverKeepsSpinner() {
        assertThat(state.onPageLoadingChanged(isLoading = true)).isFalse()
        assertThat(state.onPageLoadingChanged(isLoading = false)).isTrue()
    }

    @Test
    fun pull_keepsSpinnerUntilReloadStartsAndFinishes() {
        state.onPullRefresh()
        assertThat(state.onPageLoadingChanged(isLoading = false)).isFalse()
        assertThat(state.onPageLoadingChanged(isLoading = true)).isFalse()
        assertThat(state.onPageLoadingChanged(isLoading = false)).isTrue()
    }

    @Test
    fun pullWithoutReload_hidesOnTimeout() {
        state.onPullRefresh()
        assertThat(state.onPageLoadingChanged(isLoading = false)).isFalse()
        assertThat(state.onReloadStartTimeout()).isTrue()
    }

    @Test
    fun timeoutDuringReload_keepsSpinner() {
        state.onPullRefresh()
        state.onPageLoadingChanged(isLoading = true)
        assertThat(state.onReloadStartTimeout()).isFalse()
    }
}
