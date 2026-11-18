package com.jestk.browser.ui.viewmodel

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Test

class DownloadFailureTest {
    private var state = BrowserUiState()

    private val coordinator =
        DownloadCoordinator(
            downloadRepository = null,
            downloadManager = null,
            updateUiState = { transform -> state = transform(state) },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            ioDispatcher = Dispatchers.Unconfined,
        )

    @Test
    fun failureIsShownAndDismissed() {
        assertThat(state.downloadFailure).isNull()

        coordinator.onDownloadFailed("report.pdf", "disk full")

        assertThat(state.downloadFailure?.fileName).isEqualTo("report.pdf")
        assertThat(state.downloadFailure?.message).isEqualTo("disk full")

        coordinator.dismissDownloadFailure()

        assertThat(state.downloadFailure).isNull()
    }

    @Test
    fun newFailureGetsNewIdSoThePillRestartsItsTimer() {
        coordinator.onDownloadFailed(null, "first")
        val firstId = state.downloadFailure!!.id

        coordinator.onDownloadFailed(null, "second")

        assertThat(state.downloadFailure!!.message).isEqualTo("second")
        assertThat(state.downloadFailure!!.id).isNotEqualTo(firstId)
    }

    @Test
    fun failureDoesNotTouchTheDownloadSnackbar() {
        coordinator.onDownloadFailed(null, "")

        assertThat(state.showDownloadSnackbar).isFalse()
        assertThat(state.lastDownloadNotification).isNull()
    }
}
