package com.jestk.browser.ui.viewmodel

import com.google.common.truth.Truth.assertThat
import com.jestk.browser.domain.model.DownloadConfirmRequest
import com.jestk.browser.domain.model.DownloadItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.junit.Test

class DownloadConfirmCoordinatorTest {
    private var state = BrowserUiState()
    private var askBeforeDownload = true
    private val coordinator =
        DownloadCoordinator(
            downloadRepository = null,
            downloadManager = null,
            updateUiState = { transform -> state = transform(state) },
            scope = CoroutineScope(Job() + Dispatchers.Unconfined),
            ioDispatcher = Dispatchers.Unconfined,
            askBeforeDownload = { askBeforeDownload },
        )
    private val first = DownloadConfirmRequest("a.pdf", 1024L, "example.com")
    private val second = DownloadConfirmRequest("b.zip", null, null)

    @Test
    fun settingOff_proceedsAtOnce_andShowsNothing() {
        askBeforeDownload = false
        var started = 0

        coordinator.onConfirmDownload(first) { started++ }

        assertThat(started).isEqualTo(1)
        assertThat(state.downloadConfirmPrompt).isNull()
    }

    @Test
    fun settingOn_showsPrompt_andWaitsForTheAnswer() {
        var started = 0

        coordinator.onConfirmDownload(first) { started++ }

        assertThat(state.downloadConfirmPrompt).isEqualTo(first)
        assertThat(started).isEqualTo(0)
    }

    @Test
    fun confirm_startsTheDownloadOnce_andClearsPrompt() {
        var started = 0
        coordinator.onConfirmDownload(first) { started++ }

        coordinator.confirmDownloadPrompt()
        coordinator.confirmDownloadPrompt()

        assertThat(started).isEqualTo(1)
        assertThat(state.downloadConfirmPrompt).isNull()
    }

    @Test
    fun cancel_dropsTheDownload() {
        var started = 0
        coordinator.onConfirmDownload(first) { started++ }

        coordinator.dismissDownloadPrompt()
        coordinator.confirmDownloadPrompt()

        assertThat(started).isEqualTo(0)
        assertThat(state.downloadConfirmPrompt).isNull()
    }

    @Test
    fun secondRequest_queuesBehindTheFirst_inOrder() {
        val order = mutableListOf<String>()
        coordinator.onConfirmDownload(first) { order += "a" }
        coordinator.onConfirmDownload(second) { order += "b" }

        assertThat(state.downloadConfirmPrompt).isEqualTo(first)

        coordinator.confirmDownloadPrompt()
        assertThat(order).containsExactly("a")
        assertThat(state.downloadConfirmPrompt).isEqualTo(second)

        coordinator.confirmDownloadPrompt()
        assertThat(order).containsExactly("a", "b").inOrder()
        assertThat(state.downloadConfirmPrompt).isNull()
    }

    @Test
    fun cancelOfFirst_showsSecond_andDropsOnlyTheFirst() {
        val order = mutableListOf<String>()
        coordinator.onConfirmDownload(first) { order += "a" }
        coordinator.onConfirmDownload(second) { order += "b" }

        coordinator.dismissDownloadPrompt()
        assertThat(state.downloadConfirmPrompt).isEqualTo(second)
        coordinator.confirmDownloadPrompt()

        assertThat(order).containsExactly("b")
    }

    @Test
    fun duplicateCard_isIndependentOfTheConfirmCard() {
        val existing = DownloadItem(id = 7, url = "https://example.com/a.pdf", fileName = "a.pdf")

        coordinator.onDuplicateDownload(existing) {}

        assertThat(state.duplicateDownloadPrompt).isEqualTo(existing)
        assertThat(state.downloadConfirmPrompt).isNull()
    }
}
