package com.jestk.browser.ui.viewmodel

import com.jestk.browser.domain.model.DownloadConfirmRequest

/**
 * Holds the downloads waiting for the "Download file?" answer. One card shows at a time; later
 * requests wait behind it in arrival order and show once the earlier one is answered.
 */
internal class DownloadConfirmQueue(
    private val isEnabled: () -> Boolean,
    private val updateUiState: ((BrowserUiState) -> BrowserUiState) -> Unit,
) {
    private class Pending(val request: DownloadConfirmRequest, val proceed: () -> Unit)

    private val lock = Any()
    private val waiting = ArrayDeque<Pending>()
    private var current: Pending? = null

    /** With the setting off the download starts at once; otherwise it is queued behind the open card. */
    fun request(request: DownloadConfirmRequest, proceed: () -> Unit) {
        if (!isEnabled()) {
            proceed()
            return
        }
        synchronized(lock) {
            waiting.addLast(Pending(request, proceed))
            showNextLocked()
        }
    }

    /** Download pressed: starts the shown download, then shows the next one. */
    fun confirm() = answer(start = true)

    /** Cancel or dismissed: drops the shown download, then shows the next one. */
    fun dismiss() = answer(start = false)

    private fun answer(start: Boolean) {
        val answered = synchronized(lock) {
            val answered = current
            current = null
            showNextLocked()
            answered
        }
        if (start) answered?.proceed?.invoke()
    }

    private fun showNextLocked() {
        if (current != null) return
        val next = waiting.removeFirstOrNull()
        current = next
        updateUiState { it.copy(downloadConfirmPrompt = next?.request) }
    }
}
