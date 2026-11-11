package com.jestk.browser.ui.viewmodel.events

import android.util.Log
import com.jestk.browser.domain.model.FeedbackPromptState
import com.jestk.browser.domain.repository.SettingsRepository
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.ui.viewmodel.DownloadCoordinator
import com.jestk.browser.ui.viewmodel.SearchCoordinator
import com.jestk.browser.ui.viewmodel.SettingsUiEvent
import com.jestk.browser.ui.viewmodel.UpdateCoordinator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val TAG = "BrowserViewModel"

internal class SettingsEventHandler(
    private val searchCoordinator: SearchCoordinator,
    private val downloadCoordinator: DownloadCoordinator,
    private val updateCoordinator: UpdateCoordinator,
    private val settingsRepository: SettingsRepository,
    private val updateUiState: ((BrowserUiState) -> BrowserUiState) -> Unit,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val currentTimeProvider: () -> Long,
    private val dismissDownloadSnackbar: () -> Unit,
    private val updateDefaultBrowserStatus: (Boolean) -> Unit,
) {
    fun handle(event: SettingsUiEvent) {
        when (event) {
            is BrowserUiEvent.DismissDownloadSnackbar -> {
                dismissDownloadSnackbar()
            }

            is BrowserUiEvent.PauseDownload -> {
                Log.d(TAG, "Download command: pause ${event.id}")
                downloadCoordinator.pauseDownload(event.id)
            }

            is BrowserUiEvent.ResumeDownload -> {
                Log.d(TAG, "Download command: resume ${event.id}")
                downloadCoordinator.resumeDownload(event.id)
            }

            is BrowserUiEvent.CancelDownload -> {
                Log.d(TAG, "Download command: cancel ${event.id}")
                downloadCoordinator.cancelDownload(event.id)
            }

            is BrowserUiEvent.RetryDownload -> {
                Log.d(TAG, "Download command: retry ${event.id}")
                downloadCoordinator.retryDownload(event.id)
            }

            is BrowserUiEvent.DeleteDownload -> {
                Log.d(TAG, "Download command: delete ${event.id}")
                downloadCoordinator.deleteDownload(event.id, event.deleteFile)
            }

            is BrowserUiEvent.SetThemeMode -> {
                updateUiState { it.copy(settings = it.settings.copy(themeMode = event.mode)) }
                scope.launch(ioDispatcher) {
                    settingsRepository.setThemeMode(event.mode)
                }
            }

            is BrowserUiEvent.SetSearchEngine -> {
                Log.d(TAG, "Navigation command: set search engine ${event.engine.id}")
                searchCoordinator.setSearchEngine(event.engine)
            }

            is BrowserUiEvent.SetAdBlockerEnabled -> {
                updateUiState { it.copy(settings = it.settings.copy(isAdBlockerEnabled = event.enabled)) }
                scope.launch(ioDispatcher) {
                    settingsRepository.setAdBlockerEnabled(event.enabled)
                }
            }

            is BrowserUiEvent.SetAdRedirectProtectionEnabled -> {
                updateUiState { it.copy(settings = it.settings.copy(isAdRedirectProtectionEnabled = event.enabled)) }
                scope.launch(ioDispatcher) {
                    settingsRepository.setAdRedirectProtectionEnabled(event.enabled)
                }
            }

            is BrowserUiEvent.SetAutoConsentEnabled -> {
                updateUiState { it.copy(settings = it.settings.copy(autoConsentEnabled = event.enabled)) }
                scope.launch(ioDispatcher) {
                    settingsRepository.setAutoConsentEnabled(event.enabled)
                }
            }

            is BrowserUiEvent.SetCleanYouTubePlayerEnabled -> {
                updateUiState { it.copy(settings = it.settings.copy(isCleanYouTubePlayerEnabled = event.enabled)) }
                scope.launch(ioDispatcher) {
                    settingsRepository.setCleanYouTubePlayerEnabled(event.enabled)
                }
            }

            is BrowserUiEvent.SetTrackingProtectionEnabled -> {
                updateUiState { it.copy(settings = it.settings.copy(isTrackingProtectionEnabled = event.enabled)) }
                scope.launch(ioDispatcher) {
                    settingsRepository.setTrackingProtectionEnabled(event.enabled)
                }
            }

            is BrowserUiEvent.SetOpenInAppsEnabled -> {
                updateUiState { it.copy(settings = it.settings.copy(isOpenInAppsEnabled = event.enabled)) }
                scope.launch(ioDispatcher) {
                    settingsRepository.setOpenInAppsEnabled(event.enabled)
                }
            }

            is BrowserUiEvent.SetVibrationEnabled -> {
                updateUiState { it.copy(settings = it.settings.copy(isVibrationEnabled = event.enabled)) }
                scope.launch(ioDispatcher) {
                    settingsRepository.setVibrationEnabled(event.enabled)
                }
            }

            is BrowserUiEvent.SetGrayUnloadedTabsEnabled -> {
                updateUiState { it.copy(settings = it.settings.copy(isGrayUnloadedTabsEnabled = event.enabled)) }
                scope.launch(ioDispatcher) {
                    settingsRepository.setGrayUnloadedTabsEnabled(event.enabled)
                }
            }

            is BrowserUiEvent.SetAutoUpdateEnabled -> {
                updateUiState { it.copy(settings = it.settings.copy(isAutoUpdateEnabled = event.enabled)) }
                scope.launch(ioDispatcher) {
                    settingsRepository.setAutoUpdateEnabled(event.enabled)
                    if (event.enabled) {
                        updateCoordinator.onAutoUpdateEnabled()
                    } else {
                        updateCoordinator.onAutoUpdateDisabled()
                    }
                }
            }

            is BrowserUiEvent.CheckForUpdate -> {
                scope.launch(ioDispatcher) {
                    updateCoordinator.checkForUpdate(event.manual)
                }
            }

            is BrowserUiEvent.RedirectToUpdate -> {
                updateCoordinator.onRedirectRequested()
            }

            BrowserUiEvent.DismissFeedbackPrompt -> {
                updateUiState {
                    it.copy(
                        isFeedbackCardVisible = false,
                        settings = it.settings.copy(feedbackPromptState = FeedbackPromptState.DISMISSED),
                    )
                }
                scope.launch(ioDispatcher) {
                    settingsRepository.setFeedbackPromptState(FeedbackPromptState.DISMISSED)
                }
            }

            is BrowserUiEvent.SnoozeFeedbackPrompt -> {
                val snoozedUntil = currentTimeProvider() + event.durationMs
                updateUiState {
                    it.copy(
                        isFeedbackCardVisible = false,
                        settings =
                            it.settings.copy(
                                feedbackPromptState = FeedbackPromptState.SNOOZED,
                                feedbackPromptSnoozedUntil = snoozedUntil,
                            ),
                    )
                }
                scope.launch(ioDispatcher) {
                    settingsRepository.snoozeFeedbackPrompt(snoozedUntil)
                }
            }

            BrowserUiEvent.OpenFeedbackSheet -> {
                updateUiState { it.copy(isFeedbackSheetVisible = true) }
            }

            BrowserUiEvent.CloseFeedbackSheet -> {
                updateUiState { it.copy(isFeedbackSheetVisible = false) }
            }

            is BrowserUiEvent.SkipUpdate -> {
                updateCoordinator.onSkip()
            }

            is BrowserUiEvent.DismissUpdate -> {
                updateCoordinator.onDismiss()
            }

            is BrowserUiEvent.UpdateDefaultBrowserStatus -> {
                updateDefaultBrowserStatus(event.isDefault)
            }
        }
    }
}
