package com.jestk.browser.ui.viewmodel

import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.DownloadItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.junit.Test

class DuplicateDownloadCoordinatorTest {
    private var state = BrowserUiState()
    private val coordinator =
        DownloadCoordinator(
            downloadRepository = null,
            downloadManager = null,
            updateUiState = { transform -> state = transform(state) },
            scope = CoroutineScope(Job() + Dispatchers.Unconfined),
            ioDispatcher = Dispatchers.Unconfined,
        )
    private val existing = DownloadItem(id = 7, url = "https://example.com/a.pdf", fileName = "a.pdf")

    @Test
    fun duplicate_putsPromptInState_withoutEnqueuing() {
        var enqueued = 0
        coordinator.onDuplicateDownload(existing) { enqueued++ }

        assertThat(state.duplicateDownloadPrompt).isEqualTo(existing)
        assertThat(enqueued).isEqualTo(0)
    }

    @Test
    fun downloadAgain_runsStoredRequestOnce_andClearsPrompt() {
        var enqueued = 0
        coordinator.onDuplicateDownload(existing) { enqueued++ }

        coordinator.confirmDuplicateDownload()
        coordinator.confirmDuplicateDownload()

        assertThat(enqueued).isEqualTo(1)
        assertThat(state.duplicateDownloadPrompt).isNull()
    }

    @Test
    fun cancel_clearsPrompt_andNeverEnqueues() {
        var enqueued = 0
        coordinator.onDuplicateDownload(existing) { enqueued++ }

        coordinator.dismissDuplicateDownload()
        coordinator.confirmDuplicateDownload()

        assertThat(enqueued).isEqualTo(0)
        assertThat(state.duplicateDownloadPrompt).isNull()
    }
}
