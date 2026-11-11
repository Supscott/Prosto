package com.jestk.browser.ui.viewmodel.events

import android.content.pm.ActivityInfo
import com.jestk.browser.engine.WebViewPool
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.ui.viewmodel.MediaUiEvent
import com.jestk.browser.ui.viewmodel.NavigationCommand
import com.jestk.browser.util.YouTubeUrlHelper

internal class MediaEventHandler(
    private val webViewPool: WebViewPool?,
    private val getCurrentState: () -> BrowserUiState,
    private val updateUiState: ((BrowserUiState) -> BrowserUiState) -> Unit,
    private val emitNavigation: (NavigationCommand) -> Unit,
    private val dispatch: (BrowserUiEvent) -> Unit,
    private val getBypassCleanPlayerVideoId: () -> String?,
    private val setBypassCleanPlayerVideoId: (String?) -> Unit,
) {
    private var bypassCleanPlayerVideoId: String?
        get() = getBypassCleanPlayerVideoId()
        set(value) = setBypassCleanPlayerVideoId(value)

    fun handle(event: MediaUiEvent) {
        when (event) {
            is BrowserUiEvent.SetVideoPlaying -> {
                updateUiState { it.copy(isVideoPlaying = event.isPlaying) }
                getCurrentState().activeTabId?.let { activeId ->
                    webViewPool?.onTabActivated(activeId, event.isPlaying)
                }
            }

            is BrowserUiEvent.SetFullscreenCustomView -> {
                updateUiState {
                    it.copy(
                        fullscreenCustomView = event.view,
                        isFullscreenCustomView = event.isFullscreen,
                        fullscreenRequestedOrientation = event.requestedOrientation,
                    )
                }
            }

            is BrowserUiEvent.UpdateFullscreenOrientation -> {
                updateUiState { current ->
                    if (current.isFullscreenCustomView) {
                        current.copy(fullscreenRequestedOrientation = event.orientation)
                    } else {
                        current
                    }
                }
            }

            is BrowserUiEvent.ExitFullscreen -> {
                emitNavigation(NavigationCommand.ExitFullscreen)
                updateUiState {
                    it.copy(
                        isFullscreenCustomView = false,
                        fullscreenCustomView = null,
                        fullscreenRequestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
                    )
                }
            }

            is BrowserUiEvent.OpenCleanPlayer -> {
                updateUiState {
                    it.copy(
                        cleanPlayerVideoId = event.videoId,
                        cleanPlayerOriginalUrl = event.originalUrl,
                        cleanPlayerStartSeconds = event.startSeconds,
                    )
                }
            }

            is BrowserUiEvent.CloseCleanPlayer -> {
                updateUiState {
                    it.copy(
                        cleanPlayerVideoId = null,
                        cleanPlayerOriginalUrl = null,
                        cleanPlayerStartSeconds = 0,
                    )
                }
            }

            is BrowserUiEvent.OpenCleanPlayerOnWeb -> {
                val videoId = YouTubeUrlHelper.extractVideoId(event.url)
                bypassCleanPlayerVideoId = videoId
                updateUiState {
                    it.copy(
                        cleanPlayerVideoId = null,
                        cleanPlayerOriginalUrl = null,
                        cleanPlayerStartSeconds = 0,
                    )
                }
                dispatch(BrowserUiEvent.LoadUrl(event.url))
            }
        }
    }
}
