package com.jestk.browser.ui.viewmodel

import com.jestk.browser.domain.model.DownloadItem
import com.jestk.browser.domain.repository.DownloadRepository
import com.jestk.browser.engine.download.HoriDownloadManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Coordinator for managing file downloads, notifications, snackbars, and progress updates.
 */
internal class DownloadCoordinator(
    private val downloadRepository: DownloadRepository?,
    private val downloadManager: HoriDownloadManager?,
    private val updateUiState: ((BrowserUiState) -> BrowserUiState) -> Unit,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
) {
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
        updateUiState {
            it.copy(
                lastDownloadNotification = item,
                showDownloadSnackbar = true,
                activeDownloadIds = it.activeDownloadIds + item.id,
            )
        }
    }

    fun dismissDownloadSnackbar() {
        updateUiState {
            it.copy(showDownloadSnackbar = false)
        }
    }

    fun pauseDownload(id: Long) {
        downloadManager?.pauseDownload(id)
    }

    fun resumeDownload(id: Long) {
        downloadManager?.resumeDownload(id)
    }

    fun cancelDownload(id: Long) {
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
        downloadManager?.retryDownload(id)
    }

    fun deleteDownload(id: Long, deleteFile: Boolean) {
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
