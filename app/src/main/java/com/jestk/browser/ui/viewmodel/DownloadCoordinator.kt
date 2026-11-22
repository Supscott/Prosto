package com.jestk.browser.ui.viewmodel

import android.util.Log
import com.jestk.browser.domain.model.DownloadConfirmRequest
import com.jestk.browser.domain.model.DownloadItem
import com.jestk.browser.domain.repository.DownloadRepository
import com.jestk.browser.engine.download.HoriDownloadManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "DownloadCoordinator"

/**
 * Coordinator for managing file downloads, notifications, snackbars, and progress updates.
 */
internal class DownloadCoordinator(
    private val downloadRepository: DownloadRepository?,
    private val downloadManager: HoriDownloadManager?,
    private val updateUiState: ((BrowserUiState) -> BrowserUiState) -> Unit,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    askBeforeDownload: () -> Boolean = { true },
) {
    private val failureCounter = AtomicLong()
    private var pendingDuplicate: (() -> Unit)? = null
    private val confirmQueue = DownloadConfirmQueue(askBeforeDownload, updateUiState)

    fun observeDatabaseFlows() {
        if (downloadRepository != null) {
            scope.launch(ioDispatcher) {
                var isFirstCollection = true
                var previousDownloads = emptyList<DownloadItem>()
                downloadRepository.getAllDownloads().collectLatest { downloads ->
                    if (isFirstCollection) {
                        isFirstCollection = false
                        previousDownloads = downloads
                        updateUiState { it.copy(downloads = downloads) }
                        return@collectLatest
                    }

                    val previousIds = previousDownloads.map { it.id }.toSet()
                    val newDownloads = downloads.filter { it.id !in previousIds }
                    previousDownloads = downloads

                    val latestNewDownload = newDownloads.maxByOrNull { it.timestamp }
                    if (latestNewDownload != null) {
                        updateUiState { current ->
                            current.copy(
                                downloads = downloads,
                                lastDownloadNotification = latestNewDownload,
                                showDownloadSnackbar = true,
                                activeDownloadIds = current.activeDownloadIds + latestNewDownload.id,
                            )
                        }
                    } else {
                        updateUiState { it.copy(downloads = downloads) }
                    }
                }
            }
        }

        if (downloadManager != null) {
            scope.launch(ioDispatcher) {
                downloadManager.activeDownloads.collectLatest { progressMap ->
                    updateUiState { current ->
                        current.copy(
                            downloadProgressMap = progressMap,
                            activeDownloadIds = progressMap.keys,
                        )
                    }
                }
            }

            scope.launch(ioDispatcher) {
                downloadManager.completedDownloads.collectLatest { completedItem ->
                    Log.d(TAG, "Download completed: id=${completedItem.id} file=${completedItem.fileName}")
                    updateUiState { current ->
                        current.copy(
                            lastDownloadNotification = completedItem,
                            showDownloadSnackbar = true,
                        )
                    }
                }
            }
        }
    }

    fun onDownloadStarted(item: DownloadItem) {
        Log.d(TAG, "Download started: id=${item.id} file=${item.fileName}")
        updateUiState {
            it.copy(
                lastDownloadNotification = item,
                showDownloadSnackbar = true,
                activeDownloadIds = it.activeDownloadIds + item.id,
            )
        }
    }

    fun onDownloadFailed(fileName: String?, message: String) {
        Log.d(TAG, "Download failed: file=$fileName message=$message")
        val id = failureCounter.incrementAndGet()
        updateUiState { it.copy(downloadFailure = DownloadFailure(id, fileName, message)) }
    }

    fun onDuplicateDownload(existing: DownloadItem, proceed: () -> Unit) {
        pendingDuplicate = proceed
        updateUiState { it.copy(duplicateDownloadPrompt = existing) }
    }

    fun confirmDuplicateDownload() {
        val proceed = pendingDuplicate
        dismissDuplicateDownload()
        proceed?.invoke()
    }

    fun dismissDuplicateDownload() {
        pendingDuplicate = null
        updateUiState { it.copy(duplicateDownloadPrompt = null) }
    }

    fun onConfirmDownload(request: DownloadConfirmRequest, proceed: () -> Unit) = confirmQueue.request(request, proceed)

    fun confirmDownloadPrompt() = confirmQueue.confirm()

    fun dismissDownloadPrompt() = confirmQueue.dismiss()

    fun dismissDownloadFailure() {
        updateUiState { it.copy(downloadFailure = null) }
    }

    fun dismissDownloadSnackbar() {
        updateUiState {
            it.copy(showDownloadSnackbar = false)
        }
    }

    fun pauseDownload(id: Long) {
        Log.d(TAG, "Pausing download $id")
        downloadManager?.pauseDownload(id)
    }

    fun resumeDownload(id: Long) {
        Log.d(TAG, "Resuming download $id")
        downloadManager?.resumeDownload(id)
    }

    fun cancelDownload(id: Long) {
        Log.d(TAG, "Cancelling download $id")
        downloadManager?.cancelDownload(id)
        updateUiState { current ->
            val shouldDismissSnackbar = current.lastDownloadNotification?.id == id
            current.copy(
                activeDownloadIds = current.activeDownloadIds - id,
                downloadProgressMap = current.downloadProgressMap - id,
                showDownloadSnackbar = if (shouldDismissSnackbar) false else current.showDownloadSnackbar,
                lastDownloadNotification = if (shouldDismissSnackbar) null else current.lastDownloadNotification,
            )
        }
    }

    fun retryDownload(id: Long) {
        Log.d(TAG, "Retrying download $id")
        downloadManager?.retryDownload(id)
    }

    fun deleteDownload(id: Long, deleteFile: Boolean) {
        Log.d(TAG, "Deleting download $id (deleteFile=$deleteFile)")
        scope.launch(ioDispatcher) {
            if (downloadManager != null) {
                downloadManager.deleteDownload(id, deleteFile)
            } else {
                downloadRepository?.deleteDownload(id, deleteFile)
            }
            updateUiState { current ->
                val shouldDismissSnackbar = current.lastDownloadNotification?.id == id
                current.copy(
                    activeDownloadIds = current.activeDownloadIds - id,
                    downloadProgressMap = current.downloadProgressMap - id,
                    showDownloadSnackbar = if (shouldDismissSnackbar) false else current.showDownloadSnackbar,
                    lastDownloadNotification = if (shouldDismissSnackbar) null else current.lastDownloadNotification,
                )
            }
        }
    }
}
