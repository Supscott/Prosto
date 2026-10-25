package com.jestk.browser.ui.viewmodel

import android.content.pm.ActivityInfo
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jestk.browser.domain.model.DownloadItem
import com.jestk.browser.domain.model.OverlayType
import com.jestk.browser.domain.model.ScreenType
import com.jestk.browser.domain.model.SearchEngine
import com.jestk.browser.domain.repository.DownloadRepository
import com.jestk.browser.domain.repository.FavoriteRepository
import com.jestk.browser.domain.repository.HistoryRepository
import com.jestk.browser.domain.repository.RecentTabRepository
import com.jestk.browser.domain.repository.SearchEngineRepository
import com.jestk.browser.domain.repository.SettingsRepository
import com.jestk.browser.domain.repository.TabRepository
import com.jestk.browser.engine.WebViewPool
import com.jestk.browser.engine.download.HoriDownloadManager
import com.jestk.browser.engine.update.AppUpdateManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "BrowserViewModel"

class BrowserViewModel(
    private val tabRepository: TabRepository,
    private val historyRepository: HistoryRepository,
    private val favoriteRepository: FavoriteRepository,
    val settingsRepository: SettingsRepository,
    private val recentTabRepository: RecentTabRepository? = null,
    private val downloadRepository: DownloadRepository? = null,
    private val downloadManager: HoriDownloadManager? = null,
    private val searchEngineRepository: SearchEngineRepository? = null,
    private val webViewPool: WebViewPool? = null,
    private val appUpdateManager: AppUpdateManager? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val tabOperationMutex = Mutex()
    private val tabThemeColors = ConcurrentHashMap<String, Int>()

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    /**
     * Current vibration preference, read on demand so a toggle takes effect without restarting
     * the gesture that captured this view model.
     */
    val isVibrationEnabled: Boolean
        get() = _uiState.value.settings.isVibrationEnabled

    private val _navigationCommands = MutableSharedFlow<NavigationCommand>(extraBufferCapacity = 16)
    val navigationCommands: SharedFlow<NavigationCommand> = _navigationCommands.asSharedFlow()

    private val downloadCoordinator =
        DownloadCoordinator(
            downloadRepository = downloadRepository,
            downloadManager = downloadManager,
            updateUiState = { transform -> _uiState.update(transform) },
            scope = viewModelScope,
            ioDispatcher = ioDispatcher,
        )

    private val searchCoordinator =
        SearchCoordinator(
            historyRepository = historyRepository,
            searchEngineRepository = searchEngineRepository,
            settingsRepository = settingsRepository,
            getCurrentState = { _uiState.value },
            updateUiState = { transform -> _uiState.update(transform) },
            onSelectTab = { tabId -> tabCoordinator.selectTab(tabId) },
            onOpenNewTab = { url -> tabCoordinator.openNewTab(url) },
            scope = viewModelScope,
            ioDispatcher = ioDispatcher,
        )

    private val updateCoordinator =
        UpdateCoordinator(
            appUpdateManager = appUpdateManager,
            settingsRepository = settingsRepository,
            getCurrentState = { _uiState.value },
            updateUiState = { transform -> _uiState.update(transform) },
            scope = viewModelScope,
            ioDispatcher = ioDispatcher,
        )

    internal val tabCoordinator =
        TabCoordinator(
            tabRepository = tabRepository,
            recentTabRepository = recentTabRepository,
            favoriteRepository = favoriteRepository,
            historyRepository = historyRepository,
            webViewPool = webViewPool,
            tabOperationMutex = tabOperationMutex,
            tabThemeColors = tabThemeColors,
            getCurrentState = { _uiState.value },
            updateUiState = { transform -> _uiState.update(transform) },
            emitNavigation = { cmd -> _navigationCommands.tryEmit(cmd) },
            scope = viewModelScope,
            ioDispatcher = ioDispatcher,
        )

    init {
        webViewPool?.onDownloadStarted = { item -> onDownloadStarted(item) }
        viewModelScope.launch(ioDispatcher) {
            restoreSession()
            observeDatabaseFlows()
        }
        updateCoordinator.onAppStart()
    }

    override fun onCleared() {
        super.onCleared()
        webViewPool?.onDownloadStarted = null
    }

    private suspend fun restoreSession() {
        Log.d(TAG, "Restoring session")
        val initialSettings = settingsRepository.getCurrentSettings()
        val currentEngines =
            if (searchEngineRepository != null) {
                searchEngineRepository.ensureDefaultEnginesSeeded()
                val engines = searchEngineRepository.getAllSearchEngines()
                if (engines.isNotEmpty()) engines else SearchEngine.DEFAULT_ENGINES
            } else {
                SearchEngine.DEFAULT_ENGINES
            }
        tabCoordinator.restoreSession(initialSettings, currentEngines)
    }

    private fun observeDatabaseFlows() {
        tabCoordinator.observeDatabaseFlows()
        searchCoordinator.observeDatabaseFlows()
        downloadCoordinator.observeDatabaseFlows()
        viewModelScope.launch(ioDispatcher) {
            settingsRepository.getBlockedAdsCount().collect { count ->
                _uiState.update { it.copy(blockedAdsCount = count) }
            }
        }
        viewModelScope.launch(ioDispatcher) {
            settingsRepository.getBlockedTrackersCount().collect { count ->
                _uiState.update { it.copy(blockedTrackersCount = count) }
            }
        }
    }

    fun incrementBlockedAds(count: Long = 1) {
        viewModelScope.launch(ioDispatcher) {
            settingsRepository.incrementBlockedAds(count)
        }
    }

    fun incrementBlockedTrackers(count: Long = 1) {
        viewModelScope.launch(ioDispatcher) {
            settingsRepository.incrementBlockedTrackers(count)
        }
    }

    fun onDownloadStarted(item: DownloadItem) {
        downloadCoordinator.onDownloadStarted(item)
    }

    fun dismissDownloadSnackbar() {
        downloadCoordinator.dismissDownloadSnackbar()
    }

    fun updateDefaultBrowserStatus(isDefault: Boolean) {
        _uiState.update {
            it.copy(isDefaultBrowser = isDefault)
        }
    }

    fun onEvent(event: BrowserUiEvent) {
        when (event) {
            is BrowserUiEvent.OpenNewTab -> {
                Log.d(TAG, "Navigation command: open new tab for ${event.url}")
                tabCoordinator.openNewTab(event.url)
            }

            is BrowserUiEvent.OpenRecentTab -> {
                Log.d(TAG, "Navigation command: open recent tab ${event.item.url}")
                tabCoordinator.openRecentTab(event.item) { loadUrl(it) }
            }

            is BrowserUiEvent.SelectTab -> {
                Log.d(TAG, "Navigation command: select tab ${event.tabId}")
                tabCoordinator.selectTab(event.tabId)
            }

            is BrowserUiEvent.CloseTab -> {
                Log.d(TAG, "Navigation command: close tab ${event.tabId}")
                tabCoordinator.closeTab(event.tabId)
            }

            is BrowserUiEvent.CloseAllTabs -> {
                Log.d(TAG, "Navigation command: close all tabs")
                tabCoordinator.closeAllTabs()
            }

            is BrowserUiEvent.ReorderTabs -> {
                tabCoordinator.reorderTabs(event.orderedTabs)
            }

            is BrowserUiEvent.SwipeNextTab -> {
                tabCoordinator.switchAdjacentTab(1)
            }

            is BrowserUiEvent.SwipePrevTab -> {
                tabCoordinator.switchAdjacentTab(-1)
            }

            is BrowserUiEvent.UndoCloseTab -> {
                Log.d(TAG, "Navigation command: undo close tab")
                tabCoordinator.undoCloseTab()
            }

            is BrowserUiEvent.DismissUndoTabCloseSnackbar -> {
                _uiState.update {
                    it.copy(
                        showUndoTabCloseSnackbar = false,
                        recentlyClosedTab = null,
                    )
                }
            }

            is BrowserUiEvent.DismissDownloadSnackbar -> {
                dismissDownloadSnackbar()
            }

            is BrowserUiEvent.DownloadStarted -> {
                onDownloadStarted(event.item)
            }

            is BrowserUiEvent.OpenOverlay -> {
                _uiState.update { it.copy(activeOverlay = event.overlay) }
            }

            is BrowserUiEvent.DismissOverlay -> {
                _uiState.update {
                    if (it.activeScreen != ScreenType.NONE) {
                        it.copy(activeScreen = ScreenType.NONE)
                    } else {
                        it.copy(activeOverlay = OverlayType.NONE, isSearchHistoryExpanded = false)
                    }
                }
            }

            is BrowserUiEvent.OpenHome -> {
                _uiState.update { it.copy(activeOverlay = OverlayType.HOME) }
            }

            is BrowserUiEvent.OpenSearch -> {
                val current = _uiState.value
                val isHome =
                    current.activeOverlay == OverlayType.HOME ||
                        current.currentUrl.isEmpty() ||
                        current.currentUrl == "about:blank"
                if (event.openInNewTab && !isHome) {
                    tabCoordinator.openNewTabForSearch { searchCoordinator.updateHistorySuggestions("") }
                } else {
                    val prefill = event.prefilledUrl ?: if (isHome) "" else current.currentUrl
                    _uiState.update {
                        it.copy(
                            activeOverlay = OverlayType.SEARCH,
                            searchQuery = prefill,
                            isSearchHistoryExpanded = false,
                            isLoadingMoreHistory = false,
                            hasMoreHistory = true,
                        )
                    }
                    searchCoordinator.updateHistorySuggestions(prefill)
                }
            }

            is BrowserUiEvent.OpenTabGrid -> {
                _uiState.update { it.copy(activeOverlay = OverlayType.TAB_GRID) }
            }

            is BrowserUiEvent.OpenSettings -> {
                _uiState.update {
                    it.copy(activeScreen = ScreenType.SETTINGS, activeOverlay = OverlayType.NONE)
                }
            }

            is BrowserUiEvent.OpenDownloads -> {
                _uiState.update {
                    it.copy(activeScreen = ScreenType.DOWNLOADS, activeOverlay = OverlayType.NONE)
                }
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

            is BrowserUiEvent.LoadUrl -> {
                Log.d(TAG, "Navigation command: load url ${event.url}")
                loadUrl(event.url)
            }

            is BrowserUiEvent.SubmitSearch -> {
                Log.d(TAG, "Navigation command: submit search \"${event.query}\"")
                submitSearch(event.query)
            }

            is BrowserUiEvent.UpdateSearchQuery -> {
                _uiState.update { it.copy(searchQuery = event.query) }
                searchCoordinator.updateHistorySuggestions(event.query)
            }

            is BrowserUiEvent.GoBack -> {
                Log.d(TAG, "Navigation command: go back")
                handleGoBack()
            }

            is BrowserUiEvent.GoForward -> {
                Log.d(TAG, "Navigation command: go forward")
                _navigationCommands.tryEmit(NavigationCommand.GoForward)
            }

            is BrowserUiEvent.Reload -> {
                Log.d(TAG, "Navigation command: reload")
                _navigationCommands.tryEmit(NavigationCommand.Reload)
            }

            is BrowserUiEvent.StopLoading -> {
                Log.d(TAG, "Navigation command: stop loading")
                _navigationCommands.tryEmit(NavigationCommand.StopLoading)
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

            is BrowserUiEvent.OnFaviconReceived -> {
                Unit
            }

            is BrowserUiEvent.UpdateFaviconUrl -> {
                tabCoordinator.handleUpdateFavicon(event.tabId, event.faviconUrl)
            }

            is BrowserUiEvent.OnThemeColorReceived -> {
                tabCoordinator.handleThemeColor(event.tabId, event.color)
            }

            is BrowserUiEvent.ShowAddFavoriteDialog -> {
                _uiState.update { it.copy(showAddFavoriteDialog = true) }
            }

            is BrowserUiEvent.DismissAddFavoriteDialog -> {
                _uiState.update { it.copy(showAddFavoriteDialog = false) }
            }

            is BrowserUiEvent.AddCurrentTabToFavorites -> {
                tabCoordinator.addCurrentToFavorites()
            }

            is BrowserUiEvent.AddFavorite -> {
                tabCoordinator.addFavorite(event.title, event.url)
            }

            is BrowserUiEvent.RemoveFavorite -> {
                tabCoordinator.deleteFavorite(event.id)
            }

            is BrowserUiEvent.ToggleFavoritesExpanded -> {
                _uiState.update { it.copy(isFavoritesExpanded = !it.isFavoritesExpanded) }
            }

            is BrowserUiEvent.DeleteHistoryItem -> {
                searchCoordinator.deleteHistoryItem(event.id)
            }

            is BrowserUiEvent.ClearAllHistory -> {
                searchCoordinator.clearAllHistory {
                    if (recentTabRepository != null) {
                        recentTabRepository.clearRecentTabs()
                    } else {
                        tabRepository.clearRecentTabs()
                    }
                }
            }

            is BrowserUiEvent.SelectHistorySuggestion -> {
                loadUrl(event.item.url)
            }

            is BrowserUiEvent.ToggleSearchHistoryExpanded -> {
                val target = !_uiState.value.isSearchHistoryExpanded
                _uiState.update {
                    it.copy(
                        isSearchHistoryExpanded = target,
                        isLoadingMoreHistory = false,
                        hasMoreHistory = true,
                    )
                }
                searchCoordinator.updateHistorySuggestions(_uiState.value.searchQuery)
            }

            is BrowserUiEvent.SetSearchHistoryExpanded -> {
                _uiState.update {
                    it.copy(
                        isSearchHistoryExpanded = event.expanded,
                        isLoadingMoreHistory = false,
                        hasMoreHistory = true,
                    )
                }
                searchCoordinator.updateHistorySuggestions(_uiState.value.searchQuery)
            }

            is BrowserUiEvent.LoadMoreSearchHistory -> {
                searchCoordinator.loadMoreSearchHistory()
            }

            is BrowserUiEvent.SetThemeMode -> {
                _uiState.update { it.copy(settings = it.settings.copy(themeMode = event.mode)) }
                viewModelScope.launch(ioDispatcher) {
                    settingsRepository.setThemeMode(event.mode)
                }
            }

            is BrowserUiEvent.SetSearchEngine -> {
                Log.d(TAG, "Navigation command: set search engine ${event.engine.id}")
                searchCoordinator.setSearchEngine(event.engine)
            }

            is BrowserUiEvent.SetAdBlockerEnabled -> {
                _uiState.update { it.copy(settings = it.settings.copy(isAdBlockerEnabled = event.enabled)) }
                viewModelScope.launch(ioDispatcher) {
                    settingsRepository.setAdBlockerEnabled(event.enabled)
                }
            }

            is BrowserUiEvent.SetOpenInAppsEnabled -> {
                _uiState.update { it.copy(settings = it.settings.copy(isOpenInAppsEnabled = event.enabled)) }
                viewModelScope.launch(ioDispatcher) {
                    settingsRepository.setOpenInAppsEnabled(event.enabled)
                }
            }

            is BrowserUiEvent.SetAutoUpdateEnabled -> {
                _uiState.update { it.copy(settings = it.settings.copy(isAutoUpdateEnabled = event.enabled)) }
                viewModelScope.launch(ioDispatcher) {
                    settingsRepository.setAutoUpdateEnabled(event.enabled)
                }
                if (event.enabled) updateCoordinator.onAutoUpdateEnabled()
            }

            is BrowserUiEvent.SetVibrationEnabled -> {
                _uiState.update { it.copy(settings = it.settings.copy(isVibrationEnabled = event.enabled)) }
                viewModelScope.launch(ioDispatcher) {
                    settingsRepository.setVibrationEnabled(event.enabled)
                }
            }

            is BrowserUiEvent.InstallUpdate -> {
                updateCoordinator.onInstallRequested()
            }

            is BrowserUiEvent.InstallUpdateLater -> {
                updateCoordinator.onInstallLater()
            }

            is BrowserUiEvent.SkipUpdate -> {
                updateCoordinator.onSkip()
            }

            is BrowserUiEvent.OnExternalAppOffered -> {
                tabCoordinator.handleExternalAppOffered(event.tabId, event.prompt, event.userInitiated)
            }

            is BrowserUiEvent.DismissExternalAppPrompt -> {
                tabCoordinator.dismissExternalAppPrompt(event.reason)
            }

            is BrowserUiEvent.OpenExternalApp -> {
                tabCoordinator.dismissExternalAppPrompt(ExternalAppDismissReason.TRANSIENT)
            }

            is BrowserUiEvent.ShowAddSearchEngineDialog -> {
                _uiState.update {
                    it.copy(
                        showAddEditSearchEngineDialog = true,
                        editingSearchEngine = event.engineToEdit,
                    )
                }
            }

            is BrowserUiEvent.DismissAddSearchEngineDialog -> {
                _uiState.update {
                    it.copy(
                        showAddEditSearchEngineDialog = false,
                        editingSearchEngine = null,
                    )
                }
            }

            is BrowserUiEvent.SaveSearchEngine -> {
                searchCoordinator.saveSearchEngine(
                    id = event.id,
                    title = event.title,
                    shortcut = event.shortcut,
                    urlTemplate = event.urlTemplate,
                )
            }

            is BrowserUiEvent.DeleteSearchEngine -> {
                searchCoordinator.deleteSearchEngine(event.id)
            }

            is BrowserUiEvent.ReorderSearchEngines -> {
                searchCoordinator.reorderSearchEngines(event.orderedEngines)
            }

            is BrowserUiEvent.ToggleSearchPageEngineVisibility -> {
                val currentHidden = _uiState.value.settings.hiddenSearchPageEngineIds
                val updatedHidden =
                    if (event.isVisible) {
                        currentHidden - event.engineId
                    } else {
                        currentHidden + event.engineId
                    }
                _uiState.update { state ->
                    state.copy(settings = state.settings.copy(hiddenSearchPageEngineIds = updatedHidden))
                }
                viewModelScope.launch(ioDispatcher) {
                    settingsRepository.setHiddenSearchPageEngineIds(updatedHidden)
                }
            }

            is BrowserUiEvent.SetSearchEngineSwitcherEditExpanded -> {
                _uiState.update { it.copy(isSearchEngineSwitcherEditExpanded = event.expanded) }
            }

            is BrowserUiEvent.OnWebScroll -> {
                handleWebScroll(event.deltaY, event.scrollY, event.viewportHeight)
            }

            is BrowserUiEvent.SetBottomBarVisibility -> {
                _uiState.update { it.copy(isBottomBarVisible = event.visible) }
            }

            is BrowserUiEvent.ToggleMenu -> {
                val current = _uiState.value
                val target = event.expanded ?: !current.isMenuExpanded
                _uiState.update { it.copy(isMenuExpanded = target) }
            }

            is BrowserUiEvent.SetMenuExpanded -> {
                _uiState.update { it.copy(isMenuExpanded = event.expanded) }
            }

            is BrowserUiEvent.SetVideoPlaying -> {
                _uiState.update { it.copy(isVideoPlaying = event.isPlaying) }
                _uiState.value.activeTabId?.let { activeId ->
                    webViewPool?.onTabActivated(activeId, event.isPlaying)
                }
            }

            is BrowserUiEvent.SetFullscreenCustomView -> {
                _uiState.update {
                    it.copy(
                        fullscreenCustomView = event.view,
                        isFullscreenCustomView = event.isFullscreen,
                        fullscreenRequestedOrientation = event.requestedOrientation,
                    )
                }
            }

            is BrowserUiEvent.ExitFullscreen -> {
                _navigationCommands.tryEmit(NavigationCommand.ExitFullscreen)
                _uiState.update {
                    it.copy(
                        isFullscreenCustomView = false,
                        fullscreenCustomView = null,
                        fullscreenRequestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
                    )
                }
            }

            is BrowserUiEvent.HandleBackPress -> {
                handleBackNavigation()
            }

            is BrowserUiEvent.ShowLinkContextMenu -> {
                val anchor =
                    if (event.anchorX != null && event.anchorY != null) {
                        Offset(event.anchorX, event.anchorY)
                    } else {
                        null
                    }
                _uiState.update {
                    it.copy(
                        linkContextMenuUrl = event.url,
                        contextMenuAnchor = anchor,
                    )
                }
            }

            is BrowserUiEvent.DismissLinkContextMenu -> {
                _uiState.update {
                    it.copy(
                        linkContextMenuUrl = null,
                        contextMenuAnchor = null,
                    )
                }
            }

            is BrowserUiEvent.OpenTabInBackground -> {
                Log.d(TAG, "Navigation command: open tab in background for ${event.url}")
                tabCoordinator.openTabInBackground(event.url)
            }

            is BrowserUiEvent.UpdateDefaultBrowserStatus -> {
                updateDefaultBrowserStatus(event.isDefault)
            }

            is BrowserUiEvent.ShowImageContextMenu -> {
                val anchor =
                    if (event.anchorX != null && event.anchorY != null) {
                        Offset(event.anchorX, event.anchorY)
                    } else {
                        null
                    }
                _uiState.update {
                    it.copy(
                        selectedImageUrl = event.imageUrl,
                        imageContextMenuLinkUrl = event.linkUrl,
                        contextMenuAnchor = anchor,
                    )
                }
            }

            is BrowserUiEvent.DismissImageContextMenu -> {
                _uiState.update {
                    it.copy(
                        selectedImageUrl = null,
                        imageContextMenuLinkUrl = null,
                        contextMenuAnchor = null,
                    )
                }
            }

            is BrowserUiEvent.CopyImageUrl -> {
                _uiState.update {
                    it.copy(
                        selectedImageUrl = null,
                        imageContextMenuLinkUrl = null,
                        contextMenuAnchor = null,
                    )
                }
            }

            is BrowserUiEvent.DownloadImage -> {
                _uiState.update {
                    it.copy(
                        selectedImageUrl = null,
                        imageContextMenuLinkUrl = null,
                        contextMenuAnchor = null,
                    )
                }
            }

            is BrowserUiEvent.PreviewImageInNewTab -> {
                _uiState.update {
                    it.copy(
                        selectedImageUrl = null,
                        imageContextMenuLinkUrl = null,
                        contextMenuAnchor = null,
                    )
                }
                tabCoordinator.openNewTab(event.imageUrl)
            }
        }
    }

    private fun loadUrl(rawUrl: String) {
        val trimmed = rawUrl.trim()
        var matchedShortcutEngine: SearchEngine? = null
        val finalUrl =
            when {
                trimmed.isEmpty() -> {
                    ""
                }

                trimmed == "about:blank" -> {
                    ""
                }

                trimmed.startsWith("http://") || trimmed.startsWith("https://") -> {
                    trimmed
                }

                trimmed.startsWith("localhost") || (trimmed.contains(".") && !trimmed.contains(" ")) ||
                    (trimmed.contains(":") && !trimmed.contains(" ")) -> {
                    "https://$trimmed"
                }

                else -> {
                    val shortcutMatch = Regex("""^([a-zA-Z0-9_-]+)\s+(.+)$""").find(trimmed)
                    val shortcutEngine =
                        if (shortcutMatch != null) {
                            val prefix = shortcutMatch.groupValues[1]
                            _uiState.value.searchEngines.firstOrNull { it.shortcut?.equals(prefix, ignoreCase = true) == true }
                        } else {
                            null
                        }

                    if (shortcutEngine != null) {
                        matchedShortcutEngine = shortcutEngine
                        viewModelScope.launch(ioDispatcher) {
                            settingsRepository.setSearchEngine(shortcutEngine)
                        }
                        val searchTargetQuery = shortcutMatch!!.groupValues[2].trim()
                        shortcutEngine.buildSearchUrl(searchTargetQuery)
                    } else {
                        _uiState.value.settings.searchEngine
                            .buildSearchUrl(trimmed)
                    }
                }
            }

        tabCoordinator.navigateToUrl(finalUrl, matchedShortcutEngine)
    }

    private fun submitSearch(query: String) {
        loadUrl(query)
    }

    private fun handleWebScroll(
        deltaY: Float,
        scrollY: Int = 0,
        viewportHeight: Int = 0,
    ) {
        val current = _uiState.value
        if (deltaY > 8f && (current.isMenuExpanded || current.isSearchEngineSwitcherEditExpanded)) {
            _uiState.update { it.copy(isMenuExpanded = false, isSearchEngineSwitcherEditExpanded = false) }
        }

        val isHome = current.activeOverlay == OverlayType.HOME || current.currentUrl.isEmpty() || current.currentUrl == "about:blank"
        if (isHome) {
            if (!current.isBottomBarVisible) {
                _uiState.update { it.copy(isBottomBarVisible = true) }
            }
            return
        }

        // Hiding the dock starts working below 50% screen (scrollY > viewportHeight / 2)
        val isBelowHalfScreen = viewportHeight > 0 && scrollY > (viewportHeight / 2)
        if (!isBelowHalfScreen) {
            // Within the first half-screen (scrollY <= viewportHeight / 2) or scrolled back to top: dock must always be visible
            if (!current.isBottomBarVisible) {
                _uiState.update { it.copy(isBottomBarVisible = true) }
            }
        } else {
            // Below 50% screen: hide if scrolling down, show if scrolling up
            if (deltaY > 8f) {
                if (current.isBottomBarVisible) {
                    _uiState.update {
                        it.copy(
                            isBottomBarVisible = false,
                            isMenuExpanded = false,
                            isSearchEngineSwitcherEditExpanded = false,
                        )
                    }
                }
            } else if (deltaY < -8f) {
                if (!current.isBottomBarVisible) {
                    _uiState.update { it.copy(isBottomBarVisible = true) }
                }
            }
        }
    }

    private fun handleGoBack() {
        _navigationCommands.tryEmit(NavigationCommand.GoBack)
    }

    private fun handleBackNavigation() {
        val current = _uiState.value
        if (current.isSearchEngineSwitcherEditExpanded) {
            _uiState.update { it.copy(isSearchEngineSwitcherEditExpanded = false) }
            return
        }
        if (current.showAddEditSearchEngineDialog) {
            _uiState.update { it.copy(showAddEditSearchEngineDialog = false, editingSearchEngine = null) }
            return
        }
        if (current.showAddFavoriteDialog) {
            _uiState.update { it.copy(showAddFavoriteDialog = false) }
            return
        }
        if (current.selectedImageUrl != null) {
            _uiState.update {
                it.copy(
                    selectedImageUrl = null,
                    imageContextMenuLinkUrl = null,
                    contextMenuAnchor = null,
                )
            }
            return
        }
        if (current.activeScreen != ScreenType.NONE) {
            _uiState.update { it.copy(activeScreen = ScreenType.NONE) }
            return
        }
        if (current.linkContextMenuUrl != null) {
            _uiState.update {
                it.copy(
                    linkContextMenuUrl = null,
                    contextMenuAnchor = null,
                )
            }
            return
        }
        if (current.isFullscreenCustomView) {
            onEvent(BrowserUiEvent.ExitFullscreen)
            return
        }
        if (current.isMenuExpanded) {
            _uiState.update { it.copy(isMenuExpanded = false) }
            return
        }
        if (current.isSearchHistoryExpanded) {
            _uiState.update {
                it.copy(
                    isSearchHistoryExpanded = false,
                    isLoadingMoreHistory = false,
                    hasMoreHistory = true,
                )
            }
            searchCoordinator.updateHistorySuggestions(current.searchQuery)
            return
        }
        val isDismissibleOverlay = current.activeOverlay != OverlayType.NONE && current.activeOverlay != OverlayType.HOME
        if (isDismissibleOverlay) {
            _uiState.update {
                it.copy(
                    activeOverlay = OverlayType.NONE,
                    isSearchHistoryExpanded = false,
                    isLoadingMoreHistory = false,
                    hasMoreHistory = true,
                )
            }
            return
        }

        if (current.canGoBack) {
            _navigationCommands.tryEmit(NavigationCommand.GoBack)
            return
        }

        // Web history is exhausted!
        if (current.tabs.size > 1) {
            current.activeTabId?.let { tabCoordinator.closeTab(it) }
        } else {
            val isCurrentTabBlankOrHome =
                current.currentUrl.isEmpty() ||
                    current.currentUrl == "about:blank" ||
                    current.activeOverlay == OverlayType.HOME

            if (!isCurrentTabBlankOrHome) {
                // If it was on a website, close website and return to clean Home tab
                current.activeTabId?.let { tabId ->
                    _uiState.update {
                        it.copy(
                            currentUrl = "",
                            pageTitle = "Home",
                            canGoBack = false,
                            canGoForward = false,
                            activeOverlay = OverlayType.HOME,
                        )
                    }
                    viewModelScope.launch(ioDispatcher) {
                        tabRepository.updateTabDetails(tabId, "", "Home", canGoBack = false, canGoForward = false)
                    }
                }
            } else {
                // History exhausted and already on clean Home tab: exit app cleanly without crashing
                _navigationCommands.tryEmit(NavigationCommand.ExitApp)
            }
        }
    }
}
