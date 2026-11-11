package com.jestk.browser.ui.viewmodel.events

import android.util.Log
import com.jestk.browser.ui.viewmodel.BrowserUiEvent
import com.jestk.browser.ui.viewmodel.BrowserUiState
import com.jestk.browser.ui.viewmodel.NavigationCommand
import com.jestk.browser.ui.viewmodel.PageUiEvent
import com.jestk.browser.ui.viewmodel.SearchCoordinator
import com.jestk.browser.ui.viewmodel.TabCoordinator
import com.jestk.browser.util.YouTubeUrlHelper

private const val TAG = "BrowserViewModel"

internal class PageEventHandler(
    private val tabCoordinator: TabCoordinator,
    private val searchCoordinator: SearchCoordinator,
    private val updateUiState: ((BrowserUiState) -> BrowserUiState) -> Unit,
    private val emitNavigation: (NavigationCommand) -> Unit,
    private val loadUrl: (String) -> Unit,
    private val submitSearch: (String) -> Unit,
    private val schedulePreconnect: (String) -> Unit,
    private val handleGoBack: () -> Unit,
    private val getBypassCleanPlayerVideoId: () -> String?,
    private val setBypassCleanPlayerVideoId: (String?) -> Unit,
) {
    private var bypassCleanPlayerVideoId: String?
        get() = getBypassCleanPlayerVideoId()
        set(value) = setBypassCleanPlayerVideoId(value)

    fun handle(event: PageUiEvent) {
        when (event) {
            is BrowserUiEvent.LoadUrl -> {
                Log.d(TAG, "Navigation command: load url ${event.url}")
                loadUrl(event.url)
            }

            is BrowserUiEvent.SubmitSearch -> {
                Log.d(TAG, "Navigation command: submit search \"${event.query}\"")
                submitSearch(event.query)
            }

            is BrowserUiEvent.UpdateSearchQuery -> {
                updateUiState { it.copy(searchQuery = event.query) }
                searchCoordinator.updateHistorySuggestions(event.query)
                schedulePreconnect(event.query)
            }

            is BrowserUiEvent.GoBack -> {
                Log.d(TAG, "Navigation command: go back")
                handleGoBack()
            }

            is BrowserUiEvent.GoForward -> {
                Log.d(TAG, "Navigation command: go forward")
                emitNavigation(NavigationCommand.GoForward)
            }

            is BrowserUiEvent.Reload -> {
                Log.d(TAG, "Navigation command: reload")
                emitNavigation(NavigationCommand.Reload)
            }

            is BrowserUiEvent.StopLoading -> {
                Log.d(TAG, "Navigation command: stop loading")
                emitNavigation(NavigationCommand.StopLoading)
                tabCoordinator.handleStopLoading()
            }

            is BrowserUiEvent.SwitchSearchEngineForQuery -> {
                Log.d(TAG, "Navigation command: switch search engine to ${event.targetEngine.id}")
                searchCoordinator.switchSearchEngineForQuery(event.targetEngine, event.query)
            }

            is BrowserUiEvent.OnPageStarted -> {
                tabCoordinator.handlePageStarted(event.tabId, event.url)
            }

            is BrowserUiEvent.OnPageFinished -> {
                tabCoordinator.handlePageFinished(event.tabId, event.url, event.title)
                if (bypassCleanPlayerVideoId != null && YouTubeUrlHelper.extractVideoId(event.url) == bypassCleanPlayerVideoId) {
                    bypassCleanPlayerVideoId = null
                }
            }

            is BrowserUiEvent.OnTitleReceived -> {
                tabCoordinator.handleTitleReceived(event.tabId, event.title)
            }

            is BrowserUiEvent.OnProgressChanged -> {
                tabCoordinator.handleProgressChanged(event.tabId, event.progress)
            }

            is BrowserUiEvent.OnNavigationStateChanged -> {
                tabCoordinator.handleNavState(event.tabId, event.canGoBack, event.canGoForward)
            }

            is BrowserUiEvent.OnSnapshotCaptured -> {
                tabCoordinator.handleSnapshot(event.tabId, event.snapshotPath)
            }

            is BrowserUiEvent.UpdateFaviconUrl -> {
                tabCoordinator.handleUpdateFavicon(event.tabId, event.faviconUrl, event.pageUrl)
            }

            is BrowserUiEvent.OnThemeColorReceived -> {
                tabCoordinator.handleThemeColor(event.tabId, event.color)
            }

            is BrowserUiEvent.OnThemeColorsReceived -> {
                tabCoordinator.handleThemeColors(event.tabId, event.topColor, event.bottomColor)
            }
        }
    }
}
